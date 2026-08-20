package com.sunline.dict.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.sunline.dict.mapper.FlowtranMapper;
import com.sunline.dict.service.FlowFieldDetailService;
import com.sunline.dict.service.FlowXmlParseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookServiceImplSecurityTest {

    private static final String TOKEN = "trusted-private-token";
    private static final String FILE = "src/TC001.flowtrans.xml";
    private static final String XML = "<flowtran id=\"TC001\"/>";
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    private HttpServer trustedServer;
    private HttpServer attackerServer;
    private AtomicReference<ExchangeHandler> trustedHandler;
    private AtomicInteger trustedRequests;
    private AtomicInteger attackerRequests;
    private AtomicReference<String> trustedToken;
    private AtomicReference<String> attackerToken;
    private AtomicReference<String> trustedRequestTarget;

    @BeforeEach
    void startServers() throws IOException {
        trustedRequests = new AtomicInteger();
        attackerRequests = new AtomicInteger();
        trustedToken = new AtomicReference<>();
        attackerToken = new AtomicReference<>();
        trustedRequestTarget = new AtomicReference<>();
        trustedHandler = new AtomicReference<>();

        trustedServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        trustedServer.createContext("/", exchange -> {
            trustedRequests.incrementAndGet();
            trustedToken.set(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            trustedRequestTarget.set(exchange.getRequestURI().toString());
            trustedHandler.get().handle(exchange);
        });
        trustedServer.start();

        attackerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        attackerServer.createContext("/", exchange -> {
            attackerRequests.incrementAndGet();
            attackerToken.set(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            String body = exchange.getRequestURI().getPath().endsWith("/refs")
                    ? "[{\"name\":\"sit\"}]"
                    : XML;
            respond(exchange, 200, body);
        });
        attackerServer.start();
    }

    @AfterEach
    void stopServers() {
        trustedServer.stop(0);
        attackerServer.stop(0);
    }

    @Test
    void forged_payload_origin_never_receives_master_download_or_token() throws Exception {
        trustedHandler.set(exchange -> respond(exchange, 200, XML));
        FlowXmlParseService parser = mock(FlowXmlParseService.class);
        when(parser.parseAndSave(XML, "project:master:" + FILE))
                .thenReturn(Map.of("flowtranCount", 1, "flowStepCount", 0));
        WebhookServiceImpl service = service(parser);

        Map<String, Object> result = service.handleGitLabPushEvent(
                push("refs/heads/master", attackerBaseUrl() + "/group/project", modifiedCommit()));

        assertEquals(0, attackerRequests.get());
        assertEquals(null, attackerToken.get());
        assertEquals(1, trustedRequests.get());
        assertEquals(TOKEN, trustedToken.get());
        assertEquals("/api/v4/projects/group%2Fproject/repository/files/src%2FTC001.flowtrans.xml/raw?ref=master",
                trustedRequestTarget.get());
        verify(parser).parseAndSave(XML, "project:master:" + FILE);
        assertEquals(true, result.get("success"));
    }

    @Test
    void forged_payload_origin_never_receives_uat_validation_or_token() throws Exception {
        trustedHandler.set(exchange -> respond(exchange, 200, "[{\"name\":\"sit\"}]"));
        WebhookServiceImpl service = service(null);
        ReflectionTestUtils.setField(service, "validateUatFromSit", true);
        ReflectionTestUtils.setField(service, "bypassKeywords", "");

        Map<String, Object> result = service.handleGitLabPushEvent(
                push("refs/heads/uat", attackerBaseUrl() + "/group/project", modifiedCommit()));

        assertEquals(0, attackerRequests.get());
        assertEquals(null, attackerToken.get());
        assertEquals(1, trustedRequests.get());
        assertEquals(TOKEN, trustedToken.get());
        assertEquals("/api/v4/projects/group%2Fproject/repository/commits/" + SHA
                        + "/refs?type=branch",
                trustedRequestTarget.get());
        assertEquals(true, result.get("success"));
    }

    @Test
    void trusted_gitlab_redirect_is_not_followed_and_never_forwards_token() throws Exception {
        trustedHandler.set(exchange -> {
            exchange.getResponseHeaders().set("Location", attackerBaseUrl() + "/redirect-target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        FlowXmlParseService parser = mock(FlowXmlParseService.class);
        WebhookServiceImpl service = service(parser);

        service.handleGitLabPushEvent(
                push("refs/heads/master", trustedBaseUrl() + "/group/project", modifiedCommit()));

        assertEquals(1, trustedRequests.get());
        assertEquals(TOKEN, trustedToken.get());
        assertEquals(0, attackerRequests.get());
        assertEquals(null, attackerToken.get());
        verify(parser, never()).parseAndSave(anyString(), anyString());
    }

    @Test
    void failed_gitlab_request_omits_token_full_urls_and_response_body_from_logs_and_result()
            throws Exception {
        String responseBody = "sensitive-error-response-body";
        trustedHandler.set(exchange -> respond(exchange, 401, responseBody));
        WebhookServiceImpl service = service(mock(FlowXmlParseService.class));
        Logger logger = (Logger) LoggerFactory.getLogger(WebhookServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        Map<String, Object> result;
        try {
            result = service.handleGitLabPushEvent(
                    push("refs/heads/master", trustedBaseUrl() + "/group/project", modifiedCommit()));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        String resultText = result.toString();
        for (String secret : List.of(TOKEN, trustedBaseUrl(), attackerBaseUrl(), responseBody)) {
            assertFalse(logs.contains(secret), "logs exposed: " + secret);
            assertFalse(resultText.contains(secret), "result exposed: " + secret);
        }
        assertTrue((Boolean) result.get("success"));
    }

    @Test
    void flow_field_detail_cleanup_failure_logs_fixed_message_without_exception_details()
            throws Exception {
        String sensitiveFailure = "DELETE FROM flow_field_detail token=cleanup-secret "
                + "https://attacker.invalid/db\n\tat example.Cleanup.run(Cleanup.java:19)";
        WebhookServiceImpl service = service(null);
        FlowFieldDetailService fieldDetails = mock(FlowFieldDetailService.class);
        doThrow(new IllegalStateException(sensitiveFailure))
                .when(fieldDetails).deleteBySourceInfo("project:master:" + FILE);
        ReflectionTestUtils.setField(service, "flowFieldDetailService", fieldDetails);
        ReflectionTestUtils.setField(service, "flowtranMapper", mock(FlowtranMapper.class));
        Logger logger = (Logger) LoggerFactory.getLogger(WebhookServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            service.handleGitLabPushEvent(
                    push("refs/heads/master", trustedBaseUrl() + "/group/project", removedCommit()));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        List<ILoggingEvent> cleanupEvents = appender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith("清理 flow_field_detail 失败"))
                .toList();
        assertEquals(1, cleanupEvents.size());
        assertEquals("清理 flow_field_detail 失败（不影响主流程）",
                cleanupEvents.get(0).getFormattedMessage());
        assertEquals(null, cleanupEvents.get(0).getThrowableProxy());
        String logs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        for (String sensitive : List.of("DELETE FROM", "cleanup-secret", "https://", "at example")) {
            assertFalse(logs.contains(sensitive), "logs exposed: " + sensitive);
        }
    }

    private WebhookServiceImpl service(FlowXmlParseService parser) {
        WebhookServiceImpl service = new WebhookServiceImpl();
        if (parser != null) {
            ReflectionTestUtils.setField(service, "flowXmlParseService", parser);
        }
        ReflectionTestUtils.setField(service, "vectorizationEnabled", false);

        // Configure both sides of the RED/GREEN boundary without making the assertions
        // depend on private production structure.
        setIfPresent(service, "gitlabAccessToken", TOKEN);
        setIfPresent(service, "gitLabApiClient",
                new GitLabApiClientImpl(trustedBaseUrl(), TOKEN));
        return service;
    }

    private Map<String, Object> push(String ref, String payloadWebUrl,
                                     Map<String, Object> commit) {
        return Map.of(
                "ref", ref,
                "project", Map.of(
                        "id", 42L,
                        "name", "project",
                        "path_with_namespace", "group/project",
                        "web_url", payloadWebUrl),
                "commits", List.of(commit));
    }

    private Map<String, Object> modifiedCommit() {
        return Map.of(
                "id", SHA,
                "message", "change flowtrans",
                "modified", List.of(FILE),
                "added", List.of(),
                "removed", List.of());
    }

    private Map<String, Object> removedCommit() {
        return Map.of(
                "id", SHA,
                "message", "remove flowtrans",
                "modified", List.of(),
                "added", List.of(),
                "removed", List.of(FILE));
    }

    private String trustedBaseUrl() {
        return "http://127.0.0.1:" + trustedServer.getAddress().getPort();
    }

    private String attackerBaseUrl() {
        return "http://127.0.0.1:" + attackerServer.getAddress().getPort();
    }

    private static void setIfPresent(Object target, String fieldName, Object value) {
        Field field = ReflectionUtils.findField(target.getClass(), fieldName);
        if (field != null) {
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, target, value);
        }
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
