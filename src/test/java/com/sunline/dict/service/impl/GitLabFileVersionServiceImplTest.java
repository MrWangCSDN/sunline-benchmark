package com.sunline.dict.service.impl;

import com.sunline.dict.service.flowchange.GitLabApiClient;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.FileVersionResult;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.Status;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GitLabFileVersionServiceImplTest {

    @Test
    void fetchesRawFileThroughTrustedClientWithEncodedRepositoryPath() {
        CapturingClient client = new CapturingClient(new GitLabApiClient.ApiResponse(
                GitLabApiClient.Status.SUCCESS, 200, "<flowtran/>", Map.of(), null));
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(client);

        FileVersionResult result = service.fetch(
                123L, "https://untrusted.example/other-project", "src/a b/TC045.flowtrans.xml", "abc123");

        assertEquals(Status.FOUND, result.status());
        assertEquals("<flowtran/>", result.content());
        assertEquals("/projects/123/repository/files/src%2Fa%20b%2FTC045.flowtrans.xml/raw", client.apiPath);
        assertEquals(Map.of("ref", "abc123"), client.query);
    }

    @Test
    void mapsNotFoundWithoutContentOrError() {
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(new CapturingClient(
                new GitLabApiClient.ApiResponse(GitLabApiClient.Status.NOT_FOUND, 404, null, Map.of(), null)));

        FileVersionResult result = service.fetch(123L, "group/project", "missing.flowtrans.xml", "abc123");

        assertEquals(Status.NOT_FOUND, result.status());
        assertNull(result.content());
        assertNull(result.errorMessage());
    }

    @Test
    void retainsTransientFailureWhenApiClientExhaustsRetries() {
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(new CapturingClient(
                new GitLabApiClient.ApiResponse(GitLabApiClient.Status.TRANSIENT_FAILURE,
                        503, null, Map.of(), "GitLab request failed with HTTP status 503")));

        FileVersionResult result = service.fetch(123L, "group/project", "file.flowtrans.xml", "abc123");

        assertEquals(Status.TRANSIENT_FAILURE, result.status());
        assertNull(result.content());
    }

    @Test
    void retainsPermanentFailureForUnauthorizedApiResponse() {
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(new CapturingClient(
                new GitLabApiClient.ApiResponse(GitLabApiClient.Status.PERMANENT_FAILURE,
                        401, null, Map.of(), "GitLab request failed with HTTP status 401")));

        FileVersionResult result = service.fetch(123L, "group/project", "file.flowtrans.xml", "abc123");

        assertEquals(Status.PERMANENT_FAILURE, result.status());
        assertNull(result.content());
    }

    private static final class CapturingClient implements GitLabApiClient {
        private final ApiResponse response;
        private String apiPath;
        private Map<String, String> query;

        private CapturingClient(ApiResponse response) {
            this.response = response;
        }

        @Override
        public ApiResponse get(String apiPath, Map<String, String> query) {
            this.apiPath = apiPath;
            this.query = query;
            return response;
        }
    }
}
