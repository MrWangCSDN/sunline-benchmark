package com.sunline.dict.controller;

import com.sunline.dict.service.CallRelationScanService;
import com.sunline.dict.service.WebhookService;
import com.sunline.dict.service.pomguard.GitLabWebhookAuthenticator;
import com.sunline.dict.service.pomguard.PomMergeGuardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

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
    private record Fixture(MockMvc mockMvc, PomMergeGuardService guard, WebhookService webhookService, CallRelationScanService relationService) { }
}
