package com.sunline.dict.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType;
import com.sunline.dict.service.flowchange.GitLabApiClient;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.FlowtransFileWorkItem;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabAccessException;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabCommitInfo;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabProjectInfo;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType.ADD;
import static com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType.DELETE;
import static com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType.MODIFY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitLabCommitHistoryServiceImplTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-08-19T00:00:00Z");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-08-19T22:00:00Z");

    @Test
    void readsRequiredProjectMetadata() {
        FixtureClient client = new FixtureClient();

        GitLabProjectInfo project = service(client).project(42L);

        assertEquals(new GitLabProjectInfo(42L, "Payments", "platform/payments"), project);
        assertEquals("/projects/42", client.requests().get(0).path());
    }

    @Test
    void readsAllCommitPagesFiltersExclusiveStartDeduplicatesAndSortsStably() {
        FixtureClient client = new FixtureClient();

        List<GitLabCommitInfo> commits = service(client).commits(42L, "master", START, END);

        assertEquals(List.of("early-b", "early-c", "late-a"),
                commits.stream().map(GitLabCommitInfo::commitSha).toList());
        assertEquals("first-parent", commits.get(0).parentSha());
        assertEquals(null, commits.get(1).parentSha());
        Request firstCommitRequest = client.requestsFor("/projects/42/repository/commits").get(0);
        assertEquals("master", firstCommitRequest.query().get("ref_name"));
        assertEquals(START.toString(), firstCommitRequest.query().get("since"));
        assertEquals(END.toString(), firstCommitRequest.query().get("until"));
        assertEquals(List.of("1", "2"), client.requestsFor("/projects/42/repository/commits").stream()
                .map(request -> request.query().get("page")).toList());
    }

    @Test
    void mapsFlowtransAddsModificationsDeletesRenamesAndAllDiffPages() {
        FixtureClient client = new FixtureClient();

        assertEquals(List.of(
                new FlowtransFileWorkItem(DELETE, "old/T001.flowtrans.xml", "old/T001.flowtrans.xml", null),
                new FlowtransFileWorkItem(ADD, "new/T001.flowtrans.xml", null, "new/T001.flowtrans.xml")),
                service(client).changedFlowtransFiles(42L, "rename"));
        assertEquals(List.of(
                new FlowtransFileWorkItem(ADD, "add/T002.flowtrans.xml", null, "add/T002.flowtrans.xml"),
                new FlowtransFileWorkItem(MODIFY, "change/T003.flowtrans.xml", "change/T003.flowtrans.xml", "change/T003.flowtrans.xml"),
                new FlowtransFileWorkItem(DELETE, "remove/T004.flowtrans.xml", "remove/T004.flowtrans.xml", null)),
                service(client).changedFlowtransFiles(42L, "flags"));
        assertEquals(List.of("1", "2"), client.requestsFor("/projects/42/repository/commits/flags/diff").stream()
                .map(request -> request.query().get("page")).toList());
    }

    @Test
    void propagatesOnlySanitizedGitLabFailureDetails() {
        FixtureClient client = new FixtureClient();
        client.failProject = true;

        GitLabAccessException exception = assertThrows(GitLabAccessException.class,
                () -> service(client).project(42L));

        assertEquals(GitLabApiClient.Status.PERMANENT_FAILURE, exception.status());
        assertEquals("GitLab request failed with HTTP status 401", exception.safeMessage());
        assertFalse(exception.safeMessage().contains("response-body-secret"));
    }

    private static GitLabCommitHistoryServiceImpl service(FixtureClient client) {
        return new GitLabCommitHistoryServiceImpl(client, new ObjectMapper(), 2);
    }

    private static final class FixtureClient implements GitLabApiClient {
        private final List<Request> requests = new ArrayList<>();
        private boolean failProject;

        @Override
        public ApiResponse get(String path, Map<String, String> query) {
            requests.add(new Request(path, new LinkedHashMap<>(query)));
            if (failProject && path.equals("/projects/42")) {
                return response(Status.PERMANENT_FAILURE, "response-body-secret", Map.of(),
                        "GitLab request failed with HTTP status 401");
            }
            if (path.equals("/projects/42")) {
                return response(Status.SUCCESS, projectJson(), Map.of(), null);
            }
            if (path.equals("/projects/42/repository/commits")) {
                return query.get("page").equals("1")
                        ? response(Status.SUCCESS, commitPageOne(), Map.of("x-next-page", List.of("2")), null)
                        : response(Status.SUCCESS, commitPageTwo(), Map.of("x-next-page", List.of("")), null);
            }
            if (path.equals("/projects/42/repository/commits/rename/diff")) {
                return response(Status.SUCCESS, renameDiff(), Map.of("x-next-page", List.of("")), null);
            }
            if (path.equals("/projects/42/repository/commits/flags/diff")) {
                return query.get("page").equals("1")
                        ? response(Status.SUCCESS, flagsDiffPageOne(), Map.of("x-next-page", List.of("2")), null)
                        : response(Status.SUCCESS, flagsDiffPageTwo(), Map.of("x-next-page", List.of("")), null);
            }
            throw new AssertionError("Unexpected request: " + path + " " + query);
        }

        List<Request> requests() {
            return requests;
        }

        List<Request> requestsFor(String path) {
            return requests.stream().filter(request -> request.path().equals(path)).toList();
        }

        private static ApiResponse response(Status status, String body, Map<String, List<String>> headers, String errorMessage) {
            return new ApiResponse(status, status == Status.SUCCESS ? 200 : 401, body, headers, errorMessage);
        }

        private static String projectJson() {
            return """
                    {"id":42,"description":"Payment service","name":"Payments","name_with_namespace":"Platform / Payments", "path":"payments", "path_with_namespace":"platform/payments", "created_at":"2024-01-01T00:00:00Z", "default_branch":"master", "visibility":"private", "web_url":"https://gitlab.example/platform/payments"}
                    """;
        }

        private static String commitPageOne() {
            return """
                    [{"id":"late-a","short_id":"late-a","created_at":"2026-08-19T22:00:00Z","parent_ids":["late-parent"],"title":"late","message":"late message","author_name":"Ada","author_email":"ada@example.test","authored_date":"2026-08-19T22:00:00Z","committer_name":"Ada","committer_email":"ada@example.test","committed_date":"2026-08-19T22:00:00Z","web_url":"https://gitlab.example/late-a"},
                     {"id":"at-start","short_id":"at-start","created_at":"2026-08-19T00:00:00Z","parent_ids":["older"],"title":"start","message":"start message","author_name":"Ben","author_email":"ben@example.test","authored_date":"2026-08-19T00:00:00Z","committer_name":"Ben","committer_email":"ben@example.test","committed_date":"2026-08-19T00:00:00Z","web_url":"https://gitlab.example/at-start"},
                     {"id":"early-b","short_id":"early-b","created_at":"2026-08-19T10:00:00Z","parent_ids":["first-parent","second-parent"],"title":"merge","message":"merge message","author_name":"Cyd","author_email":"cyd@example.test","authored_date":"2026-08-19T10:00:00Z","committer_name":"Cyd","committer_email":"cyd@example.test","committed_date":"2026-08-19T10:00:00Z","web_url":"https://gitlab.example/early-b"}]
                    """;
        }

        private static String commitPageTwo() {
            return """
                    [{"id":"late-a","short_id":"late-a","created_at":"2026-08-19T22:00:00Z","parent_ids":["late-parent"],"title":"late","message":"late message","author_name":"Ada","author_email":"ada@example.test","authored_date":"2026-08-19T22:00:00Z","committer_name":"Ada","committer_email":"ada@example.test","committed_date":"2026-08-19T22:00:00Z","web_url":"https://gitlab.example/late-a"},
                     {"id":"early-c","short_id":"early-c","created_at":"2026-08-19T10:00:00Z","parent_ids":[],"title":"root","message":"root message","author_name":"Dee","author_email":"dee@example.test","authored_date":"2026-08-19T10:00:00Z","committer_name":"Dee","committer_email":"dee@example.test","committed_date":"2026-08-19T10:00:00Z","web_url":"https://gitlab.example/early-c"},
                     {"id":"after-end","short_id":"after-end","created_at":"2026-08-19T22:00:01Z","parent_ids":["late-a"],"title":"after","message":"after message","author_name":"Eve","author_email":"eve@example.test","authored_date":"2026-08-19T22:00:01Z","committer_name":"Eve","committer_email":"eve@example.test","committed_date":"2026-08-19T22:00:01Z","web_url":"https://gitlab.example/after-end"}]
                    """;
        }

        private static String renameDiff() {
            return """
                    [{"diff":"--- a/old/T001.flowtrans.xml\\n+++ b/new/T001.flowtrans.xml","new_path":"new/T001.flowtrans.xml","old_path":"old/T001.flowtrans.xml","a_mode":"100644","b_mode":"100644","new_file":false,"renamed_file":true,"deleted_file":false},
                     {"diff":"irrelevant","new_path":"new/ignored.FLOWTRANS.XML","old_path":"new/ignored.FLOWTRANS.XML","a_mode":"100644","b_mode":"100644","new_file":false,"renamed_file":false,"deleted_file":false}]
                    """;
        }

        private static String flagsDiffPageOne() {
            return """
                    [{"diff":"new","new_path":"add/T002.flowtrans.xml","old_path":"add/T002.flowtrans.xml","a_mode":"000000","b_mode":"100644","new_file":true,"renamed_file":false,"deleted_file":false},
                     {"diff":"modify","new_path":"change/T003.flowtrans.xml","old_path":"change/T003.flowtrans.xml","a_mode":"100644","b_mode":"100644","new_file":false,"renamed_file":false,"deleted_file":false}]
                    """;
        }

        private static String flagsDiffPageTwo() {
            return """
                    [{"diff":"delete","new_path":"remove/T004.flowtrans.xml","old_path":"remove/T004.flowtrans.xml","a_mode":"100644","b_mode":"000000","new_file":false,"renamed_file":false,"deleted_file":true},
                     {"diff":"irrelevant","new_path":"notes.txt","old_path":"notes.txt","a_mode":"100644","b_mode":"100644","new_file":false,"renamed_file":false,"deleted_file":false}]
                    """;
        }
    }

    private record Request(String path, Map<String, String> query) {
    }
}
