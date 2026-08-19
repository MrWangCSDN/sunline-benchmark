package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.sunline.dict.dto.FlowFieldChangeDtos.DetailView;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.ValueChangeView;
import com.sunline.dict.entity.FlowFieldChangeDetail;
import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.mapper.FlowFieldChangeDetailMapper;
import com.sunline.dict.mapper.FlowFieldChangeLogMapper;
import com.sunline.dict.service.FlowFieldChangeLogService;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FieldChange;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.ValueChange;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class FlowFieldChangeLogServiceImpl implements FlowFieldChangeLogService {

    private static final Set<String> FILE_CHANGE_TYPES = Set.of("ADD", "MODIFY", "DELETE", "UNKNOWN");
    private static final Set<String> CAPTURE_STATUSES = Set.of("SUCCESS", "FAILED");
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final Pattern SENSITIVE_ERROR = Pattern.compile(
            "(?i)(?:authorization\\s*[:=]|x-gitlab-token|private[-_]?token|access[-_]?token|https?://)");

    private final FlowFieldChangeLogMapper logMapper;
    private final FlowFieldChangeDetailMapper detailMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public FlowFieldChangeLogServiceImpl(FlowFieldChangeLogMapper logMapper,
                                         FlowFieldChangeDetailMapper detailMapper,
                                         ObjectMapper objectMapper) {
        this.logMapper = logMapper;
        this.detailMapper = detailMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean existsByDedupKey(String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return false;
        }
        return logMapper.selectCount(new QueryWrapper<FlowFieldChangeLog>()
                .eq("dedup_key", dedupKey)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowFieldChangeLog recordSuccess(FlowFieldChangeCaptureMeta meta,
                                            FlowFieldChangeSet changeSet) {
        if (meta == null || changeSet == null) {
            throw new IllegalArgumentException("采集元信息和变更集不能为空");
        }
        FlowFieldChangeLog log = header(meta);
        log.setFlowId(changeSet.flowId());
        log.setFlowLongname(changeSet.flowLongname());
        log.setFileChangeType(changeSet.fileChangeType().name());
        log.setCaptureStatus("SUCCESS");
        log.setAddCount(changeSet.addCount());
        log.setModifyCount(changeSet.modifyCount());
        log.setRemoveCount(changeSet.removeCount());
        log.setInputChangeCount(changeSet.inputChangeCount());
        log.setOutputChangeCount(changeSet.outputChangeCount());
        insertHeader(log);

        for (FieldChange change : changeSet.details()) {
            FlowFieldChangeDetail detail = new FlowFieldChangeDetail();
            detail.setLogId(log.getId());
            detail.setIoType(change.identity().ioType());
            detail.setFieldPath(change.identity().fieldPath());
            detail.setFieldId(change.identity().fieldId());
            detail.setChangeType(change.changeType().name());
            detail.setOldSnapshot(writeJson(change.oldSnapshot()));
            detail.setNewSnapshot(writeJson(change.newSnapshot()));
            detail.setChangedAttributes(writeChangedAttributes(change.changedAttributes()));
            detailMapper.insert(detail);
        }
        return log;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowFieldChangeLog recordFailure(FlowFieldChangeCaptureMeta meta, String errorMessage) {
        if (meta == null) {
            throw new IllegalArgumentException("采集元信息不能为空");
        }
        FlowFieldChangeLog log = header(meta);
        log.setFileChangeType("UNKNOWN");
        log.setCaptureStatus("FAILED");
        log.setErrorMessage(safeError(errorMessage));
        log.setAddCount(0);
        log.setModifyCount(0);
        log.setRemoveCount(0);
        log.setInputChangeCount(0);
        log.setOutputChangeCount(0);
        insertHeader(log);
        return log;
    }

    @Override
    public Page<FlowFieldChangeLog> pageLogs(FlowFieldChangeQuery query) {
        validate(query);
        QueryWrapper<FlowFieldChangeLog> wrapper = new QueryWrapper<>();
        wrapper.eq(hasText(query.flowId()), "flow_id", query.flowId())
                .like(hasText(query.filePath()), "file_path", query.filePath())
                .like(hasText(query.commitAuthor()), "commit_author", query.commitAuthor())
                .eq(hasText(query.fileChangeType()), "file_change_type", query.fileChangeType())
                .eq(hasText(query.captureStatus()), "capture_status", query.captureStatus())
                .ge(query.startTime() != null, "commit_time", query.startTime())
                .le(query.endTime() != null, "commit_time", query.endTime())
                .orderByDesc("COALESCE(commit_time, create_time)", "id");
        return logMapper.selectPage(new Page<>(query.current(), query.size()), wrapper);
    }

    @Override
    public FlowFieldChangeHistoryDetail getDetail(long logId) {
        FlowFieldChangeLog log = logMapper.selectById(logId);
        if (log == null) {
            throw new NoSuchElementException("交易接口变动历史不存在");
        }
        List<FlowFieldChangeDetail> rows = detailMapper.selectList(
                new QueryWrapper<FlowFieldChangeDetail>()
                        .eq("log_id", logId)
                        .orderByAsc("id"));
        List<DetailView> details = rows.stream().map(this::toView).toList();
        return new FlowFieldChangeHistoryDetail(log, details);
    }

    private FlowFieldChangeLog header(FlowFieldChangeCaptureMeta meta) {
        FlowFieldChangeLog log = new FlowFieldChangeLog();
        log.setDedupKey(meta.dedupKey());
        log.setWebhookUuid(meta.webhookUuid());
        log.setProjectId(meta.projectId());
        log.setProjectName(meta.projectName());
        log.setBranch(meta.branch());
        log.setFilePath(meta.filePath());
        log.setBeforeSha(meta.beforeSha());
        log.setAfterSha(meta.afterSha());
        log.setCommitSha(meta.commitSha());
        log.setCommitMessage(meta.commitMessage());
        log.setCommitAuthor(meta.commitAuthor());
        log.setCommitEmail(meta.commitEmail());
        log.setCommitTime(meta.commitTime());
        return log;
    }

    private void insertHeader(FlowFieldChangeLog log) {
        logMapper.insert(log);
        if (log.getId() == null) {
            throw new IllegalStateException("未生成变动历史主键");
        }
    }

    private DetailView toView(FlowFieldChangeDetail row) {
        return new DetailView(
                row.getId(), row.getIoType(), row.getFieldPath(), row.getFieldId(), row.getChangeType(),
                readStringMap(row.getOldSnapshot()), readStringMap(row.getNewSnapshot()),
                readValueChanges(row.getChangedAttributes()));
    }

    private String writeJson(Map<?, ?> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("字段变动 JSON 序列化失败", exception);
        }
    }

    private String writeChangedAttributes(Map<String, ValueChange> changes) {
        if (changes == null) {
            return null;
        }
        Map<String, Map<String, String>> json = new TreeMap<>();
        changes.forEach((name, change) -> {
            Map<String, String> values = new TreeMap<>();
            values.put("old", change.oldValue());
            values.put("new", change.newValue());
            json.put(name, values);
        });
        return writeJson(json);
    }

    private Map<String, String> readStringMap(String json) {
        if (json == null) {
            return null;
        }
        MapType type = objectMapper.getTypeFactory().constructMapType(
                LinkedHashMap.class, String.class, String.class);
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("字段快照 JSON 解析失败", exception);
        }
    }

    private Map<String, ValueChangeView> readValueChanges(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            Map<String, ValueChangeView> result = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                result.put(entry.getKey(), new ValueChangeView(text(value.get("old")), text(value.get("new"))));
            });
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("变动属性 JSON 解析失败", exception);
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String safeError(String errorMessage) {
        String safe = errorMessage == null || errorMessage.isBlank() ? "采集失败" : errorMessage;
        if (SENSITIVE_ERROR.matcher(safe).find()) {
            return "采集失败（敏感信息已隐藏）";
        }
        safe = safe.replace('\r', ' ').replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        return safe.length() <= MAX_ERROR_LENGTH ? safe : safe.substring(0, MAX_ERROR_LENGTH);
    }

    private static void validate(FlowFieldChangeQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("查询条件不能为空");
        }
        if (query.current() < 1) {
            throw new IllegalArgumentException("current 不能小于 1");
        }
        if (query.size() < 1 || query.size() > 100) {
            throw new IllegalArgumentException("size 必须在 1 到 100 之间");
        }
        if (hasText(query.fileChangeType()) && !FILE_CHANGE_TYPES.contains(query.fileChangeType())) {
            throw new IllegalArgumentException("fileChangeType 非法");
        }
        if (hasText(query.captureStatus()) && !CAPTURE_STATUSES.contains(query.captureStatus())) {
            throw new IllegalArgumentException("captureStatus 非法");
        }
        if (query.startTime() != null && query.endTime() != null
                && query.endTime().isBefore(query.startTime())) {
            throw new IllegalArgumentException("endTime 不能早于 startTime");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
