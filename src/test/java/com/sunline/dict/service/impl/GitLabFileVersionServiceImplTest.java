package com.sunline.dict.service.impl;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.FileVersionResult;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.time.Duration;
import java.util.Optional;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabFileVersionServiceImplTest {

    private HttpServer server;
    private HttpServer redirectServer;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        if (redirectServer != null) {
            redirectServer.stop(0);
        }
    }

    @Test
    void fetchesEncodedPathAtCommitShaAndSendsPrivateToken() throws Exception {
        AtomicReference<String> requestUri = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        server.createContext("/api/v4/projects/123/repository/files/", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            token.set(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            respond(exchange, 200, "<flowtran/>");
        });
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(
                baseUrl(), "secret-token", HttpClient.newHttpClient());

        FileVersionResult result = service.fetch(
                123L, "group/project", "src/a b/TC045.flowtrans.xml", "abc123");

        assertEquals(Status.FOUND, result.status());
        assertEquals("<flowtran/>", result.content());
        assertEquals("secret-token", token.get());
        assertTrue(requestUri.get().contains("src%2Fa%20b%2FTC045.flowtrans.xml"));
        assertTrue(requestUri.get().endsWith("ref=abc123"));
    }

    @Test
    void returnsNotFoundForMissingFile() {
        server.createContext("/api/v4/projects/123/repository/files/", exchange ->
                respond(exchange, 404, "not found"));
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(
                baseUrl(), "secret-token", HttpClient.newHttpClient());

        FileVersionResult result = service.fetch(123L, "group/project", "missing.flowtrans.xml", "abc123");

        assertEquals(Status.NOT_FOUND, result.status());
        assertEquals(null, result.content());
        assertEquals(null, result.errorMessage());
    }

    @Test
    void encodesRefUsingUtf8() {
        AtomicReference<String> requestUri = new AtomicReference<>();
        server.createContext("/api/v4/projects/123/repository/files/", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            respond(exchange, 200, "<flowtran/>");
        });
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(
                baseUrl(), "secret-token", HttpClient.newHttpClient());

        FileVersionResult result = service.fetch(123L, "group/project", "file.flowtrans.xml", "分支 名");

        assertEquals(Status.FOUND, result.status());
        assertTrue(requestUri.get().endsWith("ref=%E5%88%86%E6%94%AF%20%E5%90%8D"));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 500})
    void returnsSanitizedFailureForUnsuccessfulGitLabResponses(int statusCode) {
        server.createContext("/api/v4/projects/123/repository/files/", exchange ->
                respond(exchange, statusCode, "PRIVATE-TOKEN secret-token"));
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(
                baseUrl(), "secret-token", HttpClient.newHttpClient());

        FileVersionResult result = service.fetch(123L, "group/project", "file.flowtrans.xml", "abc123");

        assertEquals(Status.FAILED, result.status());
        assertEquals(null, result.content());
        assertNotNull(result.errorMessage());
        assertFalse(result.errorMessage().contains("secret-token"));
        assertFalse(result.errorMessage().contains("PRIVATE-TOKEN"));
    }

    @Test
    void alwaysUsesConfiguredHostWhenPayloadNamespaceLooksLikeUrl() {
        AtomicReference<String> requestUri = new AtomicReference<>();
        server.createContext("/api/v4/projects/123/repository/files/", exchange -> {
            requestUri.set(exchange.getRequestURI().toString());
            respond(exchange, 200, "<flowtran/>");
        });
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(
                baseUrl(), "secret-token", HttpClient.newHttpClient());

        FileVersionResult result = service.fetch(
                123L, "https://untrusted.example/other-project", "file.flowtrans.xml", "abc123");

        assertEquals(Status.FOUND, result.status());
        assertNotNull(requestUri.get());
        assertTrue(requestUri.get().startsWith("/api/v4/projects/123/"));
    }

    @Test
    void failsWithoutFollowingRedirectOrSendingTokenToSecondAuthority() throws IOException {
        AtomicBoolean redirectServerReceivedRequest = new AtomicBoolean();
        AtomicReference<String> redirectServerToken = new AtomicReference<>();
        redirectServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirectServer.createContext("/redirect-target", exchange -> {
            redirectServerReceivedRequest.set(true);
            redirectServerToken.set(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            respond(exchange, 200, "<flowtran/>");
        });
        redirectServer.start();
        server.createContext("/api/v4/projects/123/repository/files/", exchange -> {
            exchange.getResponseHeaders().set("Location", redirectBaseUrl() + "/redirect-target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(
                baseUrl(), "secret-token", HttpClient.newHttpClient());

        FileVersionResult result = service.fetch(123L, "group/project", "file.flowtrans.xml", "abc123");

        assertEquals(Status.FAILED, result.status());
        assertFalse(redirectServerReceivedRequest.get());
        assertEquals(null, redirectServerToken.get());
    }

    @Test
    void returnsSanitizedFailureWhenGitLabRequestTimesOut() {
        GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(
                baseUrl(), "secret-token", new TimeoutHttpClient());

        FileVersionResult result = service.fetch(123L, "group/project", "file.flowtrans.xml", "abc123");

        assertEquals(Status.FAILED, result.status());
        assertNotNull(result.errorMessage());
        assertFalse(result.errorMessage().contains("secret-token"));
        assertFalse(result.errorMessage().contains("PRIVATE-TOKEN"));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private String redirectBaseUrl() {
        return "http://127.0.0.1:" + redirectServer.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static final class TimeoutHttpClient extends HttpClient {

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(10));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws HttpTimeoutException {
            throw new HttpTimeoutException("PRIVATE-TOKEN secret-token");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(new HttpTimeoutException("PRIVATE-TOKEN secret-token"));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new HttpTimeoutException("PRIVATE-TOKEN secret-token"));
        }
    }
}
