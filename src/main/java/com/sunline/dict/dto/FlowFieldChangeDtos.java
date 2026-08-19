package com.sunline.dict.dto;

import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.ValueChange;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FlowFieldChangeDtos {
    private FlowFieldChangeDtos() {
    }

    public record FlowFieldChangeQuery(
            int current, int size, String flowId, String filePath,
            String commitAuthor, String fileChangeType, String captureStatus,
            LocalDateTime startTime, LocalDateTime endTime) {
    }

    public record DetailView(
            Long id, String ioType, String fieldPath, String fieldId,
            String changeType, Map<String, String> oldSnapshot,
            Map<String, String> newSnapshot,
            Map<String, ValueChange> changedAttributes) {
    }

    public record FlowFieldChangeHistoryDetail(
            FlowFieldChangeLog log, List<DetailView> details) {
    }
}
