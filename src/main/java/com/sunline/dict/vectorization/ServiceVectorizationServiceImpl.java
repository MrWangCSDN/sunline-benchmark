package com.sunline.dict.vectorization;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.ServiceDetail;
import com.sunline.dict.entity.ServiceFile;
import com.sunline.dict.mapper.ServiceDetailMapper;
import com.sunline.dict.mapper.ServiceFileMapper;
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
 * Service（pcs/pbs）向量化服务实现
 * 采用分批多 Point 方案：字段多时按 FIELDS_PER_CHUNK 拆分，每批独立向量化
 */
@Service
@ConditionalOnProperty(name = "vectorization.enabled", havingValue = "true")
public class ServiceVectorizationServiceImpl
        extends AbstractVectorizationBase
        implements ServiceVectorizationService {

    private static final String MODEL_TYPE = "service";

    @Autowired
    private ServiceFileMapper serviceFileMapper;

    @Autowired
    private ServiceDetailMapper serviceDetailMapper;

    @Override
    public void vectorizeBySource(String sourceInfo) {
        List<ServiceFile> serviceList = serviceFileMapper.selectList(
                new QueryWrapper<ServiceFile>().eq("from_jar", sourceInfo));

        if (serviceList.isEmpty()) {
            log.warn("未找到 from_jar={} 的 service 记录，跳过向量化", sourceInfo);
            return;
        }

        log.info("开始向量化 service，from_jar={}，共 {} 条", sourceInfo, serviceList.size());

        List<Map<String, Object>> points = new ArrayList<>();

        for (ServiceFile service : serviceList) {
            try {
                List<ServiceDetail> details = serviceDetailMapper.selectList(
                        new QueryWrapper<ServiceDetail>()
                                .eq("service_type_id", service.getId()));

                List<FieldInfo> allFieldInfos = details.stream()
                        .map(d -> new FieldInfo(
                                d.getInterfaceInputFieldLongname(), d.getInterfaceInputFieldType(), d.getInterfaceInputFieldMulti(),
                                d.getInterfaceOutputFieldLongname(), d.getInterfaceOutputFieldType(), d.getInterfaceOutputFieldMulti()))
                        .collect(Collectors.toList());

                List<String> refIds = extractRefIdsFromFields(allFieldInfos);
                String header = buildHeader(service, details);
                String fullText = buildFullText(header, allFieldInfos);

                // 分批生成 Point
                List<List<FieldInfo>> chunks = splitList(allFieldInfos, FIELDS_PER_CHUNK);

                for (int i = 0; i < chunks.size(); i++) {
                    StringBuilder chunkSb = new StringBuilder(header);
                    appendFieldsText(chunkSb, chunks.get(i));
                    String chunkText = chunkSb.toString();

                    List<Float> vector = embeddingClient.embed(chunkText);

                    Map<String, Object> point = new HashMap<>();
                    String suffix = chunks.size() > 1 ? ":chunk" + i : "";
                    point.put("id", toUuidString("service:" + service.getId() + suffix));
                    point.put("vector", vector);
                    point.put("payload", buildPayload(service, fullText, sourceInfo, refIds, i, chunks.size()));
                    points.add(point);
                }

                log.debug("service 向量化完成：id={}, detail数={}, Point数={}",
                        service.getId(), details.size(), chunks.size());

            } catch (Exception e) {
                log.error("service 向量化失败：id={}，错误：{}", service.getId(), e.getMessage(), e);
            }
        }

        if (!points.isEmpty()) {
            upsertPoints(points);
            log.info("service 向量写入完成，from_jar={}，成功 {} 个 Point", sourceInfo, points.size());
        }
    }

    @Override
    public void deleteBySource(String sourceInfo) {
        deleteBySourceInternal(MODEL_TYPE, sourceInfo);
    }

    private String buildHeader(ServiceFile service, List<ServiceDetail> details) {
        StringBuilder sb = new StringBuilder();
        sb.append(service.getId());
        if (notEmpty(service.getLongname()))    sb.append(" ").append(service.getLongname());
        if (notEmpty(service.getKind()))        sb.append(" ").append(service.getKind()).append("层");
        if (notEmpty(service.getServiceType())) sb.append("(").append(service.getServiceType()).append("服务)");

        Map<String, String> methodMap = new LinkedHashMap<>();
        for (ServiceDetail d : details) {
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

    private Map<String, Object> buildPayload(ServiceFile service, String fullText,
                                              String sourceInfo, List<String> refIds,
                                              int chunkIndex, int totalChunks) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model_type", MODEL_TYPE);
        payload.put("record_id", service.getId());
        payload.put("longname", service.getLongname() != null ? service.getLongname() : "");
        payload.put("kind", service.getKind() != null ? service.getKind() : "");
        payload.put("service_type", service.getServiceType() != null ? service.getServiceType() : "");
        payload.put("from_jar", sourceInfo);
        payload.put("text", fullText);
        payload.put("ref_ids", refIds);
        payload.put("chunk_index", chunkIndex);
        payload.put("total_chunks", totalChunks);
        return payload;
    }
}
