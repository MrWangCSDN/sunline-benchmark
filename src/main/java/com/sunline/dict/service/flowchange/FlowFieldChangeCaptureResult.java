package com.sunline.dict.service.flowchange;

import java.util.Map;

public record FlowFieldChangeCaptureResult(
        Map<String, String> afterContents,
        int successCount,
        int failedCount,
        int skippedCount) {

    public FlowFieldChangeCaptureResult {
        afterContents = Map.copyOf(afterContents);
    }

    public static FlowFieldChangeCaptureResult empty() {
        return new FlowFieldChangeCaptureResult(Map.of(), 0, 0, 0);
    }
}
