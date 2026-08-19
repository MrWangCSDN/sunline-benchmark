package com.sunline.dict.service.impl;

import com.sunline.dict.service.flowchange.GitLabFileVersionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * GitLab API adapter whose target origin is fixed by application configuration.
 */
@Service
public class GitLabFileVersionServiceImpl implements GitLabFileVersionService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final URI configuredBaseUri;
    private final String accessToken;
    private final HttpClient httpClient;

    public GitLabFileVersionServiceImpl(
            @Value("${git.gitlab.url}") String gitLabUrl,
            @Value("${gitlab.access-token:}") String accessToken) {
        this(gitLabUrl, accessToken, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    GitLabFileVersionServiceImpl(String gitLabUrl, String accessToken, HttpClient httpClient) {
        this.configuredBaseUri = normalizeBaseUri(gitLabUrl);
        this.accessToken = accessToken;
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("GitLab HTTP client must not follow redirects");
        }
        this.httpClient = httpClient;
    }

    @Override
    public FileVersionResult fetch(long projectId, String pathWithNamespace, String filePath, String ref) {
        if (filePath == null || ref == null) {
            return failed("GitLab request parameters are invalid");
        }

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(buildFileUri(projectId, filePath, ref))
                    .GET()
                    .timeout(REQUEST_TIMEOUT);
            if (accessToken != null && !accessToken.isBlank()) {
                requestBuilder.header("PRIVATE-TOKEN", accessToken);
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 200) {
                return new FileVersionResult(Status.FOUND, response.body(), null);
            }
            if (response.statusCode() == 404) {
                return new FileVersionResult(Status.NOT_FOUND, null, null);
            }
            return failed("GitLab request failed with HTTP status " + response.statusCode());
        } catch (HttpTimeoutException exception) {
            return failed("GitLab request timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return failed("GitLab request interrupted");
        } catch (IOException | RuntimeException exception) {
            return failed("GitLab request failed");
        }
    }

    private URI buildFileUri(long projectId, String filePath, String ref) {
        return URI.create(configuredBaseUri + "/api/v4/projects/" + projectId
                + "/repository/files/" + encode(filePath) + "/raw?ref=" + encode(ref));
    }

    private static URI normalizeBaseUri(String gitLabUrl) {
        URI baseUri = URI.create(gitLabUrl.trim());
        if (((!"http".equalsIgnoreCase(baseUri.getScheme()) && !"https".equalsIgnoreCase(baseUri.getScheme()))
                || baseUri.getHost() == null || baseUri.getRawQuery() != null || baseUri.getRawFragment() != null)) {
            throw new IllegalArgumentException("git.gitlab.url must be an HTTP(S) origin or base path");
        }
        String normalized = baseUri.toString().replaceFirst("/+$", "");
        return URI.create(normalized);
    }

    private static String encode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte byteValue : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = byteValue & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z')
                    || (unsigned >= 'A' && unsigned <= 'Z')
                    || (unsigned >= '0' && unsigned <= '9')
                    || unsigned == '-' || unsigned == '.' || unsigned == '_' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned & 0xf, 16)));
            }
        }
        return encoded.toString();
    }

    private static FileVersionResult failed(String errorMessage) {
        return new FileVersionResult(Status.FAILED, null, errorMessage);
    }
}
