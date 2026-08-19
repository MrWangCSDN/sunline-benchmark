package com.sunline.dict.service.flowchange;

import java.time.LocalDateTime;

public record FlowFieldChangeCaptureMeta(
        String dedupKey, String webhookUuid, long projectId, String projectName,
        String branch, String filePath, String beforeSha, String afterSha,
        String commitSha, String commitMessage, String commitAuthor,
        String commitEmail, LocalDateTime commitTime) {
}
