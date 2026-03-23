package com.sunline.dict.vectorization;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Dict;
import com.sunline.dict.entity.DictDetail;
import com.sunline.dict.mapper.DictDetailMapper;
import com.sunline.dict.mapper.DictMapper;
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
 * Dict（d_schema.xml）字典类型向量化服务实现
 *
 * <p>向量化粒度：以 dict_complex_type（dict_complex_type_id）为单位。
 * 原因：一个 dict 文件（dict_id）可包含多个 complexType 对象，每个对象是独立的语义单元（如"客户信息"、"账户信息"）。
 *
 * <p>唯一键：dict_id + dict_complex_type_id + element_id
 * <p>element_longname 可能存在多条（同一 element_id 在不同场景下有不同中文名），取第一条。
 *
 * <p>element_type 三种情况：
 * <ul>
 *   <li>基本类型（string/int 等）：文本展示类型名，不生成 ref_id</li>
 *   <li>限制类型（MBaseType.U_IP_ADDR）：ref_id = uschema:MBaseType，文本标 [限制:...]</li>
 *   <li>枚举类型（MBaseEnumType.E_WTHR_FLG）：ref_id = eschema:MBaseEnumType，文本标 [枚举:...]</li>
 * </ul>
 *
 * <p>向量化文本格式：
 * <pre>
 * {dictComplexTypeId} {dictComplexTypeLongname} [所属:{dictId}]({dictLongname})
 * 字段:[{elementId}/{elementDbname} {elementLongname} (string) | [限制:MBaseType.U_IP_ADDR] | [枚举:MBaseEnumType.E_WTHR_FLG], ...]
 * </pre>
 */
