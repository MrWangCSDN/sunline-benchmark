package com.sunline.dict.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.service.flowchange.GitLabApiClient;
import com.sunline.dict.service.pomguard.GitLabMergeRequestService.GitLabMergeRequestAccessException;
import com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestChange;
import com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestCommit;
import com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestRef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitLabMergeRequestServiceImplTest {

    @Test
    void listsOpenedMergeRequestsWithLiteralBranchesAndPagination() {
        FixtureClient client = new FixtureClient(
                ok("[{\"iid\":9,\"source_branch\":\"feature/a\",\"target_branch\":\"master\",\"state\":\"opened\"}]", Map.of("x-next-page", List.of(" 2 "))),
                ok("[{\"iid\":10,\"source_branch\":\"feature/a\",\"target_branch\":\"master\",\"state\":\"opened\"}]", Map.of()));

        List<MergeRequestRef> result = service(client).listOpen(42, "feature/a", "master");

        assertEquals(List.of(new MergeRequestRef(42, 9, "feature/a", "master", "opened"),
                new MergeRequestRef(42, 10, "feature/a", "master", "opened")), result);
        assertEquals(Map.of("state", "opened", "source_branch", "feature/a", "target_branch", "master", "page", "1", "per_page", "2"), client.queries.get(0));
        assertEquals("2", client.queries.get(1).get("page"));
    }

    @Test
    void parsesChangesCommitsAndMutationForms() {
        FixtureClient client = new FixtureClient(
                ok("{\"changes\":[{\"old_path\":\"old/pom.xml\",\"new_path\":\"new/pom.xml\",\"new_file\":false,\"deleted_file\":false,\"renamed_file\":true}]}"),
                ok("[{\"id\":\"a1\",\"message\":\"first\"}]", Map.of("x-next-page", List.of("2"))),
                ok("[{\"id\":\"b2\",\"message\":\"second\"}]"), ok("{}"), ok("{}"));

        assertEquals(List.of(new MergeRequestChange("old/pom.xml", "new/pom.xml", false, false, true)), service(client).changes(42, 7));
        assertEquals(List.of(new MergeRequestCommit("a1", "first"), new MergeRequestCommit("b2", "second")), service(client).commits(42, 7));
        service(client).createNote(42, 7, "body text");
        service(client).close(42, 7);

        assertEquals("/projects/42/merge_requests/7/notes", client.postPaths.get(0));
        assertEquals(Map.of("body", "body text"), client.postForms.get(0));
        assertEquals("/projects/42/merge_requests/7", client.putPaths.get(0));
        assertEquals(Map.of("state_event", "close"), client.putForms.get(0));
    }

    @Test
    void exposesOnlyStableErrorCodeForBadResponsesOrJson() {
        FixtureClient client = new FixtureClient(
                new GitLabApiClient.ApiResponse(GitLabApiClient.Status.PERMANENT_FAILURE, 401, "secret body", Map.of(), "secret body"),
                ok("not-json"));

        GitLabMergeRequestAccessException failed = assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).listOpen(42, "feature/a", "master"));
        GitLabMergeRequestAccessException malformed = assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).changes(42, 7));

        assertEquals("MR_QUERY_FAILED", failed.errorCode());
        assertEquals("MR_CHANGES_FAILED", malformed.errorCode());
        assertEquals("MR_QUERY_FAILED", failed.getMessage());
        assertEquals("MR_CHANGES_FAILED", malformed.getMessage());
    }

    private static GitLabMergeRequestServiceImpl service(FixtureClient client) {
        return new GitLabMergeRequestServiceImpl(client, new ObjectMapper(), 2);
    }

    private static GitLabApiClient.ApiResponse ok(String body) { return ok(body, Map.of()); }
    private static GitLabApiClient.ApiResponse ok(String body, Map<String, List<String>> headers) {
        return new GitLabApiClient.ApiResponse(GitLabApiClient.Status.SUCCESS, 200, body, headers, null);
    }

    private static final class FixtureClient implements GitLabApiClient {
        private final List<ApiResponse> responses;
        private int index;
        private final List<Map<String, String>> queries = new ArrayList<>();
        private final List<String> postPaths = new ArrayList<>();
        private final List<Map<String, String>> postForms = new ArrayList<>();
        private final List<String> putPaths = new ArrayList<>();
        private final List<Map<String, String>> putForms = new ArrayList<>();
        private FixtureClient(ApiResponse... responses) { this.responses = List.of(responses); }
        @Override public ApiResponse get(String path, Map<String, String> query) { queries.add(Map.copyOf(query)); return responses.get(index++); }
        @Override public ApiResponse postForm(String path, Map<String, String> form) { postPaths.add(path); postForms.add(Map.copyOf(form)); return responses.get(index++); }
        @Override public ApiResponse putForm(String path, Map<String, String> form) { putPaths.add(path); putForms.add(Map.copyOf(form)); return responses.get(index++); }
    }
}
