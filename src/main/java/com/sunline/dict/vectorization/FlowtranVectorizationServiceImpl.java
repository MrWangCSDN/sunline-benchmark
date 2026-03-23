package com.sunline.dict.vectorization;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.FlowStep;
import com.sunline.dict.entity.Flowtran;
import com.sunline.dict.mapper.FlowStepMapper;
import com.sunline.dict.mapper.FlowtranMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Flowtran 向量化服务实现（使用 Qdrant REST API）
 *
 * <p>向量化文本格式：{id} {longname} 编排服务:[{nodeName} {nodeLongname}, ...]
 * <p>写入 Qdrant 的 payload 包含：model_type, record_id, longname, from_jar, text, ref_ids
 */
@Service
@ConditionalOnProperty(name = "vectorization.enabled", havingValue = "true")
public class FlowtranVectorizationServiceImpl implements FlowtranVectorizationService {

    private static final Logger log = LoggerFactory.getLogger(FlowtranVectorizationServiceImpl.class);

    private static final String MODEL_TYPE = "flowtran";

    @Value("${vectorization.qdrant-host:localhost}")
    private String qdrantHost;

    @Value("${vectorization.qdrant-port:6333}")
    private int qdrantPort;   // REST 端口，使用 6333

    @Value("${vectorization.collection:xml_models}")
    private String collection;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private FlowtranMapper flowtranMapper;

    @Autowired
    private FlowStepMapper flowStepMapper;

    private RestTemplate restTemplate;

    @jakarta.annotation.PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        restTemplate = new RestTemplate(factory);
        log.info("FlowtranVectorizationService 初始化，Qdrant REST={}:{}", qdrantHost, qdrantPort);
    }

    @Override
    public void vectorizeBySource(String sourceInfo) {
        List<Flowtran> flowtranList = flowtranMapper.selectList(
                new QueryWrapper<Flowtran>().eq("from_jar", sourceInfo));

        if (flowtranList.isEmpty()) {
            log.warn("未找到 from_jar={} 的 flowtran 记录，跳过向量化", sourceInfo);
            return;
        }

        log.info("开始向量化 flowtran，from_jar={}，共 {} 条", sourceInfo, flowtranList.size());

        List<Map<String, Object>> points = new ArrayList<>();

        for (Flowtran flowtran : flowtranList) {
            try {
                // 查询 node_type=service 的编排步骤
                List<FlowStep> serviceSteps = flowStepMapper.selectList(
                        new QueryWrapper<FlowStep>()
                                .eq("flow_id", flowtran.getId())
                                .eq("node_type", "service")
                                .orderByAsc("step"));

                // 构建向量化文本
                String text = buildVectorText(flowtran, serviceSteps);

                // 生成向量
                List<Float> vector = embeddingClient.embed(text);

                // ref_ids：引用的服务 point_id 列表
                List<String> refIds = serviceSteps.stream()
                        .filter(s -> s.getNodeName() != null && !s.getNodeName().isEmpty())
                        .map(s -> "service:" + s.getNodeName())
                        .distinct()
                        .collect(Collectors.toList());

                // 构建 Qdrant point（使用确定性 UUID，同一 flowtran.id 始终对应同一 point）
                Map<String, Object> point = new HashMap<>();
                point.put("id", toUuidString("flowtran:" + flowtran.getId()));
                point.put("vector", vector);
                point.put("payload", buildPayload(flowtran, text, sourceInfo, refIds));
                points.add(point);

                log.debug("flowtran 向量化完成：id={}, 服务步骤数={}", flowtran.getId(), serviceSteps.size());

            } catch (Exception e) {
                log.error("flowtran 向量化失败：id={}，错误：{}", flowtran.getId(), e.getMessage(), e);
            }
        }

        // 批量 upsert 到 Qdrant
        if (!points.isEmpty()) {
            upsertPoints(points);
            log.info("flowtran 向量写入完成，from_jar={}，成功 {} 条", sourceInfo, points.size());
        }
    }

    @Override
    public void deleteBySource(String sourceInfo) {
        String url = buildBaseUrl() + "/points/delete";

        // 构造按 from_jar + model_type 过滤的删除请求
        Map<String, Object> filter = buildMustFilter(
                matchKeyword("model_type", MODEL_TYPE),
                matchKeyword("from_jar", sourceInfo)
        );
        Map<String, Object> body = Map.of("filter", filter);

        try {
            restTemplate.postForObject(url, body, Map.class);
            log.info("flowtran 向量删除完成，from_jar={}", sourceInfo);
        } catch (Exception e) {
            log.error("flowtran 向量删除失败，from_jar={}，错误：{}", sourceInfo, e.getMessage(), e);
            throw new RuntimeException("Qdrant 删除失败：" + e.getMessage(), e);
        }
    }

    // ───────────────── 私有辅助方法 ─────────────────

    /**
     * 构建向量化文本：{id} {longname} 编排服务:[{nodeName} {nodeLongname}, ...]
     */
    private String buildVectorText(Flowtran flowtran, List<FlowStep> serviceSteps) {
        StringBuilder sb = new StringBuilder();
        sb.append(flowtran.getId());

        if (flowtran.getLongname() != null && !flowtran.getLongname().isEmpty()) {
            sb.append(" ").append(flowtran.getLongname());
        }

        if (!serviceSteps.isEmpty()) {
            String stepText = serviceSteps.stream()
                    .map(s -> {
                        String part = s.getNodeName() != null ? s.getNodeName() : "";
                        if (s.getNodeLongname() != null && !s.getNodeLongname().isEmpty()) {
                            part += " " + s.getNodeLongname();
                        }
                        return part.trim();
                    })
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));

            if (!stepText.isEmpty()) {
                sb.append(" 编排服务:[").append(stepText).append("]");
            }
        }

        return sb.toString();
    }

    /**
     * 构建 Qdrant payload（Map 格式，对应 REST 请求体的 payload 字段）
     */
    private Map<String, Object> buildPayload(Flowtran flowtran, String text,
                                              String sourceInfo, List<String> refIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model_type", MODEL_TYPE);
        payload.put("record_id", flowtran.getId());
        payload.put("longname", flowtran.getLongname() != null ? flowtran.getLongname() : "");
        payload.put("from_jar", sourceInfo);
        payload.put("text", text);
        payload.put("ref_ids", refIds);
        return payload;
    }

    /**
     * 调用 Qdrant REST API 批量 upsert points
     */
    private void upsertPoints(List<Map<String, Object>> points) {
        String url = buildBaseUrl() + "/points?wait=true";
        Map<String, Object> body = Map.of("points", points);
        try {
            restTemplate.put(url, body);
        } catch (Exception e) {
            log.error("Qdrant upsert 失败，错误：{}", e.getMessage(), e);
            throw new RuntimeException("Qdrant upsert 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构建 Qdrant REST API 基础 URL
     */
    private String buildBaseUrl() {
        return "http://" + qdrantHost + ":" + qdrantPort + "/collections/" + collection;
    }

    /**
     * 构建单个 keyword match 条件
     */
    private Map<String, Object> matchKeyword(String key, String value) {
        return Map.of("key", key, "match", Map.of("value", value));
    }

    /**
     * 构建 must 过滤条件
     */
    private Map<String, Object> buildMustFilter(Map<String, Object> cond1, Map<String, Object> cond2) {
        List<Map<String, Object>> musts = new ArrayList<>();
        musts.add(cond1);
        musts.add(cond2);
        return Map.of("must", musts);
    }

    /**
     * 根据字符串生成确定性 UUID（相同输入始终得到相同 UUID）
     */
    private String toUuidString(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
