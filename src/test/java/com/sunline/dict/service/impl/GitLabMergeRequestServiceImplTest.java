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
                ok("[{\"iid\":9,\"source_branch\":\"feature/a\",\"target_branch\":\"master\",\"state\":\"opened\",\"changes_count\":\"0\"}]", Map.of("x-next-page", List.of(" 2 "))),
                ok("[{\"iid\":10,\"source_branch\":\"feature/a\",\"target_branch\":\"master\",\"state\":\"opened\",\"changes_count\":\"0\"}]", Map.of()));

        List<MergeRequestRef> result = service(client).listOpen(42, "feature/a", "master");

        assertEquals(List.of(new MergeRequestRef(42, 9, "feature/a", "master", "opened", "0"),
                new MergeRequestRef(42, 10, "feature/a", "master", "opened", "0")), result);
        assertEquals(Map.of("state", "opened", "source_branch", "feature/a", "target_branch", "master", "page", "1", "per_page", "2"), client.queries.get(0));
        assertEquals("2", client.queries.get(1).get("page"));
    }

    @Test
    void preservesRawChangesCountFromAuthoritativeMergeRequestRead() {
        FixtureClient client = new FixtureClient(ok("{\"iid\":7,\"source_branch\":\"feature/a\",\"target_branch\":\"master\",\"state\":\"opened\",\"changes_count\":\"2\"}"));

        assertEquals(new MergeRequestRef(42, 7, "feature/a", "master", "opened", "2"), service(client).get(42, 7));
    }

    @Test
    void parsesChangesCommitsAndMutationForms() {
        FixtureClient client = new FixtureClient(
                ok("[{\"old_path\":\"old/pom.xml\",\"new_path\":\"new/pom.xml\",\"new_file\":false,\"deleted_file\":false,\"renamed_file\":true}]"),
                ok("[{\"id\":\"a1\",\"message\":\"first\"}]", Map.of("x-next-page", List.of("2"))),
                ok("[{\"id\":\"b2\",\"message\":\"second\"}]"), ok("{}"), ok("{\"state\":\"closed\"}"));

        assertEquals(List.of(new MergeRequestChange("old/pom.xml", "new/pom.xml", false, false, true)), service(client).changes(42, 7, "1"));
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
                () -> service(client).changes(42, 7, "0"));

        assertEquals("MR_QUERY_FAILED", failed.errorCode());
        assertEquals("MR_CHANGES_FAILED", malformed.errorCode());
        assertEquals("MR_QUERY_FAILED", failed.getMessage());
        assertEquals("MR_CHANGES_FAILED", malformed.getMessage());
    }

    @Test
    void enumeratesMergeRequestDiffFilesAcrossPagesSoPomInLaterPageIsNotMissed() {
        FixtureClient client = new FixtureClient(
                ok("[{\"old_path\":\"README.md\",\"new_path\":\"README.md\",\"new_file\":false,\"deleted_file\":false,\"renamed_file\":false}]", Map.of("x-next-page", List.of("2"))),
                ok("[{\"old_path\":\"module/pom.xml\",\"new_path\":\"module/pom.xml\",\"new_file\":false,\"deleted_file\":false,\"renamed_file\":false}]"));

        List<MergeRequestChange> changes = service(client).changes(42, 7, "2");

        assertEquals(2, changes.size());
        assertEquals("module/pom.xml", changes.get(1).newPath());
        assertEquals(List.of("/projects/42/merge_requests/7/diffs", "/projects/42/merge_requests/7/diffs"), client.getPaths);
        assertEquals("2", client.queries.get(1).get("page"));
    }

    @Test
    void closeRequiresGitLabToConfirmClosedState() {
        FixtureClient client = new FixtureClient(ok("{\"state\":\"opened\"}"));

        GitLabMergeRequestAccessException exception = assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).close(42, 7));

        assertEquals("MR_CLOSE_FAILED", exception.errorCode());
    }

    @Test
    void rejectsRepeatedDiffPageAsAnIncompleteEnumeration() {
        FixtureClient client = new FixtureClient(ok("[]", Map.of("x-next-page", List.of("1"))));

        GitLabMergeRequestAccessException exception = assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).changes(42, 7, "0"));

        assertEquals("MR_CHANGES_FAILED", exception.errorCode());
    }

    @Test
    void rejectsOverflowChangesCountBecauseDiffCompletenessCannotBeProven() {
        FixtureClient client = new FixtureClient(ok("[]"));

        GitLabMergeRequestAccessException exception = assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).changes(42, 7, "1000+"));

        assertEquals("MR_CHANGES_FAILED", exception.errorCode());
    }

    @Test
    void rejectsMissingBlankAndNonDecimalChangesCounts() {
        FixtureClient client = new FixtureClient(ok("[]"), ok("[]"), ok("[]"));

        assertEquals("MR_CHANGES_FAILED", assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).changes(42, 7, null)).errorCode());
        assertEquals("MR_CHANGES_FAILED", assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).changes(42, 7, " ")).errorCode());
        assertEquals("MR_CHANGES_FAILED", assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).changes(42, 7, "2.0")).errorCode());
    }

    @Test
    void rejectsDiffCountMismatchRatherThanReportingNoPomChange() {
        FixtureClient client = new FixtureClient(ok("[{\"old_path\":\"README.md\",\"new_path\":\"README.md\",\"new_file\":false,\"deleted_file\":false,\"renamed_file\":false}]"));

        GitLabMergeRequestAccessException exception = assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).changes(42, 7, "2"));

        assertEquals("MR_CHANGES_FAILED", exception.errorCode());
    }

    @Test
    void acceptsExactZeroChangesCountWithEmptyDiffList() {
        FixtureClient client = new FixtureClient(ok("[]"));

        assertEquals(List.of(), service(client).changes(42, 7, "0"));
    }

    @Test
    void fallsBackToLegacyChangesEndpointWhenDiffsEndpointIsNotFound() {
        FixtureClient client = new FixtureClient(
                response(GitLabApiClient.Status.NOT_FOUND, 404, "{\"message\":\"404 Not Found\"}"),
                ok("{\"changes_count\":\"1\",\"changes\":[{\"old_path\":\"pom.xml\",\"new_path\":\"pom.xml\",\"new_file\":false,\"deleted_file\":false,\"renamed_file\":false}]}"));

        List<MergeRequestChange> changes = service(client).changes(42, 7, "1");

        assertEquals(List.of(new MergeRequestChange("pom.xml", "pom.xml", false, false, false)), changes);
        assertEquals(List.of("/projects/42/merge_requests/7/diffs", "/projects/42/merge_requests/7/changes"), client.getPaths);
        assertEquals(Map.of(), client.queries.get(1));
    }

    @Test
    void fallsBackToLegacyChangesEndpointWhenDiffsResponseIsNotAnArray() {
        FixtureClient client = new FixtureClient(
                ok("{\"message\":\"unsupported endpoint response\"}"),
                ok("{\"changes_count\":\"1\",\"changes\":[{\"old_path\":\"pom.xml\",\"new_path\":\"pom.xml\",\"new_file\":false,\"deleted_file\":false,\"renamed_file\":false}]}"));

        assertEquals(1, service(client).changes(42, 7, "1").size());
        assertEquals(List.of("/projects/42/merge_requests/7/diffs", "/projects/42/merge_requests/7/changes"), client.getPaths);
    }

    @Test
    void doesNotFallBackToLegacyChangesEndpointForAuthorizationOrServerFailures() {
        FixtureClient client = new FixtureClient(response(GitLabApiClient.Status.PERMANENT_FAILURE, 401, "unauthorized"));

        GitLabMergeRequestAccessException exception = assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(client).changes(42, 7, "1"));

        assertEquals("MR_CHANGES_FAILED", exception.errorCode());
        assertEquals(List.of("/projects/42/merge_requests/7/diffs"), client.getPaths);
    }

    @Test
    void rejectsIncompleteOrMalformedLegacyChangesResponse() {
        FixtureClient incomplete = new FixtureClient(
                response(GitLabApiClient.Status.NOT_FOUND, 404, "not found"),
                ok("{\"changes_count\":\"1\",\"changes\":[]}"));
        FixtureClient malformed = new FixtureClient(
                response(GitLabApiClient.Status.NOT_FOUND, 404, "not found"),
                ok("{\"changes_count\":\"1\"}"));

        assertEquals("MR_CHANGES_FAILED", assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(incomplete).changes(42, 7, "1")).errorCode());
        assertEquals("MR_CHANGES_FAILED", assertThrows(GitLabMergeRequestAccessException.class,
                () -> service(malformed).changes(42, 7, "1")).errorCode());
    }

    private static GitLabMergeRequestServiceImpl service(FixtureClient client) {
        return new GitLabMergeRequestServiceImpl(client, new ObjectMapper(), 2);
    }

    private static GitLabApiClient.ApiResponse ok(String body) { return ok(body, Map.of()); }
    private static GitLabApiClient.ApiResponse ok(String body, Map<String, List<String>> headers) {
        return new GitLabApiClient.ApiResponse(GitLabApiClient.Status.SUCCESS, 200, body, headers, null);
    }
    private static GitLabApiClient.ApiResponse response(GitLabApiClient.Status status, int httpStatus, String body) {
        return new GitLabApiClient.ApiResponse(status, httpStatus, body, Map.of(), null);
    }

    private static final class FixtureClient implements GitLabApiClient {
        private final List<ApiResponse> responses;
        private int index;
        private final List<String> getPaths = new ArrayList<>();
        private final List<Map<String, String>> queries = new ArrayList<>();
        private final List<String> postPaths = new ArrayList<>();
        private final List<Map<String, String>> postForms = new ArrayList<>();
        private final List<String> putPaths = new ArrayList<>();
        private final List<Map<String, String>> putForms = new ArrayList<>();
        private FixtureClient(ApiResponse... responses) { this.responses = List.of(responses); }
        @Override public ApiResponse get(String path, Map<String, String> query) { getPaths.add(path); queries.add(Map.copyOf(query)); return responses.get(index++); }
        @Override public ApiResponse postForm(String path, Map<String, String> form) { postPaths.add(path); postForms.add(Map.copyOf(form)); return responses.get(index++); }
        @Override public ApiResponse putForm(String path, Map<String, String> form) { putPaths.add(path); putForms.add(Map.copyOf(form)); return responses.get(index++); }
    }
}
