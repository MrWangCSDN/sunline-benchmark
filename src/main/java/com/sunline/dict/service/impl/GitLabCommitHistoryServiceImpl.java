package com.sunline.dict.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.service.flowchange.GitLabApiClient;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.FlowtransFileWorkItem;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabAccessException;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabCommitInfo;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabProjectInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType.ADD;
import static com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType.DELETE;
import static com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType.MODIFY;

/** GitLab JSON adapter for project metadata, commits, and commit diffs. */
@Service
@ConditionalOnProperty(name = "git.gitlab.url")
public class GitLabCommitHistoryServiceImpl implements GitLabCommitHistoryService {

    private static final String FLOWTRANS_SUFFIX = ".flowtrans.xml";

    private final GitLabApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final int pageSize;

    public GitLabCommitHistoryServiceImpl(GitLabApiClient apiClient, ObjectMapper objectMapper,
                                          @Value("${flow-field-change.scan.page-size:100}") int pageSize) {
        if (pageSize <= 0) {
            throw new IllegalArgumentException("flow-field-change.scan.page-size must be positive");
        }
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.pageSize = pageSize;
    }

    @Override
    public GitLabProjectInfo project(long projectId) {
        JsonNode node = object(apiClient.get("/projects/" + projectId, Map.of()), "GitLab project response is invalid");
        return new GitLabProjectInfo(requiredLong(node, "id", "GitLab project response is invalid"),
                requiredText(node, "name", "GitLab project response is invalid"),
                requiredText(node, "path_with_namespace", "GitLab project response is invalid"));
    }

    @Override
    public List<GitLabCommitInfo> commits(long projectId, String branch,
                                          OffsetDateTime exclusiveStart, OffsetDateTime inclusiveEnd) {
        if (branch == null || branch.isBlank() || exclusiveStart == null || inclusiveEnd == null) {
            throw new IllegalArgumentException("GitLab commit request parameters are invalid");
        }
        GitLabProjectInfo project = project(projectId);
        Map<String, GitLabCommitInfo> commitsBySha = new LinkedHashMap<>();
        String page = "1";
        while (page != null) {
            Map<String, String> query = Map.of("ref_name", branch, "since", exclusiveStart.toString(),
                    "until", inclusiveEnd.toString(), "per_page", String.valueOf(pageSize), "page", page);
            GitLabApiClient.ApiResponse response = apiClient.get("/projects/" + projectId + "/repository/commits", query);
            JsonNode commits = array(response, "GitLab commit response is invalid");
            for (JsonNode commit : commits) {
                GitLabCommitInfo info = commit(project, commit);
                if (info.committedAt().isAfter(exclusiveStart) && !info.committedAt().isAfter(inclusiveEnd)) {
                    commitsBySha.putIfAbsent(info.commitSha(), info);
                }
            }
            page = nextPage(response);
        }
        return commitsBySha.values().stream()
                .sorted(Comparator.comparing(GitLabCommitInfo::committedAt).thenComparing(GitLabCommitInfo::commitSha))
                .toList();
    }

    @Override
    public List<FlowtransFileWorkItem> changedFlowtransFiles(long projectId, String commitSha) {
        if (commitSha == null || commitSha.isBlank()) {
            throw new IllegalArgumentException("GitLab diff request parameters are invalid");
        }
        List<FlowtransFileWorkItem> changes = new ArrayList<>();
        String page = "1";
        while (page != null) {
            GitLabApiClient.ApiResponse response = apiClient.get(
                    "/projects/" + projectId + "/repository/commits/" + commitSha + "/diff",
                    Map.of("per_page", String.valueOf(pageSize), "page", page));
            for (JsonNode diff : array(response, "GitLab diff response is invalid")) {
                appendFlowtransChanges(changes, diff);
            }
            page = nextPage(response);
        }
        return List.copyOf(changes);
    }

