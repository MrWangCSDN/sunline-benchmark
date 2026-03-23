package com.sunline.dict.vectorization;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Component;
import com.sunline.dict.entity.ComponentDetail;
import com.sunline.dict.mapper.ComponentDetailMapper;
import com.sunline.dict.mapper.ComponentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Component（pbcb/pbcp/pbcc/pbct）向量化服务实现
 * 采用分批多 Point 方案
 */
@Service
@ConditionalOnProperty(name = "vectorization.enabled", havingValue = "true")
public class ComponentVectorizationServiceImpl
        extends AbstractVectorizationBase
        implements ComponentVectorizationService {

    private static final String MODEL_TYPE = "component";

    @Autowired
    private ComponentMapper componentMapper;

    @Autowired
    private ComponentDetailMapper componentDetailMapper;

    @Override
    public void vectorizeBySource(String sourceInfo) {
        List<Component> componentList = componentMapper.selectList(
                new QueryWrapper<Component>().eq("from_jar", sourceInfo));

        if (componentList.isEmpty()) {
            log.warn("未找到 from_jar={} 的 component 记录，跳过向量化", sourceInfo);
            return;
        }

        log.info("开始向量化 component，from_jar={}，共 {} 条", sourceInfo, componentList.size());

        List<Map<String, Object>> points = new ArrayList<>();

        for (Component component : componentList) {
            try {
                List<ComponentDetail> details = componentDetailMapper.selectList(
                        new QueryWrapper<ComponentDetail>()
                                .eq("component_id", component.getId()));

                List<FieldInfo> allFieldInfos = details.stream()
                        .map(d -> new FieldInfo(
                                d.getInterfaceInputFieldLongname(), d.getInterfaceInputFieldType(), d.getInterfaceInputFieldMulti(),
                                d.getInterfaceOutputFieldLongname(), d.getInterfaceOutputFieldType(), d.getInterfaceOutputFieldMulti()))
                        .collect(Collectors.toList());

                List<String> refIds = extractRefIdsFromFields(allFieldInfos);
                String header = buildHeader(component, details);
                String fullText = buildFullText(header, allFieldInfos);

                List<List<FieldInfo>> chunks = splitList(allFieldInfos, FIELDS_PER_CHUNK);

                for (int i = 0; i < chunks.size(); i++) {
                    StringBuilder chunkSb = new StringBuilder(header);
                    appendFieldsText(chunkSb, chunks.get(i));

                    List<Float> vector = embeddingClient.embed(chunkSb.toString());

                    Map<String, Object> point = new HashMap<>();
                    String suffix = chunks.size() > 1 ? ":chunk" + i : "";
                    point.put("id", toUuidString("component:" + component.getId() + suffix));
                    point.put("vector", vector);
                    point.put("payload", buildPayload(component, fullText, sourceInfo, refIds, i, chunks.size()));
                    points.add(point);
                }

                log.debug("component 向量化完成：id={}, detail数={}, Point数={}",
                        component.getId(), details.size(), chunks.size());

            } catch (Exception e) {
                log.error("component 向量化失败：id={}，错误：{}", component.getId(), e.getMessage(), e);
            }
        }

        if (!points.isEmpty()) {
            upsertPoints(points);
            log.info("component 向量写入完成，from_jar={}，成功 {} 个 Point", sourceInfo, points.size());
        }
    }

    @Override
    public void deleteBySource(String sourceInfo) {
        deleteBySourceInternal(MODEL_TYPE, sourceInfo);
    }

    private String buildHeader(Component component, List<ComponentDetail> details) {
        StringBuilder sb = new StringBuilder();
        sb.append(component.getId());
        if (notEmpty(component.getLongname()))      sb.append(" ").append(component.getLongname());
        if (notEmpty(component.getKind()))          sb.append(" ").append(component.getKind()).append("层");
        if (notEmpty(component.getComponentType())) sb.append("(").append(component.getComponentType()).append("构件)");

        Map<String, String> methodMap = new LinkedHashMap<>();
        for (ComponentDetail d : details) {
            if (notEmpty(d.getServiceId()) && !methodMap.containsKey(d.getServiceId())) {
                methodMap.put(d.getServiceId(), d.getServiceLongname() != null ? d.getServiceLongname() : "");
            }
        }
        if (!methodMap.isEmpty()) {
            String methods = methodMap.entrySet().stream()
                    .map(e -> notEmpty(e.getValue()) ? e.getKey() + " " + e.getValue() : e.getKey())
                    .collect(Collectors.joining(", "));
            sb.append(" 服务方法:[").append(methods).append("]");
        }
        return sb.toString();
    }

    private String buildFullText(String header, List<FieldInfo> allFieldInfos) {
        StringBuilder sb = new StringBuilder(header);
        appendFieldsText(sb, allFieldInfos);
        return sb.toString();
    }

    private Map<String, Object> buildPayload(Component component, String fullText,
                                              String sourceInfo, List<String> refIds,
                                              int chunkIndex, int totalChunks) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model_type", MODEL_TYPE);
        payload.put("record_id", component.getId());
        payload.put("longname", component.getLongname() != null ? component.getLongname() : "");
        payload.put("kind", component.getKind() != null ? component.getKind() : "");
        payload.put("component_type", component.getComponentType() != null ? component.getComponentType() : "");
        payload.put("from_jar", sourceInfo);
        payload.put("text", fullText);
        payload.put("ref_ids", refIds);
        payload.put("chunk_index", chunkIndex);
        payload.put("total_chunks", totalChunks);
        return payload;
    }
}
