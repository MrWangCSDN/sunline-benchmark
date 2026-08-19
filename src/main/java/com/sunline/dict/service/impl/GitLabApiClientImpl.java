package com.sunline.dict.service.impl;

import com.sunline.dict.service.flowchange.GitLabApiClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** HTTP client restricted to the configured GitLab API origin. */
@Service
@ConditionalOnProperty(name = "git.gitlab.url")
public class GitLabApiClientImpl implements GitLabApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMillis(200), Duration.ofMillis(400));

    private final URI baseUri;
    private final String accessToken;
    private final HttpClient httpClient;
    private final RetrySleeper retrySleeper;

    @Autowired
    public GitLabApiClientImpl(@Value("${git.gitlab.url}") String gitLabUrl,
                               @Value("${gitlab.access-token:}") String accessToken) {
        this(gitLabUrl, accessToken, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), duration -> Thread.sleep(duration.toMillis()));
    }

    GitLabApiClientImpl(String gitLabUrl, String accessToken, HttpClient httpClient, RetrySleeper retrySleeper) {
        this.baseUri = normalizeBaseUri(gitLabUrl);
        if (httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("GitLab HTTP client must not follow redirects");
        }
        this.accessToken = accessToken;
        this.httpClient = httpClient;
        this.retrySleeper = retrySleeper;
    }

    @Override
    public ApiResponse get(String apiPath, Map<String, String> query) {
        URI requestUri = buildRequestUri(apiPath, query);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
                        .GET()
                        .timeout(REQUEST_TIMEOUT);
                if (accessToken != null && !accessToken.isBlank()) {
                    builder.header("PRIVATE-TOKEN", accessToken);
                }
                HttpResponse<String> response = httpClient.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                ApiResponse result = classify(response);
                if (result.status() != Status.TRANSIENT_FAILURE || attempt == 2) {
                    return result;
                }
            } catch (HttpTimeoutException exception) {
                if (attempt == 2) {
                    return failure("GitLab request timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return failure("GitLab request interrupted");
            } catch (IOException exception) {
                if (attempt == 2) {
                    return failure("GitLab request failed");
                }
            }

            try {
                retrySleeper.sleep(RETRY_DELAYS.get(attempt));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return failure("GitLab request interrupted");
            }
        }
        return failure("GitLab request failed");
    }

    private ApiResponse classify(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        Map<String, List<String>> headers = normalizeHeaders(response.headers().map());
        if (statusCode >= 200 && statusCode < 300) {
            return new ApiResponse(Status.SUCCESS, statusCode, response.body(), headers, null);
        }
        if (statusCode == 404) {
            return new ApiResponse(Status.NOT_FOUND, statusCode, response.body(), headers, null);
        }
        if (statusCode == 429 || statusCode >= 500) {
            return new ApiResponse(Status.TRANSIENT_FAILURE, statusCode, response.body(), headers,
                    "GitLab request failed with HTTP status " + statusCode);
        }
        return new ApiResponse(Status.PERMANENT_FAILURE, statusCode, response.body(), headers,
                "GitLab request failed with HTTP status " + statusCode);
    }

    private ApiResponse failure(String message) {
        return new ApiResponse(Status.TRANSIENT_FAILURE, 0, null, Map.of(), message);
    }

    private URI buildRequestUri(String apiPath, Map<String, String> query) {
        if (apiPath == null || !apiPath.startsWith("/")) {
            throw new IllegalArgumentException("GitLab API path must be rooted");
        }
        URI path = URI.create(apiPath);
        if (path.isAbsolute() || path.getRawAuthority() != null || path.getRawQuery() != null || path.getRawFragment() != null) {
            throw new IllegalArgumentException("GitLab API path must be relative to the configured origin");
        }
        StringBuilder uri = new StringBuilder(baseUri.toString()).append("/api/v4").append(apiPath);
        String encodedQuery = encodeQuery(query);
        if (!encodedQuery.isEmpty()) {
            uri.append('?').append(encodedQuery);
        }
        return URI.create(uri.toString());
    }

    private static String encodeQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        Map<String, String> ordered = new TreeMap<>(query);
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> entry : ordered.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("GitLab query parameters must not be null");
            }
            if (encoded.length() > 0) {
                encoded.append('&');
            }
            encoded.append(percentEncode(entry.getKey())).append('=').append(percentEncode(entry.getValue()));
        }
        return encoded.toString();
    }

    private static Map<String, List<String>> normalizeHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        headers.forEach((name, values) -> normalized.put(name.toLowerCase(Locale.ROOT),
                Collections.unmodifiableList(new ArrayList<>(values))));
        return Collections.unmodifiableMap(normalized);
    }

    private static URI normalizeBaseUri(String gitLabUrl) {
        if (gitLabUrl == null || gitLabUrl.isBlank()) {
            throw new IllegalArgumentException("git.gitlab.url must be an HTTP(S) origin or base path");
        }
        URI uri = URI.create(gitLabUrl.trim());
        if ((!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null)) {
            throw new IllegalArgumentException("git.gitlab.url must be an HTTP(S) origin or base path");
        }
        return URI.create(uri.toString().replaceFirst("/+$", ""));
    }

    private static String percentEncode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte byteValue : value.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = byteValue & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z') || (unsigned >= 'A' && unsigned <= 'Z')
                    || (unsigned >= '0' && unsigned <= '9') || unsigned == '-' || unsigned == '.'
                    || unsigned == '_' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%')
                        .append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(unsigned & 0xf, 16)));
            }
        }
        return encoded.toString();
    }

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
