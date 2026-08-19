package com.sunline.dict.service.flowchange;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/** Immutable commit/file metadata for the daily history writer. */
public record FlowFieldChangeMeta(
        long scanRunId,
        LocalDate changeDate,
        long projectId,
        String projectName,
        String projectPath,
        String branch,
        String effectiveFilePath,
        String parentSha,
        String commitSha,
        String commitMessage,
        String commitAuthor,
        String commitEmail,
        LocalDateTime commitTime) {

    public FlowFieldChangeMeta {
        if (scanRunId <= 0) {
            throw new IllegalArgumentException("scanRunId 必须大于 0");
        }
        if (projectId <= 0) {
            throw new IllegalArgumentException("projectId 必须大于 0");
        }
        Objects.requireNonNull(changeDate, "changeDate 不能为空");
        requireText(branch, "branch");
        requireText(effectiveFilePath, "effectiveFilePath");
        requireText(commitSha, "commitSha");
        Objects.requireNonNull(commitTime, "commitTime 不能为空");
    }

    public String dedupKey() {
        String material = projectId + "|" + branch + "|" + commitSha + "|" + effectiveFilePath;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
