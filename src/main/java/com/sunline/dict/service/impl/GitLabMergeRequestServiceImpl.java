package com.sunline.dict.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.service.flowchange.GitLabApiClient;
import com.sunline.dict.service.pomguard.GitLabMergeRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** GitLab MR API adapter that exposes only typed, sanitized results. */
@Service
@ConditionalOnProperty(name = "git.gitlab.url")
public class GitLabMergeRequestServiceImpl implements GitLabMergeRequestService {
    private static final Logger log = LoggerFactory.getLogger(GitLabMergeRequestServiceImpl.class);

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
    public MergeRequestRef get(long projectId, long iid) {
        JsonNode item = parse(apiClient.get("/projects/" + projectId + "/merge_requests/" + iid, Map.of()), "MR_QUERY_FAILED");
        if (!item.isObject()) throw failure("MR_QUERY_FAILED");
        return new MergeRequestRef(projectId, longValue(item, "iid", "MR_QUERY_FAILED"),
                text(item, "source_branch", "MR_QUERY_FAILED"), text(item, "target_branch", "MR_QUERY_FAILED"),
                text(item, "state", "MR_QUERY_FAILED"), nullableText(item, "changes_count"));
    }

    @Override
    public List<MergeRequestRef> listOpen(long projectId, String sourceBranch, String targetBranch) {
        List<MergeRequestRef> results = new ArrayList<>();
        forEachPage("/projects/" + projectId + "/merge_requests", "MR_QUERY_FAILED",
                Map.of("state", "opened", "source_branch", required(sourceBranch), "target_branch", required(targetBranch)),
                item -> results.add(new MergeRequestRef(projectId, longValue(item, "iid", "MR_QUERY_FAILED"),
                        text(item, "source_branch", "MR_QUERY_FAILED"), text(item, "target_branch", "MR_QUERY_FAILED"),
                        text(item, "state", "MR_QUERY_FAILED"), nullableText(item, "changes_count"))));
        return List.copyOf(results);
    }

    @Override
    public List<MergeRequestChange> changes(long projectId, long iid, String expectedDiffCount) {
        int expected = expectedDiffCount(expectedDiffCount);
        List<MergeRequestChange> changes = diffChanges(projectId, iid);
        if (changes == null) {
            changes = legacyChanges(projectId, iid);
        }
        if (changes.size() != expected) throw failure("MR_CHANGES_FAILED");
        return List.copyOf(changes);
    }

    private List<MergeRequestChange> diffChanges(long projectId, long iid) {
        List<MergeRequestChange> changes = new ArrayList<>();
        String path = "/projects/" + projectId + "/merge_requests/" + iid + "/diffs";
        String page = "1";
        java.util.Set<String> seenPages = new HashSet<>();
        while (page != null) {
            if (!page.matches("[1-9][0-9]*") || !seenPages.add(page)) throw failure("MR_CHANGES_FAILED");
            Map<String, String> query = Map.of("page", page, "per_page", String.valueOf(pageSize));
            GitLabApiClient.ApiResponse response = apiClient.get(path, query);
            if ("1".equals(page) && response != null && response.status() == GitLabApiClient.Status.NOT_FOUND) {
                log.warn("GitLab MR diffs endpoint unavailable, falling back to legacy changes endpoint: projectId={}, iid={}, httpStatus={}",
                        projectId, iid, response.httpStatus());
                return null;
            }
            JsonNode values = parse(response, "MR_CHANGES_FAILED");
            if (!values.isArray()) {
                if ("1".equals(page)) {
                    log.warn("GitLab MR diffs response is incompatible, falling back to legacy changes endpoint: projectId={}, iid={}, httpStatus={}",
                            projectId, iid, response.httpStatus());
                    return null;
                }
                throw failure("MR_CHANGES_FAILED");
            }
            for (JsonNode item : values) changes.add(change(item));
            page = nextPage(response);
            if (page != null && (!page.matches("[1-9][0-9]*") || seenPages.contains(page))) throw failure("MR_CHANGES_FAILED");
        }
        return changes;
    }

    private List<MergeRequestChange> legacyChanges(long projectId, long iid) {
        String path = "/projects/" + projectId + "/merge_requests/" + iid + "/changes";
        JsonNode root = parse(apiClient.get(path, Map.of()), "MR_CHANGES_FAILED");
        if (!root.isObject()) throw failure("MR_CHANGES_FAILED");
        JsonNode values = root.path("changes");
        if (!values.isArray()) throw failure("MR_CHANGES_FAILED");
        List<MergeRequestChange> changes = new ArrayList<>();
        for (JsonNode item : values) changes.add(change(item));
        return changes;
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
        JsonNode result = parse(apiClient.putForm("/projects/" + projectId + "/merge_requests/" + iid,
                Map.of("state_event", "close")), "MR_CLOSE_FAILED");
        if (!result.isObject() || !"closed".equals(nullableText(result, "state"))) throw failure("MR_CLOSE_FAILED");
    }

    private void forEachPage(String path, String code, Map<String, String> query, java.util.function.Consumer<JsonNode> consumer) {
        String page = "1";
        java.util.Set<String> seenPages = new HashSet<>();
        while (page != null) {
            if (!page.matches("[1-9][0-9]*") || !seenPages.add(page)) throw failure(code);
            Map<String, String> pageQuery = new java.util.LinkedHashMap<>(query);
            pageQuery.put("page", page);
            pageQuery.put("per_page", String.valueOf(pageSize));
            GitLabApiClient.ApiResponse response = apiClient.get(path, Map.copyOf(pageQuery));
            JsonNode values = parse(response, code);
            if (!values.isArray()) throw failure(code);
            for (JsonNode item : values) consumer.accept(item);
            page = nextPage(response);
            if (page != null && (!page.matches("[1-9][0-9]*") || seenPages.contains(page))) throw failure(code);
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
    private static int expectedDiffCount(String value) {
        if (value == null || !value.matches("[0-9]+")) throw failure("MR_CHANGES_FAILED");
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { throw failure("MR_CHANGES_FAILED"); }
    }
    private static MergeRequestChange change(JsonNode item) {
        return new MergeRequestChange(nullableText(item, "old_path"), nullableText(item, "new_path"),
                bool(item, "new_file", "MR_CHANGES_FAILED"), bool(item, "deleted_file", "MR_CHANGES_FAILED"),
                bool(item, "renamed_file", "MR_CHANGES_FAILED"));
    }
    private static String required(String value) { if (value == null || value.isBlank()) throw new IllegalArgumentException("GitLab merge request parameter is invalid"); return value; }
    private static String text(JsonNode node, String field, String code) { String value = nullableText(node, field); if (value == null || value.isEmpty()) throw failure(code); return value; }
    private static String nullableText(JsonNode node, String field) { JsonNode value = node.path(field); return value.isTextual() ? value.textValue() : null; }
    private static long longValue(JsonNode node, String field, String code) { JsonNode value = node.path(field); if (!value.canConvertToLong()) throw failure(code); return value.longValue(); }
    private static boolean bool(JsonNode node, String field, String code) { JsonNode value = node.path(field); if (!value.isBoolean()) throw failure(code); return value.booleanValue(); }
    private static GitLabMergeRequestAccessException failure(String code) { return new GitLabMergeRequestAccessException(code); }
}