    private GitLabCommitInfo commit(GitLabProjectInfo project, JsonNode commit) {
        String invalid = "GitLab commit response is invalid";
        JsonNode parents = commit.path("parent_ids");
        if (!parents.isArray()) {
            throw new IllegalStateException(invalid);
        }
        String parentSha = parents.isEmpty() ? null : requiredText(parents.get(0), invalid);
        return new GitLabCommitInfo(project.projectId(), project.projectName(), project.projectPath(),
                requiredText(commit, "id", invalid), parentSha, requiredText(commit, "message", invalid),
                requiredText(commit, "author_name", invalid), requiredText(commit, "author_email", invalid),
                parseTime(requiredText(commit, "committed_date", invalid), invalid));
    }

    private void appendFlowtransChanges(List<FlowtransFileWorkItem> changes, JsonNode diff) {
        String invalid = "GitLab diff response is invalid";
        String oldPath = requiredText(diff, "old_path", invalid);
        String newPath = requiredText(diff, "new_path", invalid);
        if (requiredBoolean(diff, "renamed_file", invalid)) {
            if (isFlowtrans(oldPath)) {
                changes.add(new FlowtransFileWorkItem(DELETE, oldPath, oldPath, null));
            }
            if (isFlowtrans(newPath)) {
                changes.add(new FlowtransFileWorkItem(ADD, newPath, null, newPath));
            }
        } else if (requiredBoolean(diff, "new_file", invalid)) {
            if (isFlowtrans(newPath)) {
                changes.add(new FlowtransFileWorkItem(ADD, newPath, null, newPath));
            }
        } else if (requiredBoolean(diff, "deleted_file", invalid)) {
            if (isFlowtrans(oldPath)) {
                changes.add(new FlowtransFileWorkItem(DELETE, oldPath, oldPath, null));
            }
        } else if (isFlowtrans(newPath)) {
            changes.add(new FlowtransFileWorkItem(MODIFY, newPath, oldPath, newPath));
        }
    }

    private JsonNode object(GitLabApiClient.ApiResponse response, String invalidMessage) {
        JsonNode node = parse(response, invalidMessage);
        if (!node.isObject()) {
            throw new IllegalStateException(invalidMessage);
        }
        return node;
    }

    private JsonNode array(GitLabApiClient.ApiResponse response, String invalidMessage) {
        JsonNode node = parse(response, invalidMessage);
        if (!node.isArray()) {
            throw new IllegalStateException(invalidMessage);
        }
        return node;
    }

    private JsonNode parse(GitLabApiClient.ApiResponse response, String invalidMessage) {
        if (response == null) {
            throw new GitLabAccessException(GitLabApiClient.Status.TRANSIENT_FAILURE, "GitLab request failed");
        }
        if (response.status() != GitLabApiClient.Status.SUCCESS) {
            throw new GitLabAccessException(response.status(), safeMessage(response.errorMessage()));
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(invalidMessage);
        }
    }

    private static String nextPage(GitLabApiClient.ApiResponse response) {
        List<String> values = response.headers() == null ? null : response.headers().get("x-next-page");
        if (values == null || values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return null;
        }
        return values.get(0).trim();
    }

    private static String requiredText(JsonNode node, String field, String invalidMessage) {
        return requiredText(node.path(field), invalidMessage);
    }

    private static String requiredText(JsonNode node, String invalidMessage) {
        if (node == null || !node.isTextual() || node.textValue().isEmpty()) {
            throw new IllegalStateException(invalidMessage);
        }
        return node.textValue();
    }

    private static long requiredLong(JsonNode node, String field, String invalidMessage) {
        JsonNode value = node.path(field);
        if (!value.canConvertToLong()) {
            throw new IllegalStateException(invalidMessage);
        }
        return value.longValue();
    }

    private static boolean requiredBoolean(JsonNode node, String field, String invalidMessage) {
        JsonNode value = node.path(field);
        if (!value.isBoolean()) {
            throw new IllegalStateException(invalidMessage);
        }
        return value.booleanValue();
    }

    private static OffsetDateTime parseTime(String value, String invalidMessage) {
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(invalidMessage);
        }
    }

    private static boolean isFlowtrans(String path) {
        return path.endsWith(FLOWTRANS_SUFFIX);
    }

    private static String safeMessage(String errorMessage) {
        return errorMessage == null || errorMessage.isBlank() ? "GitLab request failed" : errorMessage;
    }
}
