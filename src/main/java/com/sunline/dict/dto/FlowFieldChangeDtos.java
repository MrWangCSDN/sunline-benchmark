package com.sunline.dict.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sunline.dict.entity.FlowFieldChangeLog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FlowFieldChangeDtos {
    private static final Set<String> IO_TYPES = Set.of("input", "output");
    private static final Set<String> CHANGE_TYPES = Set.of("ADD", "MODIFY", "DELETE");
    private static final Set<String> CAPTURE_STATUSES = Set.of("SUCCESS", "FAILED");
    private static final Set<String> RUN_STATUSES = Set.of(
            "RUNNING", "SUCCESS", "COMPLETED_WITH_ERRORS", "FAILED");

    private FlowFieldChangeDtos() {
    }

    public record FieldChangeQuery(
            int current,
            int size,
            LocalDate startDate,
            LocalDate endDate,
            Long projectId,
            String projectName,
            String filePath,
            String flowId,
            String ioType,
            String fieldId,
            String changeType,
            String commitAuthor,
            String captureStatus) {
        public void validate() {
            validatePage(current, size);
            validateDates(startDate, endDate);
            validateOptional(ioType, IO_TYPES, "ioType");
            validateOptional(changeType, CHANGE_TYPES, "changeType");
            validateOptional(captureStatus, CAPTURE_STATUSES, "captureStatus");
        }
    }

    /** Mapper projection; JSON remains text until the service boundary. */
    public record FieldChangeRowData(
            Long logId,
            Long detailId,
            LocalDate changeDate,
            Long projectId,
            String projectName,
            String projectPath,
            String filePath,
            String flowId,
            String flowLongname,
            String fileChangeType,
            String captureStatus,
            String errorMessage,
            String ioType,
            String fieldPath,
            String fieldId,
            String detailChangeType,
            String changedAttributes,
            String commitSha,
            String commitMessage,
            String commitAuthor,
            String commitEmail,
            LocalDateTime commitTime) {
    }

    public record FieldChangeRowView(
            Long logId,
            Long detailId,
            LocalDate changeDate,
            Long projectId,
            String projectName,
            String projectPath,
            String filePath,
            String flowId,
            String flowLongname,
            String fileChangeType,
            String captureStatus,
            String errorMessage,
            String ioType,
            String fieldPath,
            String fieldId,
            String changeType,
            Map<String, ValueChangeView> changedAttributes,
            String commitSha,
            String commitMessage,
            String commitAuthor,
            String commitEmail,
            LocalDateTime commitTime) {
        public FieldChangeRowView {
            changedAttributes = immutableMap(changedAttributes);
        }
    }

    public record DetailView(
            Long id,
            String ioType,
            String fieldPath,
            String fieldId,
            String changeType,
            Map<String, String> oldSnapshot,
            Map<String, String> newSnapshot,
            Map<String, ValueChangeView> changedAttributes) {
        public DetailView {
            oldSnapshot = immutableMap(oldSnapshot);
            newSnapshot = immutableMap(newSnapshot);
            changedAttributes = immutableMap(changedAttributes);
        }
    }

    public record ValueChangeView(
            @JsonProperty("old") String oldValue,
            @JsonProperty("new") String newValue) {
    }

    public record HistoryLogView(
            Long id,
            Long scanRunId,
            LocalDate changeDate,
            Long projectId,
            String projectName,
            String projectPath,
            String branch,
            String filePath,
            String flowId,
            String flowLongname,
            String fileChangeType,
            String captureStatus,
            String errorMessage,
            String parentSha,
            String commitSha,
            String commitMessage,
            String commitAuthor,
            String commitEmail,
            LocalDateTime commitTime,
            Integer addCount,
            Integer modifyCount,
            Integer removeCount,
            Integer inputChangeCount,
            Integer outputChangeCount) {
        private static HistoryLogView from(FlowFieldChangeLog row) {
            return new HistoryLogView(
                    row.getId(), row.getScanRunId(), row.getChangeDate(), row.getProjectId(),
                    row.getProjectName(), row.getProjectPath(), row.getBranch(), row.getFilePath(),
                    row.getFlowId(), row.getFlowLongname(), row.getFileChangeType(),
                    row.getCaptureStatus(), row.getErrorMessage(), row.getParentSha(),
                    row.getCommitSha(), row.getCommitMessage(), row.getCommitAuthor(),
                    row.getCommitEmail(), row.getCommitTime(), row.getAddCount(),
                    row.getModifyCount(), row.getRemoveCount(), row.getInputChangeCount(),
                    row.getOutputChangeCount());
        }
    }

    public record FlowFieldChangeHistoryDetail(
            HistoryLogView log,
            List<DetailView> details) {
        public FlowFieldChangeHistoryDetail(FlowFieldChangeLog log, List<DetailView> details) {
            this(HistoryLogView.from(log), details);
        }

        public FlowFieldChangeHistoryDetail {
            details = List.copyOf(details);
        }
    }

    public record ScanRunQuery(
            int current,
            int size,
            Long projectId,
            String status,
            LocalDate startDate,
            LocalDate endDate) {
        public void validate() {
            validatePage(current, size);
            validateDates(startDate, endDate);
            validateOptional(status, RUN_STATUSES, "status");
        }
    }

    public record ScanRunView(
            Long id,
            Long projectId,
            String projectName,
            String projectPath,
            String branch,
            LocalDateTime windowStart,
            LocalDateTime windowEnd,
            String status,
            Integer commitCount,
            Integer changedFileCount,
            Integer historyCount,
            Integer failedFileCount,
            Integer skippedCount,
            Boolean cursorAdvanced,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt) {
    }

    private static void validatePage(int current, int size) {
        if (current < 1) {
            throw new IllegalArgumentException("current 不能小于 1");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size 必须在 1 到 100 之间");
        }
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate 不能早于 startDate");
        }
    }

    private static void validateOptional(String value, Set<String> allowed, String name) {
        if (value != null && !value.isBlank() && !allowed.contains(value)) {
            throw new IllegalArgumentException(name + " 非法");
        }
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return source == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
