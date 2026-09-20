package com.sunline.dict.controller;

import com.sunline.dict.service.CallRelationScanService;
import com.sunline.dict.service.WebhookService;
import com.sunline.dict.service.pomguard.GitLabWebhookAuthenticator;
import com.sunline.dict.service.pomguard.PomMergeGuardService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitLabPomWebhookControllerTest {

    @Test
    void logsAuthenticationRejectionWithoutLeakingSuppliedToken() throws Exception {
        String sensitiveToken = "SYNTHETIC_WEBHOOK_TOKEN";
        try (LogCapture logs = captureLogs()) {
            fixture("configured-secret").mockMvc.perform(post("/api/webhook/gitlab")
                    .header("X-Gitlab-Event", "Merge Request Hook")
                    .header("X-Gitlab-Event-UUID", "event-401")
                    .header("X-Gitlab-Token", sensitiveToken)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"));

            assertTrue(logs.text().contains("reason=TOKEN_MISMATCH"));
            assertTrue(logs.text().contains("eventUuid=event-401"));
            assertFalse(logs.text().contains(sensitiveToken));
        }
    }

    @Test
    void logsAuthorizedWebhookResultSummary() throws Exception {
        Fixture fixture = fixture("configured-secret");
        when(fixture.guard.handleMergeRequestHook(any())).thenReturn(Map.of(
                "eventType", "merge_request", "projectId", 70649L, "attempted", 1,
                "closed", 1, "bypassed", 0, "ignored", 0, "errors", 0));

        try (LogCapture logs = captureLogs()) {
            fixture.mockMvc.perform(post("/api/webhook/gitlab")
                    .header("X-Gitlab-Event", "Merge Request Hook")
                    .header("X-Gitlab-Event-UUID", "event-ok")
                    .header("X-Gitlab-Token", "configured-secret")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"object_kind\":\"merge_request\"}"));

            assertTrue(logs.text().contains("GitLab Webhook completed"));
            assertTrue(logs.text().contains("projectId=70649"));
            assertTrue(logs.text().contains("attempted=1, closed=1, bypassed=0, ignored=0, errors=0"));
        }
    }

    @Test
    void logsSanitizedUnexpectedFailureWithoutPayloadOrExceptionMessage() throws Exception {
        Fixture fixture = fixture("configured-secret");
        String sensitivePayload = "SYNTHETIC_PAYLOAD_SECRET";
        String sensitiveMessage = "SYNTHETIC_EXCEPTION_SECRET";
        when(fixture.guard.handleMergeRequestHook(any())).thenThrow(new IllegalStateException(sensitiveMessage));

        try (LogCapture logs = captureLogs()) {
            fixture.mockMvc.perform(post("/api/webhook/gitlab")
                    .header("X-Gitlab-Event", "Merge Request Hook")
                    .header("X-Gitlab-Event-UUID", "event-error")
                    .header("X-Gitlab-Token", "configured-secret")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"object_kind\":\"merge_request\",\"title\":\"" + sensitivePayload + "\"}"));

            assertTrue(logs.text().contains("GitLab Webhook failed"));
            assertTrue(logs.text().contains("eventUuid=event-error"));
            assertTrue(logs.text().contains("exceptionType=IllegalStateException"));
            assertFalse(logs.text().contains(sensitivePayload));
            assertFalse(logs.text().contains(sensitiveMessage));
        }
    }

    @Test
    void rejectsMissingTokenBeforeAnyBusinessService() throws Exception {
        Fixture fixture = fixture("configured-secret");
        fixture.mockMvc.perform(post("/api/webhook/gitlab").header("X-Gitlab-Event", "Merge Request Hook")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("WEBHOOK_UNAUTHORIZED"));
        verifyNoInteractions(fixture.guard, fixture.webhookService, fixture.relationService);
    }

    @Test
    void rejectsBlankServerSecretWith503BeforeAnyBusinessService() throws Exception {
        Fixture fixture = fixture(" ");
        fixture.mockMvc.perform(post("/api/webhook/gitlab").header("X-Gitlab-Token", "anything")
                        .header("X-Gitlab-Event", "Push Hook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("WEBHOOK_SECRET_MISSING"));
        verifyNoInteractions(fixture.guard, fixture.webhookService, fixture.relationService);
    }

    @Test
    void routesAuthorizedMrOnlyToGuardAndPushToGuardAndLegacyHandlers() throws Exception {
        Fixture fixture = fixture("configured-secret");
        when(fixture.guard.handleMergeRequestHook(any())).thenReturn(Map.of("eventType", "merge_request"));
        when(fixture.guard.handlePushHook(any())).thenReturn(Map.of("eventType", "push"));
        when(fixture.webhookService.handleGitLabPushEvent(any())).thenReturn(Map.of("success", true));
        String mr = "{\"object_kind\":\"merge_request\"}";
        String push = "{\"object_kind\":\"push\",\"commits\":[]}";

        fixture.mockMvc.perform(post("/api/webhook/gitlab").header("X-Gitlab-Token", "configured-secret")
                        .header("X-Gitlab-Event", "Merge Request Hook").contentType(MediaType.APPLICATION_JSON).content(mr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.eventType").value("merge_request"));
        fixture.mockMvc.perform(post("/api/webhook/gitlab").header("X-Gitlab-Token", "configured-secret")
                        .header("X-Gitlab-Event", "Push Hook").contentType(MediaType.APPLICATION_JSON).content(push))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.eventType").value("push"));
        verify(fixture.guard).handleMergeRequestHook(any());
        verify(fixture.guard).handlePushHook(any());
        verify(fixture.webhookService).handleGitLabPushEvent(any());
    }

    @Test
    void genericGitRouteDoesNotInvokePomGuard() throws Exception {
        Fixture fixture = fixture("configured-secret");
        when(fixture.webhookService.handleGitLabPushEvent(any())).thenReturn(Map.of("success", true));
        fixture.mockMvc.perform(post("/api/webhook/git").header("X-Gitlab-Event", "Push Hook")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"commits\":[]}"))
                .andExpect(status().isOk());
        verify(fixture.webhookService).handleGitLabPushEvent(any());
        verify(fixture.guard, never()).handlePushHook(any());
        verify(fixture.guard, never()).handleMergeRequestHook(any());
    }

    @Test
    void genericGitRouteIgnoresNonPushGitLabEventsWithoutLegacyProcessingOrGuard() throws Exception {
        Fixture fixture = fixture("configured-secret");

        fixture.mockMvc.perform(post("/api/webhook/git").header("X-Gitlab-Event", "Tag Push Hook")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ref\":\"refs/tags/v1\",\"commits\":[{}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value("非push事件，已忽略"));

        verifyNoInteractions(fixture.webhookService, fixture.relationService);
        verify(fixture.guard, never()).handlePushHook(any());
        verify(fixture.guard, never()).handleMergeRequestHook(any());
    }

    private static Fixture fixture(String secret) {
        WebhookController controller = new WebhookController();
        PomMergeGuardService guard = mock(PomMergeGuardService.class);
        WebhookService webhook = mock(WebhookService.class);
        CallRelationScanService relations = mock(CallRelationScanService.class);
        ReflectionTestUtils.setField(controller, "pomMergeGuardService", guard);
        ReflectionTestUtils.setField(controller, "gitLabWebhookAuthenticator", new GitLabWebhookAuthenticator(secret));
        ReflectionTestUtils.setField(controller, "webhookService", webhook);
        ReflectionTestUtils.setField(controller, "callRelationScanService", relations);
        return new Fixture(MockMvcBuilders.standaloneSetup(controller).build(), guard, webhook, relations);
    }

    private static LogCapture captureLogs() {
        return new LogCapture((Logger) LoggerFactory.getLogger(WebhookController.class));
    }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private LogCapture(Logger logger) {
            this.logger = logger;
            appender.start();
            logger.addAppender(appender);
        }

        private String text() {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private record Fixture(MockMvc mockMvc, PomMergeGuardService guard, WebhookService webhookService, CallRelationScanService relationService) { }
}
