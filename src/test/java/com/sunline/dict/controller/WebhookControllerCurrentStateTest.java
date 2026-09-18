package com.sunline.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sun.net.httpserver.HttpServer;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.FlowStep;
import com.sunline.dict.entity.Flowtran;
import com.sunline.dict.mapper.FlowStepMapper;
import com.sunline.dict.mapper.FlowtranMapper;
import com.sunline.dict.service.CallRelationScanService;
import com.sunline.dict.service.FlowFieldDetailService;
import com.sunline.dict.service.WebhookService;
import com.sunline.dict.service.impl.FlowXmlParseServiceImpl;
import com.sunline.dict.service.impl.GitLabApiClientImpl;
import com.sunline.dict.service.impl.WebhookServiceImpl;
import com.sunline.dict.service.pomguard.GitLabWebhookAuthenticator;
import com.sunline.dict.service.pomguard.PomMergeGuardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.ResponseEntity;
import org.w3c.dom.Element;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookControllerCurrentStateTest {

    private static final String FILE = "src/TC001.flowtrans.xml";
    private static final String SOURCE_INFO = "project:master:" + FILE;
    private static final String XML = """
            <flowtran id="TC001" longname="Transfer" package="demo">
              <input><field id="amount" type="decimal"/></input>
              <output><field id="result" type="string"/></output>
              <flow>
                <service serviceName="accountService" longname="Account"/>
                <method method="post" longname="Post"/>
              </flow>
            </flowtran>
            """;

    @Mock
    private FlowtranMapper flowtranMapper;

    @Mock
    private FlowStepMapper flowStepMapper;

    @Mock
    private FlowFieldDetailService flowFieldDetailService;

    private HttpServer server;
    private WebhookServiceImpl webhookService;

    @BeforeEach
    void setUp() {
        FlowXmlParseServiceImpl parser = new FlowXmlParseServiceImpl();
        ReflectionTestUtils.setField(parser, "flowtranMapper", flowtranMapper);
        ReflectionTestUtils.setField(parser, "flowStepMapper", flowStepMapper);
        ReflectionTestUtils.setField(parser, "flowFieldDetailService", flowFieldDetailService);

        webhookService = new WebhookServiceImpl();
        ReflectionTestUtils.setField(webhookService, "flowXmlParseService", parser);
        ReflectionTestUtils.setField(webhookService, "flowtranMapper", flowtranMapper);
        ReflectionTestUtils.setField(webhookService, "flowStepMapper", flowStepMapper);
        ReflectionTestUtils.setField(webhookService, "flowFieldDetailService", flowFieldDetailService);
        ReflectionTestUtils.setField(webhookService, "vectorizationEnabled", false);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void gitlab_uuid_header_remains_wire_compatible_but_is_not_forwarded_to_the_service()
            throws Exception {
        WebhookService service = mock(WebhookService.class);
        CallRelationScanService relationScanService = mock(CallRelationScanService.class);
        PomMergeGuardService guardService = mock(PomMergeGuardService.class);
        WebhookController controller = new WebhookController();
        ReflectionTestUtils.setField(controller, "webhookService", service);
        ReflectionTestUtils.setField(controller, "callRelationScanService", relationScanService);
        ReflectionTestUtils.setField(controller, "pomMergeGuardService", guardService);
        ReflectionTestUtils.setField(controller, "gitLabWebhookAuthenticator", new GitLabWebhookAuthenticator("secret"));
        Map<String, Object> payload = Map.of("commits", List.of());
        when(service.handleGitLabPushEvent(payload)).thenReturn(Map.of("success", true));
        when(guardService.handlePushHook(payload)).thenReturn(Map.of("eventType", "push"));

        ResponseEntity<Result<Map<String, Object>>> direct =
                controller.handleGitLabWebhook(payload, "Push Hook", "secret", "legacy-event-uuid");
        Result<Map<String, Object>> generic =
                controller.handleGitWebhook(payload, null, "Push Hook", "legacy-event-uuid");

        verify(service, times(2)).handleGitLabPushEvent(payload);
        assertEquals(200, direct.getBody().getCode());
        assertEquals(200, generic.getCode());
        assertNoWebhookHistorySemantics(direct.getBody().getData());
        assertNoWebhookHistorySemantics(generic.getData());
    }

    @Test
    void master_flowtrans_change_parses_transaction_steps_and_fields_without_a_capture_bean()
            throws Exception {
        startFileServer(XML);
        when(flowFieldDetailService.extractAndSave(
                any(Element.class), eq("TC001"), eq(SOURCE_INFO)))
                .thenReturn(Map.of("inputCount", 1, "outputCount", 1));

        Map<String, Object> result = webhookService.handleGitLabPushEvent(
                pushPayload(List.of(FILE), List.of(), List.of()));

        ArgumentCaptor<Flowtran> flowtran = ArgumentCaptor.forClass(Flowtran.class);
        verify(flowtranMapper).insert(flowtran.capture());
        assertEquals("TC001", flowtran.getValue().getId());
        assertEquals(SOURCE_INFO, flowtran.getValue().getFromJar());
        verify(flowStepMapper, times(2)).insert(any(FlowStep.class));
        verify(flowFieldDetailService).extractAndSave(
                any(Element.class), eq("TC001"), eq(SOURCE_INFO));
        assertEquals(1, result.get("flowtranCount"));
        assertEquals(2, result.get("flowStepCount"));
        assertNoWebhookHistorySemantics(result);
    }

    @Test
    void removed_flowtrans_cleans_transaction_steps_and_fields_by_exact_source() throws Exception {
        Flowtran existing = new Flowtran();
        existing.setId("TC001");
        when(flowtranMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(existing));
        when(flowStepMapper.delete(any(QueryWrapper.class))).thenReturn(2);
        when(flowtranMapper.deleteById("TC001")).thenReturn(1);

        Map<String, Object> result = webhookService.handleGitLabPushEvent(
                pushPayload(List.of(), List.of(), List.of(FILE)));

        verify(flowStepMapper).delete(any(QueryWrapper.class));
        verify(flowtranMapper).deleteById("TC001");
        verify(flowFieldDetailService).deleteBySourceInfo(SOURCE_INFO);
        assertEquals(-1, result.get("flowtranCount"));
        assertEquals(-2, result.get("flowStepCount"));
        assertNoWebhookHistorySemantics(result);
    }

    private void startFileServer(String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        ReflectionTestUtils.setField(webhookService, "gitLabApiClient",
                new GitLabApiClientImpl("http://127.0.0.1:" + server.getAddress().getPort(), ""));
    }

    private Map<String, Object> pushPayload(List<String> modified,
                                            List<String> added,
                                            List<String> removed) {
        String projectUrl = server == null
                ? "http://127.0.0.1:1/group/project"
                : "http://127.0.0.1:" + server.getAddress().getPort() + "/group/project";
        return Map.of(
                "ref", "refs/heads/master",
                "project", Map.of(
                        "id", 42L,
                        "name", "project",
                        "path_with_namespace", "group/project",
                        "web_url", projectUrl),
                "commits", List.of(Map.of(
                        "modified", modified,
                        "added", added,
                        "removed", removed)));
    }

    private static void assertNoWebhookHistorySemantics(Map<String, Object> result) {
        assertTrue(result.keySet().stream().noneMatch(key ->
                key.toLowerCase().contains("capture") || key.toLowerCase().contains("uuid")));
        assertFalse(result.containsKey("historyCount"));
    }
}
