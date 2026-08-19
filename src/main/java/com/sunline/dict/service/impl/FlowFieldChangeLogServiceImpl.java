package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.sunline.dict.dto.FlowFieldChangeDtos.DetailView;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowData;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowView;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunView;
import com.sunline.dict.dto.FlowFieldChangeDtos.ValueChangeView;
import com.sunline.dict.entity.FlowFieldChangeDetail;
import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.mapper.FlowFieldChangeDetailMapper;
import com.sunline.dict.mapper.FlowFieldChangeLogMapper;
import com.sunline.dict.mapper.FlowFieldChangeQueryMapper;
import com.sunline.dict.service.FlowFieldChangeLogService;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeMeta;
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
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
public class FlowFieldChangeLogServiceImpl implements FlowFieldChangeLogService {

    private static final int MAX_ERROR_LENGTH = 2000;
    private static final Pattern UNSAFE_ERROR = Pattern.compile(
            "(?is)(?:"
                    + "authorization|bearer|token|secret|password"
                    + "|\\b[a-z][a-z0-9+.-]*://|\\bjdbc:"
                    + "|\\b(?:select|insert|update|delete|alter|drop|create|truncate|merge|call|grant|revoke)"
                    + "\\b\\s+\\S+"
                    + "|(?:^|\\R)\\s*at\\s+(?:[^\\s/]+/)?[\\w.$]+\\([^\\r\\n)]*\\)"
                    + "|\\bcaused\\s+by:|\\b[\\w.$]+(?:exception|error):"
                    + ")");

