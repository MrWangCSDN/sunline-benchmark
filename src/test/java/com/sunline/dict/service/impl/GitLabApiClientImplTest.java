package com.sunline.dict.service.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sunline.dict.service.flowchange.GitLabApiClient.ApiResponse;
import com.sunline.dict.service.flowchange.GitLabApiClient.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabApiClientImplTest {

    private HttpServer server;
    private HttpServer redirectServer;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServers() {
        server.stop(0);
        if (redirectServer != null) {
            redirectServer.stop(0);
        }
    }

    @Test
    void encodesPathAndQueryAndReturnsPaginationHeaders() {
        AtomicReference<URI> capturedRequest = new AtomicReference<>();
        server.createContext("/api/v4/projects/42/repository/commits", exchange -> {
            capturedRequest.set(exchange.getRequestURI());
            exchange.getResponseHeaders().add("X-Next-Page", "2");
            respond(exchange, 200, "[]");
        });
        GitLabApiClientImpl client = client("secret-token");

        ApiResponse response = client.get("/projects/42/repository/commits",
                Map.of("ref_name", "master", "path", "目录/A B.flowtrans.xml"));

        assertEquals(Status.SUCCESS, response.status());
        assertEquals("2", response.headers().get("x-next-page").get(0));
        assertEquals("/api/v4/projects/42/repository/commits?path=%E7%9B%AE%E5%BD%95%2FA%20B.flowtrans.xml&ref_name=master",
                capturedRequest.get().getRawPath() + "?" + capturedRequest.get().getRawQuery());
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void retries429And5xxAtMostThreeAttemptsThenClassifiesTransientFailure(int responseStatus) {
        AtomicInteger requestCount = new AtomicInteger();
        server.createContext("/api/v4/projects/42", exchange -> {
            requestCount.incrementAndGet();
            respond(exchange, responseStatus, "secret-token must never be exposed");
        });
        GitLabApiClientImpl client = client("secret-token");

        ApiResponse response = client.get("/projects/42", Map.of());

        assertEquals(Status.TRANSIENT_FAILURE, response.status());
        assertEquals(3, requestCount.get());
        assertFalse(response.errorMessage().contains("secret-token"));
    }

    @ParameterizedTest
    @CsvSource({"200,SUCCESS", "404,NOT_FOUND", "401,PERMANENT_FAILURE", "403,PERMANENT_FAILURE", "418,PERMANENT_FAILURE"})
    void classifiesHttpResponses(int httpStatus, Status expectedStatus) {
        server.createContext("/api/v4/projects/42", exchange -> respond(exchange, httpStatus, "response body"));

        ApiResponse response = client("secret-token").get("/projects/42", Map.of());

        assertEquals(expectedStatus, response.status());
        assertEquals(httpStatus, response.httpStatus());
        assertFalse(String.valueOf(response.errorMessage()).contains("response body"));
    }

    @Test
    void doesNotFollowRedirectOrSendTokenToAnotherOrigin() throws IOException {
        AtomicBoolean redirectedRequestReceived = new AtomicBoolean();
        AtomicReference<String> redirectToken = new AtomicReference<>();
        redirectServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirectServer.createContext("/target", exchange -> {
            redirectedRequestReceived.set(true);
            redirectToken.set(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            respond(exchange, 200, "unexpected");
        });
        redirectServer.start();
        server.createContext("/api/v4/projects/42", exchange -> {
            exchange.getResponseHeaders().set("Location", redirectUrl() + "/target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        ApiResponse response = client("secret-token").get("/projects/42", Map.of());

        assertEquals(Status.PERMANENT_FAILURE, response.status());
        assertFalse(redirectedRequestReceived.get());
        assertEquals(null, redirectToken.get());
    }

    @Test
    void sendsPrivateTokenToConfiguredOrigin() {
        AtomicReference<String> token = new AtomicReference<>();
        server.createContext("/api/v4/projects/42", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            respond(exchange, 200, "ok");
        });

        client("secret-token").get("/projects/42", Map.of());

        assertEquals("secret-token", token.get());
    }

    @Test
    void rejectsAbsoluteOrUnrootedApiPathsAndBaseUriQueryOrFragment() {
        GitLabApiClientImpl client = client("secret-token");

        assertThrows(IllegalArgumentException.class, () -> client.get("projects/42", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> client.get("https://untrusted.example/projects/42", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new GitLabApiClientImpl("ftp://example.test",
                "secret-token", HttpClient.newHttpClient(), duration -> { }));
        assertThrows(IllegalArgumentException.class, () -> new GitLabApiClientImpl(baseUrl() + "?query=value",
                "secret-token", HttpClient.newHttpClient(), duration -> { }));
        assertThrows(IllegalArgumentException.class, () -> new GitLabApiClientImpl(baseUrl() + "#fragment",
                "secret-token", HttpClient.newHttpClient(), duration -> { }));
    }

    @Test
    void returnsSanitizedTransientFailureForTimeoutIoAndInterruption() {
        for (Exception exception : List.of(
                new HttpTimeoutException("PRIVATE-TOKEN secret-token"),
                new IOException("PRIVATE-TOKEN secret-token"),
                new InterruptedException("PRIVATE-TOKEN secret-token"))) {
            ApiResponse response = new GitLabApiClientImpl(baseUrl(), "secret-token",
                    new ThrowingHttpClient(exception), duration -> { }).get("/projects/42", Map.of());

            assertEquals(Status.TRANSIENT_FAILURE, response.status());
            assertFalse(response.errorMessage().contains("secret-token"));
        }
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }

    private GitLabApiClientImpl client(String token) {
        return new GitLabApiClientImpl(baseUrl(), token, HttpClient.newHttpClient(), duration -> { });
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String redirectUrl() {
        return "http://127.0.0.1:" + redirectServer.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static final class ThrowingHttpClient extends HttpClient {
        private final Exception exception;

        private ThrowingHttpClient(Exception exception) {
            this.exception = exception;
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.of(Duration.ofSeconds(10)); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { try { return SSLContext.getDefault(); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
                throws IOException, InterruptedException {
            if (exception instanceof IOException ioException) throw ioException;
            if (exception instanceof InterruptedException interruptedException) throw interruptedException;
            throw (HttpTimeoutException) exception;
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) { return CompletableFuture.failedFuture(exception); }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { return CompletableFuture.failedFuture(exception); }
    }
}
