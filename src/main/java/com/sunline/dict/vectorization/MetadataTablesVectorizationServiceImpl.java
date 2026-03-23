package com.sunline.dict.vectorization;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.MetadataTables;
import com.sunline.dict.entity.MetadataTablesDetail;
import com.sunline.dict.entity.MetadataTablesIndexes;
import com.sunline.dict.mapper.MetadataTablesDetailMapper;
import com.sunline.dict.mapper.MetadataTablesIndexesMapper;
import com.sunline.dict.mapper.MetadataTablesMapper;
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
 * MetadataTables（tables.xml）向量化服务实现
 *
 * <p>向量化粒度：以 table（table_id）为单位，每张表一个 Point。
 *
 * <p>向量化文本格式：
 * <pre>
 * {tableName} {tableLongname} [继承:{tableExtension}]
 * 字段:[{fieldDbname}/{fieldId} {fieldLongname}[来源字典:{fieldRef}], ...]
 * ODB方法:[{odbindexId}({odbindexType}) 字段:{odbindexFields} 操作:{odbindexOperate}, ...]
 * </pre>
 *
 * <p>Point ID：metadata_tables:{schemaId}:{tableId}（每张表唯一）
 */
@Service
@ConditionalOnProperty(name = "vectorization.enabled", havingValue = "true")
public class MetadataTablesVectorizationServiceImpl
        extends AbstractVectorizationBase
        implements MetadataTablesVectorizationService {

    private static final String MODEL_TYPE = "metadata_tables";

    @Autowired
    private MetadataTablesMapper metadataTablesMapper;

    @Autowired
    private MetadataTablesDetailMapper metadataTablesDetailMapper;

    @Autowired
    private MetadataTablesIndexesMapper metadataTablesIndexesMapper;

    @Override
    public void vectorizeBySource(String sourceInfo) {
        // 查该来源下的所有 schema
        List<MetadataTables> schemaList = metadataTablesMapper.selectList(
                new QueryWrapper<MetadataTables>().eq("from_jar", sourceInfo));

        if (schemaList.isEmpty()) {
            log.warn("未找到 from_jar={} 的 metadata_tables 记录，跳过向量化", sourceInfo);
            return;
        }

        log.info("开始向量化 metadata_tables，from_jar={}，共 {} 个 schema", sourceInfo, schemaList.size());

        List<Map<String, Object>> points = new ArrayList<>();

        for (MetadataTables schema : schemaList) {
            try {
                // 查该 schema 下所有字段明细
                List<MetadataTablesDetail> allDetails = metadataTablesDetailMapper.selectList(
                        new QueryWrapper<MetadataTablesDetail>()
                                .eq("metadata_tables_id", schema.getId()));

                // 查该 schema 下所有 ODB 索引（只取 odbindex_id 不为空的）
                List<MetadataTablesIndexes> allIndexes = metadataTablesIndexesMapper.selectList(
                        new QueryWrapper<MetadataTablesIndexes>()
                                .eq("metadata_tables_id", schema.getId())
                                .isNotNull("odbindex_id"));

                // 按 table_id 分组，每张表生成一个 Point
                Map<String, List<MetadataTablesDetail>> detailsByTable =
                        allDetails.stream().collect(Collectors.groupingBy(
                                d -> d.getTableId() != null ? d.getTableId() : ""));

                Map<String, List<MetadataTablesIndexes>> indexesByTable =
                        allIndexes.stream().collect(Collectors.groupingBy(
                                i -> i.getTableId() != null ? i.getTableId() : ""));

                Set<String> processedTables = new HashSet<>();

                for (MetadataTablesDetail detail : allDetails) {
                    String tableId = detail.getTableId();
                    if (tableId == null || tableId.isEmpty() || processedTables.contains(tableId)) continue;
                    processedTables.add(tableId);

                    List<MetadataTablesDetail> tableDetails = detailsByTable.getOrDefault(tableId, List.of());
                    List<MetadataTablesIndexes> tableIndexes = indexesByTable.getOrDefault(tableId, List.of());

                    // 去重字段
                    List<MetadataTablesDetail> uniqueFields = deduplicateTableFields(tableDetails);

                    String header = buildTableHeader(detail);
                    String fullText = buildVectorText(detail, tableDetails, tableIndexes);

                    List<String> refIds = tableDetails.stream()
                            .filter(d -> notEmpty(d.getFieldRef()))
                            .map(d -> "dict:" + d.getFieldRef())
                            .distinct()
                            .collect(Collectors.toList());

                    // 分批生成 Point（按字段分批，ODB 方法只加在第一批）
                    List<List<MetadataTablesDetail>> fieldChunks = splitList(uniqueFields, FIELDS_PER_CHUNK);

                    for (int ci = 0; ci < fieldChunks.size(); ci++) {
                        String chunkText = buildChunkText(header, fieldChunks.get(ci), ci == 0 ? tableIndexes : List.of());
                        List<Float> vector = embeddingClient.embed(chunkText);

                        Map<String, Object> point = new HashMap<>();
                        String suffix = fieldChunks.size() > 1 ? ":chunk" + ci : "";
                        point.put("id", toUuidString("metadata_tables:" + schema.getId() + ":" + tableId + suffix));
                        point.put("vector", vector);

                        Map<String, Object> payload = buildPayload(schema, detail, fullText, sourceInfo, refIds);
                        payload.put("chunk_index", ci);
                        payload.put("total_chunks", fieldChunks.size());
                        point.put("payload", payload);
                        points.add(point);
                    }

                    log.debug("metadata_tables 向量化完成：schemaId={}, tableId={}, 字段数={}, Point数={}",
                            schema.getId(), tableId, uniqueFields.size(), fieldChunks.size());
                }

            } catch (Exception e) {
                log.error("metadata_tables 向量化失败：schemaId={}，错误：{}", schema.getId(), e.getMessage(), e);
            }
        }

        if (!points.isEmpty()) {
            upsertPoints(points);
            log.info("metadata_tables 向量写入完成，from_jar={}，成功 {} 张表", sourceInfo, points.size());
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
     * DEPT_CUST 客户信息表 [继承:COMMON_BASE]
     * 字段:[CUST_ID/custId 客户号, CUST_NAME/custName 客户姓名[来源字典:CUST_DICT], CUST_TYPE/custType 客户类型[来源字典:CUST_TYPE]]
     * ODB方法:[queryCust(select) 字段:CUST_ID 操作:SELECT, insertCust(insert) 字段:CUST_ID,CUST_NAME 操作:INSERT]
     * </pre>
     */
    private String buildVectorText(MetadataTablesDetail tableRepresentative,
                                   List<MetadataTablesDetail> tableDetails,
                                   List<MetadataTablesIndexes> tableIndexes) {
        StringBuilder sb = new StringBuilder();

        // 1. 表基本信息
        if (notEmpty(tableRepresentative.getTableName())) {
            sb.append(tableRepresentative.getTableName());
        }
        if (notEmpty(tableRepresentative.getTableLongname())) {
            sb.append(" ").append(tableRepresentative.getTableLongname());
        }
        if (notEmpty(tableRepresentative.getTableExtension())) {
            sb.append(" [继承:").append(tableRepresentative.getTableExtension()).append("]");
        }

        // 2. 字段列表（去重，每个 field_id 只出现一次）
        Map<String, MetadataTablesDetail> fieldMap = new LinkedHashMap<>();
        for (MetadataTablesDetail d : tableDetails) {
            String key = notEmpty(d.getFieldId()) ? d.getFieldId() :
                         (notEmpty(d.getFieldDbname()) ? d.getFieldDbname() : "");
            if (!key.isEmpty() && !fieldMap.containsKey(key)) {
                fieldMap.put(key, d);
            }
        }
        if (!fieldMap.isEmpty()) {
            String fieldsText = fieldMap.values().stream()
                    .map(d -> {
                        // 格式：{fieldDbname}/{fieldId} {fieldLongname}[来源字典:{fieldRef}]
                        StringBuilder fs = new StringBuilder();
                        if (notEmpty(d.getFieldDbname()) && notEmpty(d.getFieldId())) {
                            fs.append(d.getFieldDbname()).append("/").append(d.getFieldId());
                        } else if (notEmpty(d.getFieldDbname())) {
                            fs.append(d.getFieldDbname());
                        } else if (notEmpty(d.getFieldId())) {
                            fs.append(d.getFieldId());
                        }
                        if (notEmpty(d.getFieldLongname())) {
                            fs.append(" ").append(d.getFieldLongname());
                        }
                        if (notEmpty(d.getFieldRef())) {
                            fs.append("[来源字典:").append(d.getFieldRef()).append("]");
                        }
                        return fs.toString().trim();
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));
            sb.append(" 字段:[").append(fieldsText).append("]");
        }

        // 3. ODB 方法列表（去重，按 odbindex_id）
        Map<String, MetadataTablesIndexes> odbMap = new LinkedHashMap<>();
        for (MetadataTablesIndexes idx : tableIndexes) {
            if (notEmpty(idx.getOdbindexId()) && !odbMap.containsKey(idx.getOdbindexId())) {
                odbMap.put(idx.getOdbindexId(), idx);
            }
        }
        if (!odbMap.isEmpty()) {
            String odbText = odbMap.values().stream()
                    .map(idx -> {
                        // 格式：{odbindexId}({odbindexType}) 字段:{odbindexFields} 操作:{odbindexOperate}
                        StringBuilder os = new StringBuilder();
                        os.append(idx.getOdbindexId());
                        if (notEmpty(idx.getOdbindexType())) {
                            os.append("(").append(idx.getOdbindexType()).append(")");
                        }
                        if (notEmpty(idx.getOdbindexFields())) {
                            os.append(" 字段:").append(idx.getOdbindexFields());
                        }
                        if (notEmpty(idx.getOdbindexOperate())) {
                            os.append(" 操作:").append(idx.getOdbindexOperate());
                        }
                        return os.toString();
                    })
                    .collect(Collectors.joining(", "));
            sb.append(" ODB方法:[").append(odbText).append("]");
        }

        return sb.toString();
    }

    /** 字段去重（按 fieldId 或 fieldDbname） */
    private List<MetadataTablesDetail> deduplicateTableFields(List<MetadataTablesDetail> details) {
        LinkedHashMap<String, MetadataTablesDetail> map = new LinkedHashMap<>();
        for (MetadataTablesDetail d : details) {
            String key = notEmpty(d.getFieldId()) ? d.getFieldId()
                    : (notEmpty(d.getFieldDbname()) ? d.getFieldDbname() : "");
            if (!key.isEmpty() && !map.containsKey(key)) map.put(key, d);
        }
        return new ArrayList<>(map.values());
    }

    /** 构建表头部文本 */
    private String buildTableHeader(MetadataTablesDetail rep) {
        StringBuilder sb = new StringBuilder();
        if (notEmpty(rep.getTableName())) sb.append(rep.getTableName());
        if (notEmpty(rep.getTableLongname())) sb.append(" ").append(rep.getTableLongname());
        if (notEmpty(rep.getTableExtension())) sb.append(" [继承:").append(rep.getTableExtension()).append("]");
        return sb.toString();
    }

    /** 构建单个 chunk 的向量化文本（头部 + 该批字段 + 可选 ODB 方法） */
    private String buildChunkText(String header, List<MetadataTablesDetail> chunkFields, List<MetadataTablesIndexes> indexes) {
        StringBuilder sb = new StringBuilder(header);

        if (!chunkFields.isEmpty()) {
            String fieldsText = chunkFields.stream().map(d -> {
                StringBuilder fs = new StringBuilder();
                if (notEmpty(d.getFieldDbname()) && notEmpty(d.getFieldId())) {
                    fs.append(d.getFieldDbname()).append("/").append(d.getFieldId());
                } else if (notEmpty(d.getFieldDbname())) {
                    fs.append(d.getFieldDbname());
                } else if (notEmpty(d.getFieldId())) {
                    fs.append(d.getFieldId());
                }
                if (notEmpty(d.getFieldLongname())) fs.append(" ").append(d.getFieldLongname());
                if (notEmpty(d.getFieldRef())) fs.append("[来源字典:").append(d.getFieldRef()).append("]");
                return fs.toString().trim();
            }).filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
            sb.append(" 字段:[").append(fieldsText).append("]");
        }

        if (!indexes.isEmpty()) {
            LinkedHashMap<String, MetadataTablesIndexes> odbMap = new LinkedHashMap<>();
            for (MetadataTablesIndexes idx : indexes) {
                if (notEmpty(idx.getOdbindexId()) && !odbMap.containsKey(idx.getOdbindexId())) {
                    odbMap.put(idx.getOdbindexId(), idx);
                }
            }
            if (!odbMap.isEmpty()) {
                String odbText = odbMap.values().stream().map(idx -> {
                    StringBuilder os = new StringBuilder(idx.getOdbindexId());
                    if (notEmpty(idx.getOdbindexType())) os.append("(").append(idx.getOdbindexType()).append(")");
                    if (notEmpty(idx.getOdbindexFields())) os.append(" 字段:").append(idx.getOdbindexFields());
                    if (notEmpty(idx.getOdbindexOperate())) os.append(" 操作:").append(idx.getOdbindexOperate());
                    return os.toString();
                }).collect(Collectors.joining(", "));
                sb.append(" ODB方法:[").append(odbText).append("]");
            }
        }

        return sb.toString();
    }

    /**
     * 构建 Qdrant payload
     */
    private Map<String, Object> buildPayload(MetadataTables schema, MetadataTablesDetail tableRep,
                                              String text, String sourceInfo, List<String> refIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model_type", MODEL_TYPE);
        payload.put("schema_id", schema.getId());
        payload.put("record_id", schema.getId() + ":" + tableRep.getTableId());
        payload.put("table_id", tableRep.getTableId() != null ? tableRep.getTableId() : "");
        payload.put("table_name", tableRep.getTableName() != null ? tableRep.getTableName() : "");
        payload.put("longname", tableRep.getTableLongname() != null ? tableRep.getTableLongname() : "");
        payload.put("table_extension", tableRep.getTableExtension() != null ? tableRep.getTableExtension() : "");
        payload.put("from_jar", sourceInfo);
        payload.put("text", text);
        payload.put("ref_ids", refIds);
        return payload;
    }
}