    private final FlowFieldChangeLogMapper logMapper;
    private final FlowFieldChangeDetailMapper detailMapper;
    private final FlowFieldChangeQueryMapper queryMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public FlowFieldChangeLogServiceImpl(FlowFieldChangeLogMapper logMapper,
                                         FlowFieldChangeDetailMapper detailMapper,
                                         FlowFieldChangeQueryMapper queryMapper,
                                         ObjectMapper objectMapper) {
        this.logMapper = logMapper;
        this.detailMapper = detailMapper;
        this.queryMapper = queryMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public HistoryState state(String dedupKey) {
        if (!hasText(dedupKey)) {
            return HistoryState.NONE;
        }
        FlowFieldChangeLog existing = logMapper.selectByDedupKey(dedupKey);
        if (existing == null) {
            return HistoryState.NONE;
        }
        return "SUCCESS".equals(existing.getCaptureStatus())
                ? HistoryState.SUCCESS
                : HistoryState.FAILED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WriteOutcome recordSuccess(FlowFieldChangeMeta meta, FlowFieldChangeSet changeSet) {
        if (meta == null || changeSet == null) {
            throw new IllegalArgumentException("采集元信息和变更集不能为空");
        }
        rejectEmptyModify(changeSet);
        FlowFieldChangeLog existing = logMapper.selectByDedupKey(meta.dedupKey());
        if (isSuccess(existing)) {
            return skipped(existing);
        }

        FlowFieldChangeLog row = dailyHeader(meta);
        applySuccess(row, changeSet);
        WriteDisposition disposition = saveHeader(existing, row);
        insertDetails(row.getId(), changeSet.details());
        return new WriteOutcome(row.getId(), disposition);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WriteOutcome recordFailure(FlowFieldChangeMeta meta, String safeError) {
        if (meta == null) {
            throw new IllegalArgumentException("采集元信息不能为空");
        }
        FlowFieldChangeLog existing = logMapper.selectByDedupKey(meta.dedupKey());
        if (isSuccess(existing)) {
            return skipped(existing);
        }

        FlowFieldChangeLog row = dailyHeader(meta);
        applyFailure(row, safeError);
        WriteDisposition disposition = saveHeader(existing, row);
        return new WriteOutcome(row.getId(), disposition);
    }

    @Override
    public Page<FieldChangeRowView> pageFieldChanges(FieldChangeQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("查询条件不能为空");
        }
        query.validate();
        Page<FieldChangeRowData> databasePage = queryMapper.selectFieldChanges(
                new Page<>(query.current(), query.size()), query);
        Page<FieldChangeRowView> result = new Page<>(
                databasePage.getCurrent(), databasePage.getSize(), databasePage.getTotal());
        result.setRecords(databasePage.getRecords().stream().map(this::toFieldView).toList());
        return result;
    }

    @Override
    public FlowFieldChangeHistoryDetail getDetail(long logId) {
        FlowFieldChangeLog log = logMapper.selectById(logId);
        if (log == null || log.getScanRunId() == null) {
            throw new NoSuchElementException("交易接口变动历史不存在");
        }
        List<DetailView> details = detailMapper.selectByLogId(logId).stream()
                .map(this::toDetailView)
                .toList();
        return new FlowFieldChangeHistoryDetail(log, details);
    }

    @Override
    public Page<ScanRunView> pageScanRuns(ScanRunQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("查询条件不能为空");
        }
        query.validate();
        return queryMapper.selectScanRuns(new Page<>(query.current(), query.size()), query);
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean existsByDedupKey(String dedupKey) {
        return state(dedupKey) != HistoryState.NONE;
    }

    @Override
    @Deprecated(forRemoval = true)
    @Transactional(rollbackFor = Exception.class)
    public WriteOutcome recordSuccess(FlowFieldChangeCaptureMeta meta, FlowFieldChangeSet changeSet) {
        if (meta == null || changeSet == null) {
            throw new IllegalArgumentException("采集元信息和变更集不能为空");
        }
        rejectEmptyModify(changeSet);
        return recordLegacy(meta, changeSet, null);
    }

    @Override
    @Deprecated(forRemoval = true)
    @Transactional(rollbackFor = Exception.class)
    public WriteOutcome recordFailure(FlowFieldChangeCaptureMeta meta, String safeError) {
        if (meta == null) {
            throw new IllegalArgumentException("采集元信息不能为空");
        }
        return recordLegacy(meta, null, safeError);
    }

    private WriteOutcome recordLegacy(FlowFieldChangeCaptureMeta meta,
                                      FlowFieldChangeSet changeSet,
                                      String safeError) {
        FlowFieldChangeLog existing = logMapper.selectByDedupKey(meta.dedupKey());
        if (isSuccess(existing)) {
            return skipped(existing);
        }
        FlowFieldChangeLog row = legacyHeader(meta);
        if (changeSet == null) {
            applyFailure(row, safeError);
        } else {
            applySuccess(row, changeSet);
        }
        WriteDisposition disposition = saveHeader(existing, row);
        if (changeSet != null) {
            insertDetails(row.getId(), changeSet.details());
        }
        return new WriteOutcome(row.getId(), disposition);
    }

    private WriteDisposition saveHeader(FlowFieldChangeLog existing, FlowFieldChangeLog row) {
        if (existing == null) {
            insertHeader(row);
            return WriteDisposition.INSERTED;
        }
        row.setId(existing.getId());
        detailMapper.deleteByLogId(existing.getId());
        if (logMapper.updateDaily(row) != 1) {
            throw new IllegalStateException("更新变动历史失败");
        }
        return WriteDisposition.UPGRADED;
    }

    private void insertHeader(FlowFieldChangeLog row) {
        logMapper.insert(row);
        if (row.getId() == null) {
            throw new IllegalStateException("未生成变动历史主键");
        }
    }

    private void insertDetails(long logId, List<FieldChange> changes) {
        for (FieldChange change : changes) {
            FlowFieldChangeDetail detail = new FlowFieldChangeDetail();
            detail.setLogId(logId);
            detail.setIoType(change.identity().ioType());
            detail.setFieldPath(change.identity().fieldPath());
            detail.setFieldId(change.identity().fieldId());
            detail.setChangeType(change.changeType().name());
            detail.setOldSnapshot(writeJson(change.oldSnapshot()));
            detail.setNewSnapshot(writeJson(change.newSnapshot()));
            detail.setChangedAttributes(writeChangedAttributes(change.changedAttributes()));
            detailMapper.insert(detail);
        }
    }

    private FlowFieldChangeLog dailyHeader(FlowFieldChangeMeta meta) {
        FlowFieldChangeLog row = new FlowFieldChangeLog();
        row.setDedupKey(meta.dedupKey());
        row.setScanRunId(meta.scanRunId());
        row.setChangeDate(meta.changeDate());
        row.setProjectId(meta.projectId());
        row.setProjectName(meta.projectName());
        row.setProjectPath(meta.projectPath());
        row.setBranch(meta.branch());
        row.setFilePath(meta.effectiveFilePath());
        row.setParentSha(meta.parentSha());
        row.setCommitSha(meta.commitSha());
        row.setCommitMessage(meta.commitMessage());
        row.setCommitAuthor(meta.commitAuthor());
        row.setCommitEmail(meta.commitEmail());
        row.setCommitTime(meta.commitTime());
        return row;
    }

    private FlowFieldChangeLog legacyHeader(FlowFieldChangeCaptureMeta meta) {
        FlowFieldChangeLog row = new FlowFieldChangeLog();
        row.setDedupKey(meta.dedupKey());
        row.setWebhookUuid(meta.webhookUuid());
        row.setProjectId(meta.projectId());
        row.setProjectName(meta.projectName());
        row.setBranch(meta.branch());
        row.setFilePath(meta.filePath());
        row.setBeforeSha(meta.beforeSha());
        row.setAfterSha(meta.afterSha());
        row.setCommitSha(meta.commitSha());
        row.setCommitMessage(meta.commitMessage());
        row.setCommitAuthor(meta.commitAuthor());
        row.setCommitEmail(meta.commitEmail());
        row.setCommitTime(meta.commitTime());
        return row;
    }

    private static void applySuccess(FlowFieldChangeLog row, FlowFieldChangeSet changeSet) {
        row.setFlowId(changeSet.flowId());
        row.setFlowLongname(changeSet.flowLongname());
        row.setFileChangeType(changeSet.fileChangeType().name());
        row.setCaptureStatus("SUCCESS");
        row.setErrorMessage(null);
        row.setAddCount(changeSet.addCount());
        row.setModifyCount(changeSet.modifyCount());
        row.setRemoveCount(changeSet.removeCount());
        row.setInputChangeCount(changeSet.inputChangeCount());
        row.setOutputChangeCount(changeSet.outputChangeCount());
    }

    private static void applyFailure(FlowFieldChangeLog row, String safeError) {
        row.setFileChangeType("UNKNOWN");
        row.setCaptureStatus("FAILED");
        row.setErrorMessage(normalizeError(safeError));
        row.setAddCount(0);
        row.setModifyCount(0);
        row.setRemoveCount(0);
        row.setInputChangeCount(0);
        row.setOutputChangeCount(0);
    }

    private FieldChangeRowView toFieldView(FieldChangeRowData row) {
        String changeType = row.detailChangeType() == null
                ? row.fileChangeType()
                : row.detailChangeType();
        return new FieldChangeRowView(
                row.logId(), row.detailId(), row.changeDate(), row.projectId(), row.projectName(),
                row.projectPath(), row.filePath(), row.flowId(), row.flowLongname(),
                row.fileChangeType(), row.captureStatus(), row.errorMessage(), row.ioType(),
                row.fieldPath(), row.fieldId(), changeType,
                readValueChanges(row.changedAttributes()), row.commitSha(), row.commitMessage(),
                row.commitAuthor(), row.commitEmail(), row.commitTime());
    }

    private DetailView toDetailView(FlowFieldChangeDetail row) {
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
                result.put(entry.getKey(), new ValueChangeView(
                        text(value.get("old")), text(value.get("new"))));
            });
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("变动属性 JSON 解析失败", exception);
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static WriteOutcome skipped(FlowFieldChangeLog existing) {
        return new WriteOutcome(existing.getId(), WriteDisposition.SKIPPED);
    }

    private static boolean isSuccess(FlowFieldChangeLog row) {
        return row != null && "SUCCESS".equals(row.getCaptureStatus());
    }

    private static void rejectEmptyModify(FlowFieldChangeSet changeSet) {
        if (changeSet.fileChangeType() == FlowFieldChangeSet.FileChangeType.MODIFY
                && changeSet.details().isEmpty()) {
            throw new IllegalArgumentException("修改文件没有字段差异");
        }
    }

    private static String normalizeError(String error) {
        if (!hasText(error)) {
            return "采集失败";
        }
        if (UNSAFE_ERROR.matcher(error).find()) {
            return "采集失败（敏感信息已隐藏）";
        }
        String normalized = error.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\p{Cntrl}", "");
        return normalized.length() <= MAX_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_LENGTH);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
