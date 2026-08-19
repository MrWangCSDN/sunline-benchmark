package com.sunline.dict.controller;

import com.sunline.dict.service.CallRelationScanService;
import com.sunline.dict.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookControllerEventUuidTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private CallRelationScanService callRelationScanService;

    @InjectMocks
    private WebhookController controller;

    private Map<String, Object> payload;

    @BeforeEach
    void setUp() throws Exception {
        payload = Map.of("commits", List.of());
        when(webhookService.handleGitLabPushEvent(payload, "event-uuid-1"))
                .thenReturn(Map.of("success", true));
    }

    @Test
    void gitlab_endpoint_forwards_gitlab_event_uuid_to_service() throws Exception {
        controller.handleGitLabWebhook(payload, "Push Hook", "event-uuid-1");

        verify(webhookService).handleGitLabPushEvent(payload, "event-uuid-1");
    }

    @Test
    void generic_git_endpoint_forwards_gitlab_event_uuid_to_service() throws Exception {
        controller.handleGitWebhook(payload, null, "Push Hook", "event-uuid-1");

        verify(webhookService).handleGitLabPushEvent(payload, "event-uuid-1");
    }
}
