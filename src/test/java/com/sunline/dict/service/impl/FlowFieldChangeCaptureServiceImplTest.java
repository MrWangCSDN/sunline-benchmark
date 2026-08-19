package com.sunline.dict.service.impl;

import com.sunline.dict.service.FlowFieldChangeCaptureService;
import com.sunline.dict.service.FlowFieldChangeLogService;
import com.sunline.dict.service.FlowXmlParseService;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureResult;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet;
import com.sunline.dict.service.flowchange.GitLabFileVersionService;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.FileVersionResult;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowFieldChangeCaptureServiceImplTest {

    private static final String FILE = "a/TC045.flowtrans.xml";
    private static final String ZERO_SHA = "0000000000000000000000000000000000000000";

    @Mock
    private GitLabFileVersionService fileService;

    @Mock
    private FlowFieldChangeLogService logService;

    private FlowFieldChangeCaptureServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FlowFieldChangeCaptureServiceImpl(fileService, logService);
    }

    @Test
    void same_file_in_multiple_commits_creates_one_history_with_last_commit_metadata() {
        Map<String, Object> payload = gitLabPush("refs/heads/master", "oldSha", "newSha",
                commit("c1", "first", "First Author", "first@example.com",
                        "2026-08-19T09:00:00+08:00", List.of(FILE), List.of(), List.of()),
                commit("c2", "last", "Last Author", "last@example.com",
                        "2026-08-19T10:15:30+08:00", List.of(), List.of(FILE), List.of()));
        when(fileService.fetch(42L, "group/project", FILE, "oldSha")).thenReturn(found(oldXml()));
        when(fileService.fetch(42L, "group/project", FILE, "newSha")).thenReturn(found(newXml()));

        FlowFieldChangeCaptureResult result = service.capture(payload, "event-1");

        ArgumentCaptor<FlowFieldChangeCaptureMeta> metaCaptor =
                ArgumentCaptor.forClass(FlowFieldChangeCaptureMeta.class);
        ArgumentCaptor<FlowFieldChangeSet> changeCaptor = ArgumentCaptor.forClass(FlowFieldChangeSet.class);
        verify(logService, times(1)).recordSuccess(metaCaptor.capture(), changeCaptor.capture());
        FlowFieldChangeCaptureMeta meta = metaCaptor.getValue();
        assertEquals("7660096842e258635e4efbf52abcbea02077e61e9fce59909d1481ff4727ada1",
                meta.dedupKey());
        assertEquals("event-1", meta.webhookUuid());
        assertEquals(42L, meta.projectId());
        assertEquals("project", meta.projectName());
        assertEquals("master", meta.branch());
        assertEquals(FILE, meta.filePath());
        assertEquals("oldSha", meta.beforeSha());
        assertEquals("newSha", meta.afterSha());
        assertEquals("c2", meta.commitSha());
        assertEquals("last", meta.commitMessage());
        assertEquals("Last Author", meta.commitAuthor());
        assertEquals("last@example.com", meta.commitEmail());
        assertEquals(LocalDateTime.parse("2026-08-19T10:15:30"), meta.commitTime());
        assertEquals(FlowFieldChangeSet.FileChangeType.MODIFY, changeCaptor.getValue().fileChangeType());
        assertEquals(Map.of(FILE, newXml()), result.afterContents());
        assertEquals(1, result.successCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, result.skippedCount());
    }

    @Test
    void non_master_push_does_not_fetch_or_write_history() {
        Map<String, Object> payload = gitLabPush("refs/heads/feature", "oldSha", "newSha",
                commit("c1", "feature", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(), List.of(FILE), List.of()));

        FlowFieldChangeCaptureResult result = service.capture(payload, "event-feature");

        verify(fileService, never()).fetch(anyLong(), anyString(), anyString(), anyString());
        verify(logService, never()).recordSuccess(any(), any());
        verify(logService, never()).recordFailure(any(), anyString());
        assertTrue(result.afterContents().isEmpty());
        assertEquals(0, result.successCount());
    }

    @Test
    void non_flowtrans_files_are_ignored() {
        Map<String, Object> payload = gitLabPush("refs/heads/master", "oldSha", "newSha",
                commit("c1", "java", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of("src/App.java"), List.of("README.md"), List.of()));

        FlowFieldChangeCaptureResult result = service.capture(payload, "event-java");

        verify(fileService, never()).fetch(anyLong(), anyString(), anyString(), anyString());
        verify(logService, never()).recordSuccess(any(), any());
        assertEquals(0, result.successCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, result.skippedCount());
    }

    @Test
    void all_zero_before_sha_is_absent_and_creates_file_add_history() {
        Map<String, Object> payload = gitLabPush("refs/heads/master", ZERO_SHA, "newSha",
                commit("c1", "add", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(FILE), List.of(), List.of()));
        when(fileService.fetch(42L, "group/project", FILE, "newSha")).thenReturn(found(newXml()));

        FlowFieldChangeCaptureResult result = service.capture(payload, null);

        ArgumentCaptor<FlowFieldChangeSet> captor = ArgumentCaptor.forClass(FlowFieldChangeSet.class);
        verify(logService).recordSuccess(any(), captor.capture());
        verify(fileService, times(1)).fetch(42L, "group/project", FILE, "newSha");
        assertEquals(FlowFieldChangeSet.FileChangeType.ADD, captor.getValue().fileChangeType());
        assertEquals(1, result.successCount());
    }

    @Test
    void all_zero_after_sha_is_absent_and_creates_file_delete_history() {
        Map<String, Object> payload = gitLabPush("refs/heads/master", "oldSha", ZERO_SHA,
                commit("c1", "delete", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(), List.of(), List.of(FILE)));
        when(fileService.fetch(42L, "group/project", FILE, "oldSha")).thenReturn(found(oldXml()));

        service.capture(payload, "event-delete");

        ArgumentCaptor<FlowFieldChangeSet> captor = ArgumentCaptor.forClass(FlowFieldChangeSet.class);
        verify(logService).recordSuccess(any(), captor.capture());
        verify(fileService, times(1)).fetch(42L, "group/project", FILE, "oldSha");
        assertEquals(FlowFieldChangeSet.FileChangeType.DELETE, captor.getValue().fileChangeType());
    }

    @Test
    void unchanged_interface_skips_success_history_but_returns_after_content() {
        Map<String, Object> payload = gitLabPush("refs/heads/master", "oldSha", "newSha",
                commit("c1", "implementation only", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(), List.of(FILE), List.of()));
        String sameInterfaceWithOtherBody = oldXml().replace("</flowtran>", "<steps/></flowtran>");
        when(fileService.fetch(42L, "group/project", FILE, "oldSha")).thenReturn(found(oldXml()));
        when(fileService.fetch(42L, "group/project", FILE, "newSha"))
                .thenReturn(found(sameInterfaceWithOtherBody));

        FlowFieldChangeCaptureResult result = service.capture(payload, "event-no-diff");

        verify(logService, never()).recordSuccess(any(), any());
        verify(logService, never()).recordFailure(any(), anyString());
        assertEquals(Map.of(FILE, sameInterfaceWithOtherBody), result.afterContents());
        assertEquals(1, result.skippedCount());
    }

    @Test
    void existing_dedup_key_skips_before_and_after_fetches() {
        Map<String, Object> payload = gitLabPush("refs/heads/master", "oldSha", "newSha",
                commit("c1", "duplicate", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(), List.of(FILE), List.of()));
        when(logService.existsByDedupKey(
                "040e6220b916bcfee6a637ecd4d539cc4c7047c7c0fe3f1ebe6980545a7cadbe"))
                .thenReturn(true);

        FlowFieldChangeCaptureResult result = service.capture(payload, " ");

        verify(fileService, never()).fetch(anyLong(), anyString(), anyString(), anyString());
        verify(logService, never()).recordSuccess(any(), any());
        verify(logService, never()).recordFailure(any(), anyString());
        assertEquals(1, result.skippedCount());
    }

    @Test
    void download_and_xml_failures_record_failure_and_do_not_stop_later_files() {
        String networkFile = "a/Network.flowtrans.xml";
        String malformedFile = "a/Malformed.flowtrans.xml";
        String validFile = "a/Valid.flowtrans.xml";
        Map<String, Object> payload = gitLabPush("refs/heads/master", "oldSha", "newSha",
                commit("c1", "three files", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(), List.of(networkFile, malformedFile, validFile), List.of()));
        when(fileService.fetch(42L, "group/project", networkFile, "oldSha"))
                .thenReturn(new FileVersionResult(Status.TRANSIENT_FAILURE, null, "HTTP 503"));
        when(fileService.fetch(42L, "group/project", malformedFile, "oldSha")).thenReturn(found(oldXml()));
        when(fileService.fetch(42L, "group/project", malformedFile, "newSha"))
                .thenReturn(found("<flowtran>"));
        when(fileService.fetch(42L, "group/project", validFile, "oldSha")).thenReturn(found(oldXml()));
        when(fileService.fetch(42L, "group/project", validFile, "newSha")).thenReturn(found(newXml()));

        FlowFieldChangeCaptureResult result = service.capture(payload, "event-failures");

        verify(logService, times(2)).recordFailure(any(), anyString());
        verify(logService, times(1)).recordSuccess(any(), any());
        assertEquals(1, result.successCount());
        assertEquals(2, result.failedCount());
        assertEquals(newXml(), result.afterContents().get(validFile));
    }

    @Test
    void permanentGitLabFailureRetainsCaptureFailureBehavior() {
        Map<String, Object> payload = gitLabPush("refs/heads/master", "oldSha", "newSha",
                commit("c1", "unauthorized", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(), List.of(FILE), List.of()));
        when(fileService.fetch(42L, "group/project", FILE, "oldSha"))
                .thenReturn(new FileVersionResult(Status.PERMANENT_FAILURE, null, "HTTP 401"));

        FlowFieldChangeCaptureResult result = service.capture(payload, "event-permanent-failure");

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(logService).recordFailure(any(), reasonCaptor.capture());
        assertTrue(reasonCaptor.getValue().contains("HTTP 401"));
        assertEquals(1, result.failedCount());
    }

    @Test
    void missing_both_versions_skips_history() {
        Map<String, Object> payload = gitLabPush("refs/heads/master", "oldSha", "newSha",
                commit("c1", "gone", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(), List.of(FILE), List.of()));
        when(fileService.fetch(42L, "group/project", FILE, "oldSha")).thenReturn(notFound());
        when(fileService.fetch(42L, "group/project", FILE, "newSha")).thenReturn(notFound());

        FlowFieldChangeCaptureResult result = service.capture(payload, "event-gone");

        verify(logService, never()).recordSuccess(any(), any());
        verify(logService, never()).recordFailure(any(), anyString());
        assertEquals(1, result.skippedCount());
    }

    @Test
    void webhook_service_executes_configured_capture_and_reuses_after_content() throws Exception {
        Map<String, Object> payload = gitLabPush("refs/heads/master", "oldSha", "newSha",
                commit("c1", "modify", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(), List.of(FILE), List.of()));
        FlowFieldChangeCaptureService captureService = mock(FlowFieldChangeCaptureService.class);
        FlowXmlParseService flowXmlParseService = mock(FlowXmlParseService.class);
        when(captureService.capture(payload, "event-wired")).thenReturn(
                new FlowFieldChangeCaptureResult(Map.of(FILE, newXml()), 1, 0, 0));
        when(flowXmlParseService.parseAndSave(newXml(), "project:master:" + FILE))
                .thenReturn(Map.of("flowtranCount", 1, "flowStepCount", 2));
        WebhookServiceImpl webhookService = new WebhookServiceImpl();
        ReflectionTestUtils.setField(webhookService, "flowFieldChangeCaptureService", captureService);
        ReflectionTestUtils.setField(webhookService, "flowXmlParseService", flowXmlParseService);
        ReflectionTestUtils.setField(webhookService, "vectorizationEnabled", false);

        Map<String, Object> result = webhookService.handleGitLabPushEvent(payload, "event-wired");

        verify(captureService).capture(payload, "event-wired");
        verify(flowXmlParseService).parseAndSave(newXml(), "project:master:" + FILE);
        assertEquals(true, result.get("success"));
        assertEquals(1, result.get("flowtranCount"));
        assertEquals(2, result.get("flowStepCount"));
    }

    @Test
    void webhook_service_accepts_long_project_id_and_reaches_capture_and_current_processing() throws Exception {
        Map<String, Object> payload = gitLabPush(42L, "refs/heads/master", "oldSha", "newSha",
                commit("c1", "modify", "A", "a@example.com", "2026-08-19T10:00:00Z",
                        List.of(), List.of(FILE), List.of()));
        FlowFieldChangeCaptureService captureService = mock(FlowFieldChangeCaptureService.class);
        FlowXmlParseService flowXmlParseService = mock(FlowXmlParseService.class);
        when(captureService.capture(payload, "event-long-id")).thenReturn(
                new FlowFieldChangeCaptureResult(Map.of(FILE, newXml()), 1, 0, 0));
        when(flowXmlParseService.parseAndSave(newXml(), "project:master:" + FILE))
                .thenReturn(Map.of("flowtranCount", 1, "flowStepCount", 2));
        WebhookServiceImpl webhookService = new WebhookServiceImpl();
        ReflectionTestUtils.setField(webhookService, "flowFieldChangeCaptureService", captureService);
        ReflectionTestUtils.setField(webhookService, "flowXmlParseService", flowXmlParseService);
        ReflectionTestUtils.setField(webhookService, "vectorizationEnabled", false);

        Map<String, Object> result = webhookService.handleGitLabPushEvent(payload, "event-long-id");

        verify(captureService).capture(payload, "event-long-id");
        verify(flowXmlParseService).parseAndSave(newXml(), "project:master:" + FILE);
        assertEquals(true, result.get("success"));
    }

    @SafeVarargs
    private Map<String, Object> gitLabPush(String ref, String before, String after,
                                           Map<String, Object>... commits) {
        return gitLabPush(42, ref, before, after, commits);
    }

    @SafeVarargs
    private Map<String, Object> gitLabPush(Number projectId, String ref, String before, String after,
                                           Map<String, Object>... commits) {
        return Map.of(
                "ref", ref,
                "before", before,
                "after", after,
                "project", Map.of(
                        "id", projectId,
                        "name", "project",
                        "path_with_namespace", "group/project",
                        "web_url", "https://attacker.invalid/group/project"),
                "commits", List.of(commits));
    }

    private Map<String, Object> commit(String id, String message, String authorName,
                                        String authorEmail, String timestamp,
                                        List<String> added, List<String> modified,
                                        List<String> removed) {
        return Map.of(
                "id", id,
                "message", message,
                "timestamp", timestamp,
                "author", Map.of("name", authorName, "email", authorEmail),
                "added", added,
                "modified", modified,
                "removed", removed);
    }

    private FileVersionResult found(String content) {
        return new FileVersionResult(Status.FOUND, content, null);
    }

    private FileVersionResult notFound() {
        return new FileVersionResult(Status.NOT_FOUND, null, null);
    }

    private String oldXml() {
        return """
                <flowtran><interface id="TC045" longname="Transfer">
                  <input><field id="amount" type="OLD"/></input>
                  <output><field id="result" type="TEXT"/></output>
                </interface></flowtran>
                """;
    }

    private String newXml() {
        return """
                <flowtran><interface id="TC045" longname="Transfer">
                  <input><field id="amount" type="NEW" required="true"/></input>
                  <output><field id="result" type="TEXT"/></output>
                </interface></flowtran>
                """;
    }
}
