package com.sunline.dict.vectorization;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Eschema;
import com.sunline.dict.entity.EschemaDetail;
import com.sunline.dict.mapper.EschemaDetailMapper;
import com.sunline.dict.mapper.EschemaMapper;
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
 * Eschema（e_schema.xml）枚举类型向量化服务实现
 *
 * <p>向量化粒度：以 restriction_type（restriction_type_id）为单位，每个枚举类型生成一个 Point。
 * <p>唯一键：eschema_id + restriction_type_id
 *
 * <p>向量化文本格式：
 * <pre>
 * {restrictionTypeId} {restrictionTypeLongname} [所属:{eschemaId}]({eschemaLongname})
 * 基础类型:{restrictionTypeBase}
 * 枚举项:[{enumerationValue}={enumerationId} {enumerationLongname}, ...]
 * </pre>
 *
 * <p>ref_ids：restriction_type_base 中引用的限制类型（uschema）
 */
@Service
@ConditionalOnProperty(name = "vectorization.enabled", havingValue = "true")
public class EschemaVectorizationServiceImpl
        extends AbstractVectorizationBase
        implements EschemaVectorizationService {

    private static final String MODEL_TYPE = "eschema";

    @Autowired
    private EschemaMapper eschemaMapper;

    @Autowired
    private EschemaDetailMapper eschemaDetailMapper;

    @Override
    public void vectorizeBySource(String sourceInfo) {
        List<Eschema> eschemaList = eschemaMapper.selectList(
                new QueryWrapper<Eschema>().eq("from_jar", sourceInfo));

        if (eschemaList.isEmpty()) {
            log.warn("未找到 from_jar={} 的 eschema 记录，跳过向量化", sourceInfo);
            return;
        }

        log.info("开始向量化 eschema，from_jar={}，共 {} 个文件", sourceInfo, eschemaList.size());

        List<Map<String, Object>> points = new ArrayList<>();

        for (Eschema eschema : eschemaList) {
            try {
                List<EschemaDetail> allDetails = eschemaDetailMapper.selectList(
                        new QueryWrapper<EschemaDetail>().eq("eschema_id", eschema.getId()));

                if (allDetails.isEmpty()) {
                    log.debug("eschema id={} 下无明细，跳过", eschema.getId());
                    continue;
                }

                // 按 restriction_type_id 分组，每个枚举类型一个 Point
                Map<String, List<EschemaDetail>> detailsByRestriction =
                        allDetails.stream().collect(Collectors.groupingBy(
                                d -> d.getRestrictionTypeId() != null ? d.getRestrictionTypeId() : ""));

                Set<String> processed = new HashSet<>();

                for (EschemaDetail detail : allDetails) {
                    String restrictionId = detail.getRestrictionTypeId();
                    if (restrictionId == null || restrictionId.isEmpty() || processed.contains(restrictionId))
                        continue;
                    processed.add(restrictionId);

                    List<EschemaDetail> enumDetails = detailsByRestriction.getOrDefault(restrictionId, List.of());

                    String fullText = buildVectorText(eschema, detail, enumDetails);
                    List<String> refIds = extractRefIds(detail);

                    // 构建头部
                    StringBuilder headerSb = new StringBuilder();
                    if (notEmpty(detail.getRestrictionTypeId())) headerSb.append(detail.getRestrictionTypeId());
                    if (notEmpty(detail.getRestrictionTypeLongname())) headerSb.append(" ").append(detail.getRestrictionTypeLongname());
                    if (notEmpty(eschema.getId())) headerSb.append(" [所属:").append(eschema.getId()).append("]");
                    if (notEmpty(detail.getRestrictionTypeBase())) headerSb.append(" 基础类型:").append(detail.getRestrictionTypeBase());
                    String header = headerSb.toString();

                    // 枚举项去重
                    LinkedHashMap<String, EschemaDetail> enumMap = new LinkedHashMap<>();
                    for (EschemaDetail ed : enumDetails) {
                        String key = notEmpty(ed.getEnumerationId()) ? ed.getEnumerationId()
                                : (notEmpty(ed.getEnumerationValue()) ? ed.getEnumerationValue() : "");
                        if (!key.isEmpty() && !enumMap.containsKey(key)) enumMap.put(key, ed);
                    }
                    List<EschemaDetail> uniqueEnums = new ArrayList<>(enumMap.values());

                    // 分批
                    List<List<EschemaDetail>> chunks = splitList(uniqueEnums, FIELDS_PER_CHUNK);

                    for (int ci = 0; ci < chunks.size(); ci++) {
                        String enumText = chunks.get(ci).stream().map(ed -> {
                            StringBuilder es = new StringBuilder();
                            if (notEmpty(ed.getEnumerationValue())) es.append(ed.getEnumerationValue());
                            if (notEmpty(ed.getEnumerationId())) { if (es.length() > 0) es.append("="); es.append(ed.getEnumerationId()); }
                            if (notEmpty(ed.getEnumerationLongname())) es.append(" ").append(ed.getEnumerationLongname());
                            return es.toString().trim();
                        }).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.joining(", "));

                        String chunkText = header + " 枚举项:[" + enumText + "]";
                        List<Float> vector = embeddingClient.embed(chunkText);

                        Map<String, Object> point = new HashMap<>();
                        String suffix = chunks.size() > 1 ? ":chunk" + ci : "";
                        point.put("id", toUuidString("eschema:" + eschema.getId() + ":" + restrictionId + suffix));
                        point.put("vector", vector);

                        Map<String, Object> payload = buildPayload(eschema, detail, fullText, sourceInfo, refIds);
                        payload.put("chunk_index", ci);
                        payload.put("total_chunks", chunks.size());
                        point.put("payload", payload);
                        points.add(point);
                    }

                    log.debug("eschema 向量化完成：eschemaId={}, restrictionId={}, 枚举项数={}, Point数={}",
                            eschema.getId(), restrictionId, uniqueEnums.size(), chunks.size());
                }

            } catch (Exception e) {
                log.error("eschema 向量化失败：id={}，错误：{}", eschema.getId(), e.getMessage(), e);
            }
        }

        if (!points.isEmpty()) {
            upsertPoints(points);
            log.info("eschema 向量写入完成，from_jar={}，成功 {} 个枚举类型", sourceInfo, points.size());
        }
    }

    @Override
    public void deleteBySource(String sourceInfo) {
        deleteBySourceInternal(MODEL_TYPE, sourceInfo);
    }

    // ─────────────── 私有方法 ───────────────

    /**
     * 构建向量化文本
     *
     * <p>示例：
     * <pre>
     * E_CUST_TYPE 客户类型枚举 [所属:MBaseEnumType](基础枚举类型)
     * 基础类型:MBaseType.U_CUST_TYPE
     * 枚举项:[00=PERSONAL 个人客户, 01=ENTERPRISE 企业客户, 02=INSTITUTION 机构客户]
     * </pre>
     */
    private String buildVectorText(Eschema eschema, EschemaDetail restrictionRep,
                                   List<EschemaDetail> enumDetails) {
        StringBuilder sb = new StringBuilder();

        // 1. 枚举类型基本信息
        if (notEmpty(restrictionRep.getRestrictionTypeId())) {
            sb.append(restrictionRep.getRestrictionTypeId());
        }
        if (notEmpty(restrictionRep.getRestrictionTypeLongname())) {
            sb.append(" ").append(restrictionRep.getRestrictionTypeLongname());
        }
        if (notEmpty(eschema.getId())) {
            sb.append(" [所属:").append(eschema.getId()).append("]");
        }
        if (notEmpty(eschema.getLongname())) {
            sb.append("(").append(eschema.getLongname()).append(")");
        }

        // 2. 基础类型
        if (notEmpty(restrictionRep.getRestrictionTypeBase())) {
            sb.append(" 基础类型:").append(restrictionRep.getRestrictionTypeBase());
        }

        // 3. 枚举项列表（按 enumeration_id 去重）
        Map<String, EschemaDetail> enumMap = new LinkedHashMap<>();
        for (EschemaDetail d : enumDetails) {
            String key = notEmpty(d.getEnumerationId()) ? d.getEnumerationId() :
                         (notEmpty(d.getEnumerationValue()) ? d.getEnumerationValue() : "");
            if (!key.isEmpty() && !enumMap.containsKey(key)) {
                enumMap.put(key, d);
            }
        }

        if (!enumMap.isEmpty()) {
            String enumText = enumMap.values().stream()
                    .map(d -> {
                        StringBuilder es = new StringBuilder();
                        if (notEmpty(d.getEnumerationValue())) {
                            es.append(d.getEnumerationValue());
                        }
                        if (notEmpty(d.getEnumerationId())) {
                            if (es.length() > 0) es.append("=");
                            es.append(d.getEnumerationId());
                        }
                        if (notEmpty(d.getEnumerationLongname())) {
                            es.append(" ").append(d.getEnumerationLongname());
                        }
                        return es.toString().trim();
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));
            sb.append(" 枚举项:[").append(enumText).append("]");
        }

        return sb.toString();
    }

    /**
     * 提取 ref_ids
     * restriction_type_base 含 "." 时认为引用了限制类型（uschema），取 "." 前部分作为 schema id
     */
    private List<String> extractRefIds(EschemaDetail restrictionRep) {
        List<String> refs = new ArrayList<>();
        String base = restrictionRep.getRestrictionTypeBase();
        if (notEmpty(base) && base.contains(".")) {
            String schemaId = base.split("\\.")[0];
            refs.add("uschema:" + schemaId);
        }
        return refs;
    }

    /**
     * 构建 Qdrant payload
     */
    private Map<String, Object> buildPayload(Eschema eschema, EschemaDetail restrictionRep,
                                              String text, String sourceInfo, List<String> refIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model_type", MODEL_TYPE);
        payload.put("eschema_id", eschema.getId());
        payload.put("record_id", eschema.getId() + ":" + restrictionRep.getRestrictionTypeId());
        payload.put("restriction_type_id", restrictionRep.getRestrictionTypeId() != null ? restrictionRep.getRestrictionTypeId() : "");
        payload.put("longname", restrictionRep.getRestrictionTypeLongname() != null ? restrictionRep.getRestrictionTypeLongname() : "");
        payload.put("restriction_type_base", restrictionRep.getRestrictionTypeBase() != null ? restrictionRep.getRestrictionTypeBase() : "");
        payload.put("from_jar", sourceInfo);
        payload.put("text", text);
        payload.put("ref_ids", refIds);
        return payload;
    }
}