@Service
@ConditionalOnProperty(name = "vectorization.enabled", havingValue = "true")
public class DictVectorizationServiceImpl
        extends AbstractVectorizationBase
        implements DictVectorizationService {

    private static final String MODEL_TYPE = "dict";

    @Autowired
    private DictMapper dictMapper;

    @Autowired
    private DictDetailMapper dictDetailMapper;

    @Override
    public void vectorizeBySource(String sourceInfo) {
        List<Dict> dictList = dictMapper.selectList(
                new QueryWrapper<Dict>().eq("from_jar", sourceInfo));

        if (dictList.isEmpty()) {
            log.warn("未找到 from_jar={} 的 dict 记录，跳过向量化", sourceInfo);
            return;
        }

        log.info("开始向量化 dict，from_jar={}，共 {} 个 dict 文件", sourceInfo, dictList.size());

        List<Map<String, Object>> points = new ArrayList<>();

        for (Dict dict : dictList) {
            try {
                List<DictDetail> allDetails = dictDetailMapper.selectList(
                        new QueryWrapper<DictDetail>().eq("dict_id", dict.getId()));

                if (allDetails.isEmpty()) {
                    log.debug("dict id={} 下无明细，跳过", dict.getId());
                    continue;
                }

                // 按 dict_complex_type_id 分组
                Map<String, List<DictDetail>> detailsByComplexType =
                        allDetails.stream().collect(Collectors.groupingBy(
                                d -> d.getDictComplexTypeId() != null ? d.getDictComplexTypeId() : ""));

                Set<String> processedTypes = new HashSet<>();

                for (DictDetail detail : allDetails) {
                    String complexTypeId = detail.getDictComplexTypeId();
                    if (complexTypeId == null || complexTypeId.isEmpty() || processedTypes.contains(complexTypeId))
                        continue;
                    processedTypes.add(complexTypeId);

                    List<DictDetail> typeDetails = detailsByComplexType.getOrDefault(complexTypeId, List.of());

                    // 去重
                    List<DictDetail> uniqueFields = deduplicateDictFields(typeDetails);

                    String header = buildDictHeader(dict, detail);
                    String fullText = buildVectorText(dict, detail, typeDetails);
                    List<String> refIds = extractRefIds(typeDetails);

                    // 分批
                    List<List<DictDetail>> fieldChunks = splitList(uniqueFields, FIELDS_PER_CHUNK);

                    for (int ci = 0; ci < fieldChunks.size(); ci++) {
                        String chunkText = buildDictChunkText(header, fieldChunks.get(ci));
                        List<Float> vector = embeddingClient.embed(chunkText);

                        Map<String, Object> point = new HashMap<>();
                        String suffix = fieldChunks.size() > 1 ? ":chunk" + ci : "";
                        point.put("id", toUuidString("dict:" + dict.getId() + ":" + complexTypeId + suffix));
                        point.put("vector", vector);

                        Map<String, Object> payload = buildPayload(dict, detail, fullText, sourceInfo, refIds);
                        payload.put("chunk_index", ci);
                        payload.put("total_chunks", fieldChunks.size());
                        point.put("payload", payload);
                        points.add(point);
                    }

                    log.debug("dict 向量化完成：dictId={}, complexTypeId={}, 字段数={}, Point数={}",
                            dict.getId(), complexTypeId, uniqueFields.size(), fieldChunks.size());
                }

            } catch (Exception e) {
                log.error("dict 向量化失败：id={}，错误：{}", dict.getId(), e.getMessage(), e);
            }
        }

        if (!points.isEmpty()) {
            upsertPoints(points);
            log.info("dict 向量写入完成，from_jar={}，成功 {} 个 complexType", sourceInfo, points.size());
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
     * CustInfo 客户信息 [所属:DeptDictSchema](存款字典)
     * 字段:[custId/CUST_ID 客户号(string), custType/CUST_TYPE 客户类型[枚举:MBaseEnumType.E_CUST_TYPE],
     *       ipAddr/IP_ADDR IP地址[限制:MBaseType.U_IP_ADDR]]
     * </pre>
     */
    private String buildVectorText(Dict dict, DictDetail complexTypeRep, List<DictDetail> typeDetails) {
        StringBuilder sb = new StringBuilder();

        // 1. complexType 基本信息
        if (notEmpty(complexTypeRep.getDictComplexTypeId())) {
            sb.append(complexTypeRep.getDictComplexTypeId());
        }
        if (notEmpty(complexTypeRep.getDictComplexTypeLongname())) {
            sb.append(" ").append(complexTypeRep.getDictComplexTypeLongname());
        }
        if (notEmpty(dict.getId())) {
            sb.append(" [所属:").append(dict.getId()).append("]");
        }
        if (notEmpty(dict.getLongname())) {
            sb.append("(").append(dict.getLongname()).append(")");
        }

        // 2. 字段列表（按 element_id 去重，element_longname 取首条）
        Map<String, DictDetail> fieldMap = new LinkedHashMap<>();
        for (DictDetail d : typeDetails) {
            String key = notEmpty(d.getElementId()) ? d.getElementId() : "";
            if (!key.isEmpty() && !fieldMap.containsKey(key)) {
                fieldMap.put(key, d);
            }
        }

        if (!fieldMap.isEmpty()) {
            String fieldsText = fieldMap.values().stream()
                    .map(this::formatField)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));
            sb.append(" 字段:[").append(fieldsText).append("]");
        }

        return sb.toString();
    }

    /**
     * 格式化单个字段
     *
     * <p>格式：{elementId}/{elementDbname} {elementLongname} + 类型标识
     * <p>类型标识：
     *   基本类型 → (string)
     *   限制类型 → [限制:MBaseType.U_IP_ADDR]
     *   枚举类型 → [枚举:MBaseEnumType.E_WTHR_FLG]
     */
    private String formatField(DictDetail d) {
        StringBuilder fs = new StringBuilder();

        // elementId / elementDbname
        if (notEmpty(d.getElementId()) && notEmpty(d.getElementDbname())) {
            fs.append(d.getElementId()).append("/").append(d.getElementDbname());
        } else if (notEmpty(d.getElementId())) {
            fs.append(d.getElementId());
        } else if (notEmpty(d.getElementDbname())) {
            fs.append(d.getElementDbname());
        }

        // elementLongname
        if (notEmpty(d.getElementLongname())) {
            fs.append(" ").append(d.getElementLongname());
        }

        // elementType 分类处理
        if (notEmpty(d.getElementType())) {
            TypeCategory category = classifyType(d.getElementType());
            switch (category) {
                case BASIC:
                    fs.append("(").append(d.getElementType()).append(")");
                    break;
                case USCHEMA:
                    fs.append("[限制:").append(d.getElementType()).append("]");
                    break;
                case ESCHEMA:
                    fs.append("[枚举:").append(d.getElementType()).append("]");
                    break;
            }
        }

        return fs.toString().trim();
    }

    /**
     * 提取 ref_ids
     * - 限制类型 MBaseType.U_IP_ADDR → uschema:MBaseType
     * - 枚举类型 MBaseEnumType.E_WTHR_FLG → eschema:MBaseEnumType
     */
    private List<String> extractRefIds(List<DictDetail> typeDetails) {
        Set<String> refSet = new HashSet<>();
        for (DictDetail d : typeDetails) {
            if (!notEmpty(d.getElementType())) continue;

            TypeCategory category = classifyType(d.getElementType());
            if (category == TypeCategory.USCHEMA) {
                String schemaId = d.getElementType().split("\\.")[0];
                refSet.add("uschema:" + schemaId);
            } else if (category == TypeCategory.ESCHEMA) {
                String schemaId = d.getElementType().split("\\.")[0];
                refSet.add("eschema:" + schemaId);
            }
        }
        return new ArrayList<>(refSet);
    }

    /**
     * 构建 Qdrant payload
     */
    private Map<String, Object> buildPayload(Dict dict, DictDetail complexTypeRep,
                                              String text, String sourceInfo, List<String> refIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model_type", MODEL_TYPE);
        payload.put("dict_id", dict.getId());
        payload.put("record_id", dict.getId() + ":" + complexTypeRep.getDictComplexTypeId());
        payload.put("complex_type_id", complexTypeRep.getDictComplexTypeId() != null ? complexTypeRep.getDictComplexTypeId() : "");
        payload.put("longname", complexTypeRep.getDictComplexTypeLongname() != null ? complexTypeRep.getDictComplexTypeLongname() : "");
        payload.put("from_jar", sourceInfo);
        payload.put("text", text);
        payload.put("ref_ids", refIds);
        return payload;
    }

    // ─────────────── 类型分类 ───────────────

    private List<DictDetail> deduplicateDictFields(List<DictDetail> details) {
        LinkedHashMap<String, DictDetail> map = new LinkedHashMap<>();
        for (DictDetail d : details) {
            String key = notEmpty(d.getElementId()) ? d.getElementId() : "";
            if (!key.isEmpty() && !map.containsKey(key)) map.put(key, d);
        }
        return new ArrayList<>(map.values());
    }

    private String buildDictHeader(Dict dict, DictDetail rep) {
        StringBuilder sb = new StringBuilder();
        if (notEmpty(rep.getDictComplexTypeId())) sb.append(rep.getDictComplexTypeId());
        if (notEmpty(rep.getDictComplexTypeLongname())) sb.append(" ").append(rep.getDictComplexTypeLongname());
        if (notEmpty(dict.getId())) sb.append(" [所属:").append(dict.getId()).append("]");
        if (notEmpty(dict.getLongname())) sb.append("(").append(dict.getLongname()).append(")");
        return sb.toString();
    }

    private String buildDictChunkText(String header, List<DictDetail> chunkFields) {
        String fieldsText = chunkFields.stream()
                .map(this::formatField)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        return header + " 字段:[" + fieldsText + "]";
    }

    private enum TypeCategory {
        BASIC,     // string, int 等基本类型
        USCHEMA,   // MBaseType.U_IP_ADDR 限制类型
        ESCHEMA    // MBaseEnumType.E_WTHR_FLG 枚举类型
    }

    /**
     * 分类 element_type：
     * - 含 "Enum" 前缀 → 枚举（eschema）
     * - 含 "." 且不含 "Enum" → 限制类型（uschema）
     * - 其他 → 基本类型
     */
    private TypeCategory classifyType(String elementType) {
        if (elementType == null || elementType.isEmpty()) return TypeCategory.BASIC;

        if (elementType.contains(".")) {
            String prefix = elementType.split("\\.")[0];
            if (prefix.contains("Enum")) {
                return TypeCategory.ESCHEMA;
            }
            return TypeCategory.USCHEMA;
        }

        return TypeCategory.BASIC;
    }
}
