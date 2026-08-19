package com.sunline.dict.service.impl;

import com.sunline.dict.service.FlowFieldChangeCaptureService;
import com.sunline.dict.service.FlowFieldChangeLogService;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureResult;
import com.sunline.dict.service.flowchange.FlowFieldChangeDiffService;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshotParser;
import com.sunline.dict.service.flowchange.GitLabFileVersionService;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.FileVersionResult;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "git.gitlab.url")
public class FlowFieldChangeCaptureServiceImpl implements FlowFieldChangeCaptureService {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldChangeCaptureServiceImpl.class);
    private static final String MASTER_REF = "refs/heads/master";

    private final GitLabFileVersionService fileService;
    private final FlowFieldChangeLogService logService;
    private final FlowtransInterfaceSnapshotParser parser;
    private final FlowFieldChangeDiffService diffService;

    @Autowired
    public FlowFieldChangeCaptureServiceImpl(GitLabFileVersionService fileService,
                                              FlowFieldChangeLogService logService) {
        this.fileService = fileService;
        this.logService = logService;
        this.parser = new FlowtransInterfaceSnapshotParser();
        this.diffService = new FlowFieldChangeDiffService();
    }

    @Override
    public FlowFieldChangeCaptureResult capture(Map<String, Object> payload, String eventUuid) {
        if (payload == null || !MASTER_REF.equals(payload.get("ref"))) {
            return FlowFieldChangeCaptureResult.empty();
        }

        Map<String, CommitInfo> files = collectFiles(payload.get("commits"));
        if (files.isEmpty()) {
            return FlowFieldChangeCaptureResult.empty();
        }

        ProjectInfo project = projectInfo(payload.get("project"));
        String beforeSha = text(payload.get("before"));
        String afterSha = text(payload.get("after"));
        Map<String, String> afterContents = new LinkedHashMap<>();
        int successes = 0;
        int failures = 0;
        int skipped = 0;

        for (Map.Entry<String, CommitInfo> entry : files.entrySet()) {
            String filePath = entry.getKey();
            FlowFieldChangeCaptureMeta meta = meta(project, filePath, beforeSha, afterSha,
                    eventUuid, entry.getValue());
            try {
                if (logService.existsByDedupKey(meta.dedupKey())) {
                    skipped++;
                    continue;
                }

                SnapshotVersion before = readVersion(project, filePath, beforeSha, "before");
                SnapshotVersion after = readVersion(project, filePath, afterSha, "after");
                if (after.content() != null) {
                    afterContents.put(filePath, after.content());
                }
                if (before.content() == null && after.content() == null) {
                    skipped++;
                    continue;
                }

                FlowtransInterfaceSnapshot beforeSnapshot = parse(before.content());
                FlowtransInterfaceSnapshot afterSnapshot = parse(after.content());
                Optional<FlowFieldChangeSet> changeSet = diffService.diff(beforeSnapshot, afterSnapshot);
                if (changeSet.isEmpty()) {
                    skipped++;
                    continue;
                }

                logService.recordSuccess(meta, changeSet.orElseThrow());
                successes++;
            } catch (Exception exception) {
                failures++;
                recordFailure(meta, exception);
            }
        }

        return new FlowFieldChangeCaptureResult(afterContents, successes, failures, skipped);
    }

    private SnapshotVersion readVersion(ProjectInfo project, String filePath, String sha,
                                        String versionName) {
        if (isAbsentSha(sha)) {
            return new SnapshotVersion(null);
        }
        FileVersionResult result = fileService.fetch(
                project.projectId(), project.pathWithNamespace(), filePath, sha);
        if (result == null || result.status() == Status.FAILED) {
            String reason = result == null ? "empty GitLab response" : result.errorMessage();
            throw new IllegalStateException("读取 " + versionName + " 版本失败：" + safeReason(reason));
        }
        if (result.status() == Status.NOT_FOUND) {
            return new SnapshotVersion(null);
        }
        if (result.status() != Status.FOUND || result.content() == null) {
            throw new IllegalStateException("读取 " + versionName + " 版本失败：invalid GitLab response");
        }
        return new SnapshotVersion(result.content());
    }

    private FlowtransInterfaceSnapshot parse(String content) {
        return content == null ? null : parser.parse(content);
    }

    private void recordFailure(FlowFieldChangeCaptureMeta meta, Exception exception) {
        try {
            logService.recordFailure(meta, safeReason(exception.getMessage()));
        } catch (Exception persistenceException) {
            log.error("记录 flowtrans 接口采集失败历史失败，文件: {}", meta.filePath(), persistenceException);
        }
    }

    private Map<String, CommitInfo> collectFiles(Object commitsValue) {
        Map<String, CommitInfo> files = new LinkedHashMap<>();
        if (!(commitsValue instanceof List<?> commits)) {
            return files;
        }
        for (Object commitValue : commits) {
            if (!(commitValue instanceof Map<?, ?> commit)) {
                continue;
            }
            CommitInfo commitInfo = commitInfo(commit);
            collectPaths(commit.get("added"), commitInfo, files);
            collectPaths(commit.get("modified"), commitInfo, files);
            collectPaths(commit.get("removed"), commitInfo, files);
        }
        return files;
    }

    private void collectPaths(Object pathsValue, CommitInfo commit,
                              Map<String, CommitInfo> files) {
        if (!(pathsValue instanceof List<?> paths)) {
            return;
        }
        for (Object pathValue : paths) {
            if (pathValue instanceof String path && path.endsWith(".flowtrans.xml")) {
                files.put(path, commit);
            }
        }
    }

    private CommitInfo commitInfo(Map<?, ?> commit) {
        String authorName = null;
        String authorEmail = null;
        Object authorValue = commit.get("author");
        if (authorValue instanceof Map<?, ?> author) {
            authorName = text(author.get("name"));
            authorEmail = text(author.get("email"));
        }
        return new CommitInfo(
                text(commit.get("id")),
                text(commit.get("message")),
                authorName,
                authorEmail,
                parseCommitTime(text(commit.get("timestamp"))));
    }

    private ProjectInfo projectInfo(Object projectValue) {
        if (!(projectValue instanceof Map<?, ?> project)) {
            throw new IllegalArgumentException("GitLab project metadata is missing");
        }
        Object idValue = project.get("id");
        if (!(idValue instanceof Number id)) {
            throw new IllegalArgumentException("GitLab project id is missing");
        }
        return new ProjectInfo(
                id.longValue(),
                text(project.get("name")),
                text(project.get("path_with_namespace")));
    }

    private FlowFieldChangeCaptureMeta meta(ProjectInfo project, String filePath,
                                            String beforeSha, String afterSha,
                                            String eventUuid, CommitInfo commit) {
        String normalizedUuid = hasText(eventUuid) ? eventUuid : null;
        String eventIdentity = normalizedUuid != null
                ? normalizedUuid
                : nullToEmpty(beforeSha) + "|" + nullToEmpty(afterSha);
        String dedupKey = sha256(project.projectId() + "|" + eventIdentity + "|" + filePath);
        return new FlowFieldChangeCaptureMeta(
                dedupKey, normalizedUuid, project.projectId(), project.projectName(),
                "master", filePath, beforeSha, afterSha,
                commit.commitSha(), commit.message(), commit.authorName(),
                commit.authorEmail(), commit.commitTime());
    }

    private LocalDateTime parseCommitTime(String timestamp) {
        if (!hasText(timestamp)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(timestamp).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception invalidTimestamp) {
                return null;
            }
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isAbsentSha(String sha) {
        return !hasText(sha) || sha.chars().allMatch(character -> character == '0');
    }

    private static String safeReason(String reason) {
        return hasText(reason) ? reason : "采集失败";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String text(Object value) {
        return value instanceof String string ? string : null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record ProjectInfo(long projectId, String projectName, String pathWithNamespace) {
    }

    private record CommitInfo(String commitSha, String message, String authorName,
                              String authorEmail, LocalDateTime commitTime) {
    }

    private record SnapshotVersion(String content) {
    }
}
