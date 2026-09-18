package com.sunline.dict.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.service.flowchange.GitLabApiClient;
import com.sunline.dict.service.pomguard.GitLabMergeRequestService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GitLab MR API adapter that exposes only typed, sanitized results. */
@Service
@ConditionalOnProperty(name = "git.gitlab.url")
public class GitLabMergeRequestServiceImpl implements GitLabMergeRequestService {
    private final GitLabApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final int pageSize;

    public GitLabMergeRequestServiceImpl(GitLabApiClient apiClient, ObjectMapper objectMapper,
                                         @Value("${gitlab.pom-guard.page-size:100}") int pageSize) {
        if (pageSize <= 0) throw new IllegalArgumentException("gitlab.pom-guard.page-size must be positive");
        this.apiClient = apiClient;
        this.objectMapper = objectMapper;
        this.pageSize = pageSize;
    }

    @Override
    public List<MergeRequestRef> listOpen(long projectId, String sourceBranch, String targetBranch) {
        List<MergeRequestRef> results = new ArrayList<>();
        forEachPage("/projects/" + projectId + "/merge_requests", "MR_QUERY_FAILED",
                Map.of("state", "opened", "source_branch", required(sourceBranch), "target_branch", required(targetBranch)),
                item -> results.add(new MergeRequestRef(projectId, longValue(item, "iid", "MR_QUERY_FAILED"),
                        text(item, "source_branch", "MR_QUERY_FAILED"), text(item, "target_branch", "MR_QUERY_FAILED"),
                        text(item, "state", "MR_QUERY_FAILED"))));
        return List.copyOf(results);
    }

    @Override
    public List<MergeRequestChange> changes(long projectId, long iid) {
        JsonNode root = parse(apiClient.get("/projects/" + projectId + "/merge_requests/" + iid + "/changes", Map.of()), "MR_CHANGES_FAILED");
        JsonNode values = root.path("changes");
        if (!values.isArray()) throw failure("MR_CHANGES_FAILED");
        List<MergeRequestChange> changes = new ArrayList<>();
        for (JsonNode item : values) changes.add(new MergeRequestChange(nullableText(item, "old_path"), nullableText(item, "new_path"),
                bool(item, "new_file", "MR_CHANGES_FAILED"), bool(item, "deleted_file", "MR_CHANGES_FAILED"), bool(item, "renamed_file", "MR_CHANGES_FAILED")));
        return List.copyOf(changes);
    }

    @Override
    public List<MergeRequestCommit> commits(long projectId, long iid) {
        List<MergeRequestCommit> commits = new ArrayList<>();
        forEachPage("/projects/" + projectId + "/merge_requests/" + iid + "/commits", "MR_COMMITS_FAILED", Map.of(),
                item -> commits.add(new MergeRequestCommit(text(item, "id", "MR_COMMITS_FAILED"), text(item, "message", "MR_COMMITS_FAILED"))));
        return List.copyOf(commits);
    }

    @Override
    public void createNote(long projectId, long iid, String body) {
        requireSuccess(apiClient.postForm("/projects/" + projectId + "/merge_requests/" + iid + "/notes", Map.of("body", required(body))), "MR_NOTE_FAILED");
    }

    @Override
    public void close(long projectId, long iid) {
        requireSuccess(apiClient.putForm("/projects/" + projectId + "/merge_requests/" + iid, Map.of("state_event", "close")), "MR_CLOSE_FAILED");
    }

    private void forEachPage(String path, String code, Map<String, String> query, java.util.function.Consumer<JsonNode> consumer) {
        String page = "1";
        while (page != null) {
            Map<String, String> pageQuery = new java.util.LinkedHashMap<>(query);
            pageQuery.put("page", page);
            pageQuery.put("per_page", String.valueOf(pageSize));
            GitLabApiClient.ApiResponse response = apiClient.get(path, Map.copyOf(pageQuery));
            JsonNode values = parse(response, code);
            if (!values.isArray()) throw failure(code);
            for (JsonNode item : values) consumer.accept(item);
            page = nextPage(response);
        }
    }

    private JsonNode parse(GitLabApiClient.ApiResponse response, String code) {
        requireSuccess(response, code);
        try { return objectMapper.readTree(response.body()); }
        catch (Exception ignored) { throw failure(code); }
    }

    private static void requireSuccess(GitLabApiClient.ApiResponse response, String code) {
        if (response == null || response.status() != GitLabApiClient.Status.SUCCESS) throw failure(code);
    }
    private static String nextPage(GitLabApiClient.ApiResponse response) {
        List<String> pages = response.headers() == null ? null : response.headers().get("x-next-page");
        if (pages == null || pages.isEmpty() || pages.get(0) == null || pages.get(0).isBlank()) return null;
        return pages.get(0).trim();
    }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("GitLab merge request parameter is invalid"); return value; }
    private static String text(JsonNode node, String field, String code) { String value = nullableText(node, field); if (value == null || value.isEmpty()) throw failure(code); return value; }
    private static String nullableText(JsonNode node, String field) { JsonNode value = node.path(field); return value.isTextual() ? value.textValue() : null; }
    private static long longValue(JsonNode node, String field, String code) { JsonNode value = node.path(field); if (!value.canConvertToLong()) throw failure(code); return value.longValue(); }
    private static boolean bool(JsonNode node, String field, String code) { JsonNode value = node.path(field); if (!value.isBoolean()) throw failure(code); return value.booleanValue(); }
    private static GitLabMergeRequestAccessException failure(String code) { return new GitLabMergeRequestAccessException(code); }
}
