package com.sunline.dict.vectorization;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Complex;
import com.sunline.dict.entity.ComplexDetail;
import com.sunline.dict.mapper.ComplexDetailMapper;
import com.sunline.dict.mapper.ComplexMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Complex（c_schema.xml）复合类型向量化服务实现
 *
 * <p>向量化策略：一个 pojo 对象可能有几十上百个字段，超出模型 token 限制。
 * 采用"分批多 Point"方案：
 * <ul>
 *   <li>每 FIELDS_PER_CHUNK 个字段为一批，每批生成一个独立的 Point</li>
 *   <li>每个 Point 的文本 = 对象头部信息 + 该批字段信息</li>
 *   <li>所有 Point 共享相同的 payload（对象元信息），搜索命中任意一个都能定位到该对象</li>
 *   <li>Point ID = complex:{complexId}:{pojoId}:chunk{N}，保证唯一且可覆盖</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "vectorization.enabled", havingValue = "true")
public class ComplexVectorizationServiceImpl
        extends AbstractVectorizationBase
        implements ComplexVectorizationService {

    private static final String MODEL_TYPE = "complex";

    /** 每批字段数量（25个字段约300-400字符，加上头部信息控制在450字符以内） */
    private static final int FIELDS_PER_CHUNK = 25;

    @Autowired
    private ComplexMapper complexMapper;

    @Autowired
    private ComplexDetailMapper complexDetailMapper;

    @Override
    public void vectorizeBySource(String sourceInfo) {
        List<Complex> complexList = complexMapper.selectList(
                new QueryWrapper<Complex>().eq("from_jar", sourceInfo));

        if (complexList.isEmpty()) {
            log.warn("未找到 from_jar={} 的 complex 记录，跳过向量化", sourceInfo);
            return;
        }

        log.info("开始向量化 complex，from_jar={}，共 {} 个 complex 文件", sourceInfo, complexList.size());

        List<Map<String, Object>> points = new ArrayList<>();

        for (Complex complex : complexList) {
            try {
                List<ComplexDetail> allDetails = complexDetailMapper.selectList(
                        new QueryWrapper<ComplexDetail>()
                                .eq("complex_id", complex.getId()));

                if (allDetails.isEmpty()) {
                    log.debug("complex id={} 下无明细，跳过", complex.getId());
                    continue;
                }

                // 按 complex_pojo_id 分组
                Map<String, List<ComplexDetail>> detailsByPojo =
                        allDetails.stream().collect(Collectors.groupingBy(
                                d -> d.getComplexPojoId() != null ? d.getComplexPojoId() : ""));

                Set<String> processedPojos = new HashSet<>();

                for (ComplexDetail detail : allDetails) {
                    String pojoId = detail.getComplexPojoId();
                    if (pojoId == null || pojoId.isEmpty() || processedPojos.contains(pojoId)) continue;
                    processedPojos.add(pojoId);

                    List<ComplexDetail> pojoDetails = detailsByPojo.getOrDefault(pojoId, List.of());
                    List<String> refIds = extractRefIds(pojoDetails);

                    // 对字段去重
                    List<ComplexDetail> uniqueFields = deduplicateFields(pojoDetails);

                    // 构建头部信息（对象名 + 所属）
                    String header = buildHeader(complex, detail);

                    // 构建完整文本（用于 payload.text，展示用）
                    String fullText = buildFullText(header, uniqueFields);

                    // 分批生成 Point
                    List<List<ComplexDetail>> chunks = splitFieldsIntoChunks(uniqueFields);

                    for (int i = 0; i < chunks.size(); i++) {
                        List<ComplexDetail> chunkFields = chunks.get(i);
                        String chunkText = buildChunkText(header, chunkFields);
                        List<Float> vector = embeddingClient.embed(chunkText);

                        Map<String, Object> point = new HashMap<>();
                        // 多 chunk 时 Point ID 带 chunk 序号
                        String pointIdSuffix = chunks.size() > 1 ? ":chunk" + i : "";
                        point.put("id", toUuidString("complex:" + complex.getId() + ":" + pojoId + pointIdSuffix));
                        point.put("vector", vector);
                        point.put("payload", buildPayload(complex, detail, fullText, sourceInfo, refIds, i, chunks.size()));
                        points.add(point);
                    }

                    log.debug("complex 向量化完成：complexId={}, pojoId={}, 字段数={}, Point数={}",
                            complex.getId(), pojoId, uniqueFields.size(), chunks.size());
                }

            } catch (Exception e) {
                log.error("complex 向量化失败：id={}，错误：{}", complex.getId(), e.getMessage(), e);
            }
        }

        if (!points.isEmpty()) {
            upsertPoints(points);
            log.info("complex 向量写入完成，from_jar={}，成功 {} 个 Point", sourceInfo, points.size());
        }
    }

    @Override
    public void deleteBySource(String sourceInfo) {
        deleteBySourceInternal(MODEL_TYPE, sourceInfo);
    }

    // ─────────────── 私有方法 ───────────────

    /** 字段去重（按 element_id） */
    private List<ComplexDetail> deduplicateFields(List<ComplexDetail> pojoDetails) {
        Map<String, ComplexDetail> fieldMap = new LinkedHashMap<>();
        for (ComplexDetail d : pojoDetails) {
            String key = notEmpty(d.getElementId()) ? d.getElementId() : "";
            if (!key.isEmpty() && !fieldMap.containsKey(key)) {
                fieldMap.put(key, d);
            }
        }
        return new ArrayList<>(fieldMap.values());
    }

    /** 按 FIELDS_PER_CHUNK 拆分字段列表 */
    private List<List<ComplexDetail>> splitFieldsIntoChunks(List<ComplexDetail> fields) {
        List<List<ComplexDetail>> chunks = new ArrayList<>();
        for (int i = 0; i < fields.size(); i += FIELDS_PER_CHUNK) {
            chunks.add(fields.subList(i, Math.min(i + FIELDS_PER_CHUNK, fields.size())));
        }
        if (chunks.isEmpty()) {
            chunks.add(fields);
        }
        return chunks;
    }

    /** 构建头部信息：{pojoId} {longname} [所属:{complexId}]({complexLongname}) */
    private String buildHeader(Complex complex, ComplexDetail pojoRep) {
        StringBuilder sb = new StringBuilder();
        if (notEmpty(pojoRep.getComplexPojoId())) sb.append(pojoRep.getComplexPojoId());
        if (notEmpty(pojoRep.getComplexPojoLongname())) sb.append(" ").append(pojoRep.getComplexPojoLongname());
        if (notEmpty(complex.getId())) sb.append(" [所属:").append(complex.getId()).append("]");
        if (notEmpty(complex.getLongname())) sb.append("(").append(complex.getLongname()).append(")");
        return sb.toString();
    }

    /** 格式化单个字段文本 */
    private String formatField(ComplexDetail d) {
        StringBuilder fs = new StringBuilder();
        if (notEmpty(d.getElementId())) fs.append(d.getElementId());
        if (notEmpty(d.getElementLongname())) fs.append(" ").append(d.getElementLongname());
        if ("true".equalsIgnoreCase(d.getElementMulti())) fs.append("[数组]");
        if (notEmpty(d.getElementRef())) fs.append("[字典:").append(d.getElementRef()).append("]");
        if (notEmpty(d.getElementType()) && !isBasicType(d.getElementType())) {
            fs.append("[类型:").append(d.getElementType()).append("]");
        }
        return fs.toString().trim();
    }

    /** 构建单个 chunk 的向量化文本（头部 + 该批字段） */
    private String buildChunkText(String header, List<ComplexDetail> chunkFields) {
        String fieldsText = chunkFields.stream()
                .map(this::formatField)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        return header + " 字段:[" + fieldsText + "]";
    }

    /** 构建完整文本（所有字段，用于 payload.text 展示） */
    private String buildFullText(String header, List<ComplexDetail> allFields) {
        String fieldsText = allFields.stream()
                .map(this::formatField)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        return header + " 字段:[" + fieldsText + "]";
    }

    private List<String> extractRefIds(List<ComplexDetail> pojoDetails) {
        Set<String> refSet = new HashSet<>();
        for (ComplexDetail d : pojoDetails) {
            if (notEmpty(d.getElementRef())) refSet.add("dict:" + d.getElementRef());
            if (notEmpty(d.getElementType()) && !isBasicType(d.getElementType())) {
                refSet.add("complex:" + d.getElementType());
            }
        }
        return new ArrayList<>(refSet);
    }

    private Map<String, Object> buildPayload(Complex complex, ComplexDetail pojoRep,
                                              String fullText, String sourceInfo,
                                              List<String> refIds, int chunkIndex, int totalChunks) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model_type", MODEL_TYPE);
        payload.put("complex_id", complex.getId());
        payload.put("record_id", complex.getId() + ":" + pojoRep.getComplexPojoId());
        payload.put("pojo_id", pojoRep.getComplexPojoId() != null ? pojoRep.getComplexPojoId() : "");
        payload.put("longname", pojoRep.getComplexPojoLongname() != null ? pojoRep.getComplexPojoLongname() : "");
        payload.put("from_jar", sourceInfo);
        payload.put("text", fullText);
        payload.put("ref_ids", refIds);
        payload.put("chunk_index", chunkIndex);
        payload.put("total_chunks", totalChunks);
        return payload;
    }
}
