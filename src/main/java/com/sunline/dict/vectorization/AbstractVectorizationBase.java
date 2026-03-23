package com.sunline.dict.vectorization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 向量化服务公共基类
 *
 * <p>封装 Qdrant REST API 交互的通用逻辑：upsert、delete by filter、工具方法。
 * <p>子类只需关注：数据查询、文本构建、payload 构建。
 */
public abstract class AbstractVectorizationBase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 基本类型集合，非基本类型认为是复合类型引用，生成 ref_id */
    protected static final Set<String> BASIC_TYPES = Set.of(
            "string", "int", "long", "double", "float", "decimal",
            "boolean", "date", "datetime", "timestamp", "char", "byte",
            "short", "BigDecimal", "BigInteger", "String", "Integer",
            "Long", "Double", "Float", "Boolean", "Date"
    );

    @Value("${vectorization.qdrant-host:localhost}")
    protected String qdrantHost;

    @Value("${vectorization.qdrant-port:6333}")
    protected int qdrantPort;

    @Value("${vectorization.collection:xml_models}")
    protected String collection;

    @Autowired
    protected EmbeddingClient embeddingClient;

    protected RestTemplate restTemplate;

    @PostConstruct
    public void initRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        restTemplate = new RestTemplate(factory);
        log.info("{} 初始化完成，Qdrant={}:{}", getClass().getSimpleName(), qdrantHost, qdrantPort);
    }

    // ─────────────── Qdrant REST 操作 ───────────────

    /**
     * 批量 upsert points 到 Qdrant
     * 相同 point_id 会自动覆盖旧数据
     */
    protected void upsertPoints(List<Map<String, Object>> points) {
        String url = buildBaseUrl() + "/points?wait=true";
        try {
            restTemplate.put(url, Map.of("points", points));
        } catch (Exception e) {
            log.error("Qdrant upsert 失败，错误：{}", e.getMessage(), e);
            throw new RuntimeException("Qdrant upsert 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 按 model_type + from_jar 删除 Qdrant 中的数据
     *
     * @param modelType  如 "service"、"component"、"flowtran"
     * @param sourceInfo from_jar 字段值
     */
    protected void deleteBySourceInternal(String modelType, String sourceInfo) {
        String url = buildBaseUrl() + "/points/delete";
        Map<String, Object> filter = buildMustFilter(
                matchKeyword("model_type", modelType),
                matchKeyword("from_jar", sourceInfo));
        try {
            restTemplate.postForObject(url, Map.of("filter", filter), Map.class);
            log.info("{} 向量删除完成，from_jar={}", modelType, sourceInfo);
        } catch (Exception e) {
            log.error("{} 向量删除失败，from_jar={}，错误：{}", modelType, sourceInfo, e.getMessage(), e);
            throw new RuntimeException("Qdrant 删除失败：" + e.getMessage(), e);
        }
    }

    // ─────────────── 工具方法 ───────────────

    /** 构建 Qdrant REST API 基础 URL */
    protected String buildBaseUrl() {
        return "http://" + qdrantHost + ":" + qdrantPort + "/collections/" + collection;
    }

    /** 根据字符串生成确定性 UUID（相同输入始终得到相同 UUID） */
    protected String toUuidString(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 构建单个 keyword 匹配条件 */
    protected Map<String, Object> matchKeyword(String key, String value) {
        return Map.of("key", key, "match", Map.of("value", value));
    }

    /** 构建 must 过滤条件（两个条件） */
    protected Map<String, Object> buildMustFilter(Map<String, Object> cond1, Map<String, Object> cond2) {
        List<Map<String, Object>> musts = new ArrayList<>();
        musts.add(cond1);
        musts.add(cond2);
        return Map.of("must", musts);
    }

    /** 判断字符串非空 */
    protected boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /** 判断是否是基本类型（非基本类型认为引用了复合类型） */
    protected boolean isBasicType(String type) {
        return BASIC_TYPES.contains(type);
    }

    /**
     * 拼接输入/输出字段文本到 StringBuilder
     * 供 ServiceVectorizationServiceImpl 和 ComponentVectorizationServiceImpl 共用
     *
     * @param sb     目标 StringBuilder
     * @param fields 字段信息列表（由子类从 ServiceDetail/ComponentDetail 转换而来）
     */
    protected void appendFieldsText(StringBuilder sb, List<FieldInfo> fields) {
        // 输入字段
        List<String> inputFields = fields.stream()
                .filter(f -> notEmpty(f.inputLongname))
                .map(f -> {
                    String part = f.inputLongname;
                    if (notEmpty(f.inputType)) {
                        part += "(" + f.inputType;
                        if ("true".equalsIgnoreCase(f.inputMulti)) part += "数组";
                        part += ")";
                    }
                    return part;
                })
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (!inputFields.isEmpty()) {
            sb.append(" 输入字段:[").append(String.join(", ", inputFields)).append("]");
        }

        // 输出字段
        List<String> outputFields = fields.stream()
                .filter(f -> notEmpty(f.outputLongname))
                .map(f -> {
                    String part = f.outputLongname;
                    if (notEmpty(f.outputType)) {
                        part += "(" + f.outputType;
                        if ("true".equalsIgnoreCase(f.outputMulti)) part += "数组";
                        part += ")";
                    }
                    return part;
                })
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (!outputFields.isEmpty()) {
            sb.append(" 输出字段:[").append(String.join(", ", outputFields)).append("]");
        }
    }

    /**
     * 从字段列表中提取复合类型 ref_ids
     */
    protected List<String> extractRefIdsFromFields(List<FieldInfo> fields) {
        Set<String> refSet = new java.util.HashSet<>();
        for (FieldInfo f : fields) {
            if (notEmpty(f.inputType) && !isBasicType(f.inputType)) {
                refSet.add("complex:" + f.inputType);
            }
            if (notEmpty(f.outputType) && !isBasicType(f.outputType)) {
                refSet.add("complex:" + f.outputType);
            }
        }
        return new ArrayList<>(refSet);
    }

    /** 每批字段数量（service/component 的输入+输出字段） */
    protected static final int FIELDS_PER_CHUNK = 25;

    /** 将 FieldInfo 列表按 FIELDS_PER_CHUNK 拆分 */
    protected <T> List<List<T>> splitList(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        if (chunks.isEmpty()) chunks.add(list);
        return chunks;
    }
}
