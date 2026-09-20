package com.sunline.dict.service.impl;

import com.sunline.dict.service.flowchange.ConfiguredGitLabProjectProvider;
import com.sunline.dict.service.pomguard.GitLabMergeRequestService;
import com.sunline.dict.service.pomguard.PomChangePolicy;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestChange;
import static com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestCommit;
import static com.sunline.dict.service.pomguard.GitLabMergeRequestService.MergeRequestRef;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomMergeGuardServiceImplTest {

    @Test
    void logsExplicitMergeRequestIgnoreReasonsWithoutSensitiveContent() {
        String sensitiveSource = "SYNTHETIC_SOURCE_BRANCH_SECRET";
        String sensitiveTitle = "SYNTHETIC_TITLE_SECRET";
        String sensitiveDescription = "SYNTHETIC_DESCRIPTION_SECRET";
        FixtureMrService noPom = new FixtureMrService();
        noPom.changes = List.of(new MergeRequestChange("README.md", "README.md", false, false, false));

        try (LogCapture logs = captureLogs()) {
            guard(new FixtureMrService()).handleMergeRequestHook(mrPayload(99, 7, "open", "opened", "master"));
            guard(new FixtureMrService()).handleMergeRequestHook(mrPayload(42, 7, "open", "opened", "main"));
            guard(new FixtureMrService()).handleMergeRequestHook(mrPayload(42, 7, "open", "closed", "master"));
            guard(new FixtureMrService()).handleMergeRequestHook(mrPayload(42, 7, "approved", "opened", "master"));
            guard(new FixtureMrService()).handleMergeRequestHook(Map.of(
                    "object_kind", "merge_request", "project", Map.of("id", 42),
                    "object_attributes", Map.of("action", "open", "state", "opened", "source_branch", sensitiveSource,
                            "target_branch", "master", "title", sensitiveTitle, "description", sensitiveDescription)));
            guard(noPom).handleMergeRequestHook(mrPayload(42, 7, "update", "opened", "master"));

            String text = logs.text();
            assertTrue(text.contains("reason=PROJECT_NOT_ALLOWED"));
            assertTrue(text.contains("reason=TARGET_BRANCH_MISMATCH"));
            assertTrue(text.contains("reason=STATE_NOT_OPENED"));
            assertTrue(text.contains("reason=ACTION_NOT_SUPPORTED"));
            assertTrue(text.contains("reason=IID_MISSING"));
            assertTrue(text.contains("reason=NO_POM_CHANGE"));
            assertFalse(text.contains(sensitiveSource));
            assertFalse(text.contains(sensitiveTitle));
            assertFalse(text.contains(sensitiveDescription));
            assertFalse(text.contains("README.md"));
        }
    }

    @Test
    void logsFinalDecisionsWithoutCommitMessageOrPomPath() {
        String sensitiveMessage = "SYNTHETIC_COMMIT_MESSAGE_SECRET";
        String sensitivePath = "sensitive/module/pom.xml";
        FixtureMrService closed = pomFixture();
        closed.changes = List.of(new MergeRequestChange(sensitivePath, sensitivePath, false, false, false));
        FixtureMrService bypassed = pomFixture();
        bypassed.changes = closed.changes;
        bypassed.commits = List.of(new MergeRequestCommit("c3", sensitiveMessage + " merge pom file go"));
        FixtureMrService failed = pomFixture();
        failed.changes = closed.changes;
        failed.failClose = true;

        try (LogCapture logs = captureLogs()) {
            guard(closed).handleMergeRequestHook(mrPayload(42, 7, "open", "opened", "master"));
            guard(bypassed).handleMergeRequestHook(mrPayload(42, 8, "open", "opened", "master"));
            guard(failed).handleMergeRequestHook(mrPayload(42, 9, "open", "opened", "master"));

            String text = logs.text();
            assertTrue(text.contains("outcome=CLOSED"));
            assertTrue(text.contains("outcome=BYPASSED"));
            assertTrue(text.contains("outcome=ERROR"));
            assertTrue(text.contains("pomPathCount=1"));
            assertTrue(text.contains("errorCode=MR_CLOSE_FAILED"));
            assertFalse(text.contains(sensitiveMessage));
            assertFalse(text.contains(sensitivePath));
        }
    }

    @Test
    void logsSafePushReceiptAndCompletionWithoutMessageOrPath() {
        String sensitiveMessage = "SYNTHETIC_PUSH_MESSAGE_SECRET";
        String sensitivePath = "sensitive/pom.xml";
        try (LogCapture logs = captureLogs()) {
            guard(pomFixture()).handlePushHook(pushPayload("refs/heads/master", sensitiveMessage, List.of(sensitivePath)));

            String text = logs.text();
            assertTrue(text.contains("POM guard Push received"));
            assertTrue(text.contains("targetsProtectedBranch=true"));
            assertTrue(text.contains("POM guard Push completed"));
            assertFalse(text.contains(sensitiveMessage));
            assertFalse(text.contains(sensitivePath));
        }
    }

    @Test
    void ignoresProjectsOutsideAllowListWithoutGitLabCalls() {
        FixtureMrService mr = new FixtureMrService();
        Map<String, Object> result = guard(mr).handleMergeRequestHook(mrPayload(99, 7, "open", "opened", "master"));
        assertEquals(1, result.get("ignored"));
        assertEquals(0, mr.calls);
    }

    @Test
    void ignoresNonTargetMergeRequestEvents() {
        FixtureMrService mr = new FixtureMrService();
        PomMergeGuardServiceImpl guard = guard(mr);
        assertEquals(1, guard.handleMergeRequestHook(mrPayload(42, 7, "merge", "opened", "master")).get("ignored"));
        assertEquals(1, guard.handleMergeRequestHook(mrPayload(42, 7, "open", "closed", "master")).get("ignored"));
        assertEquals(1, guard.handleMergeRequestHook(mrPayload(42, 7, "open", "opened", "main")).get("ignored"));
        assertEquals(0, mr.calls);
    }

    @Test
    void ignoresMrWithoutPomWithoutQueryingCommits() {
        FixtureMrService mr = new FixtureMrService();
        mr.changes = List.of(new MergeRequestChange("README.md", "README.md", false, false, false));
        Map<String, Object> result = guard(mr).handleMergeRequestHook(mrPayload(42, 7, "update", "opened", "master"));
        assertEquals(1, result.get("ignored"));
        assertEquals(0, mr.commitCalls);
    }

    @Test
    void titleAndDescriptionCannotBypassButCommitCanBypass() {
        FixtureMrService mr = pomFixture();
        Map<String, Object> closed = guard(mr).handleMergeRequestHook(mrPayload(42, 7, "open", "opened", "master"));
        assertEquals(1, closed.get("closed"));
        assertEquals(1, mr.notes.size());
        FixtureMrService bypassed = pomFixture();
        bypassed.commits = List.of(new MergeRequestCommit("c3", "build: merge pom file go"));
        Map<String, Object> bypass = guard(bypassed).handleMergeRequestHook(mrPayload(42, 7, "open", "opened", "master"));
        assertEquals(1, bypass.get("bypassed"));
        assertEquals(0, bypassed.notes.size());
        assertEquals(0, bypassed.closeCalls);
    }

    @Test
    void closesAfterNoteAndKeepsCloseTruthfulWhenWritesFail() {
        FixtureMrService ok = pomFixture();
        Map<String, Object> result = guard(ok).handleMergeRequestHook(mrPayload(42, 7, "reopen", "opened", "master"));
        assertEquals("CLOSED", decision(result).get("outcome"));
        assertEquals(true, decision(result).get("noteCreated"));
        assertEquals(true, decision(result).get("closed"));
        assertEquals("禁止提交 pom 文件。本合并请求包含 pom.xml，已自动关闭。\n如确需提交，请在 commit message 中加入：merge pom file go\n\n命中的文件：\n- module/pom.xml", ok.notes.get(0));

        FixtureMrService noteFails = pomFixture(); noteFails.failNote = true;
        assertEquals("CLOSED", decision(guard(noteFails).handleMergeRequestHook(mrPayload(42, 7, "open", "opened", "master"))).get("outcome"));
        FixtureMrService closeFails = pomFixture(); closeFails.failClose = true;
        Map<String, Object> closeError = guard(closeFails).handleMergeRequestHook(mrPayload(42, 7, "open", "opened", "master"));
        assertEquals("ERROR", decision(closeError).get("outcome"));
        assertEquals(false, decision(closeError).get("closed"));
    }

    @Test
    void sourceBranchPushProcessesUniqueIidsInAscendingOrderAndMasterOnlyWarns() {
        FixtureMrService source = pomFixture();
        source.open = List.of(new MergeRequestRef(42, 9, "feature/a", "master", "opened", "1"), new MergeRequestRef(42, 7, "feature/a", "master", "opened", "1"), new MergeRequestRef(42, 9, "feature/a", "master", "opened", "1"));
        Map<String, Object> sourceResult = guard(source).handlePushHook(pushPayload("refs/heads/feature/a", "ordinary", List.of("module/pom.xml")));
        assertEquals(List.of(7L, 9L), source.changedIids);
        assertEquals(2, sourceResult.get("closed"));

        FixtureMrService direct = pomFixture();
        Map<String, Object> warned = guard(direct).handlePushHook(pushPayload("refs/heads/master", "ordinary", List.of("pom.xml")));
        assertEquals("WARNED_DIRECT_PUSH", decision(warned).get("outcome"));
        assertEquals(0, direct.closeCalls);
        assertEquals("BYPASSED", decision(guard(direct).handlePushHook(pushPayload("refs/heads/master", "merge pom file go", List.of("pom.xml")))).get("outcome"));
        assertEquals(1, guard(direct).handlePushHook(Map.of("after", "0000000000000000000000000000000000000000", "ref", "refs/heads/feature/a", "project", Map.of("id", 42))).get("ignored"));
    }

    @Test
    void currentClosedOrRetargetedMrIsSkippedBeforeAnyReadOfChangesOrWrite() {
        FixtureMrService mr = pomFixture();
        mr.current = new MergeRequestRef(42, 7, "feature/a", "develop", "closed", "1");

        Map<String, Object> result = guard(mr).handleMergeRequestHook(mrPayload(42, 7, "update", "opened", "master"));

        assertEquals(1, result.get("ignored"));
        assertEquals(0, mr.changeCalls);
        assertEquals(0, mr.notes.size());
        assertEquals(0, mr.closeCalls);
    }

    @Test
    void escapesPathSoItCannotCreateGitLabQuickActionLine() {
        FixtureMrService mr = pomFixture();
        mr.changes = List.of(new MergeRequestChange("evil\n/target_branch develop\n/pom.xml", "evil\n/target_branch develop\n/pom.xml", false, false, false));

        guard(mr).handleMergeRequestHook(mrPayload(42, 7, "open", "opened", "master"));

        assertEquals(false, mr.notes.get(0).contains("\n/target_branch develop\n"));
        assertEquals(false, mr.notes.get(0).contains("\r"));
    }

    @Test
    void sourceBranchQueryFailureLogsOnlyStableCodeAndProjectId() {
        FixtureMrService mr = pomFixture();
        mr.failList = true;
        String sensitive = "REVIEW_SYNTHETIC_SOURCE_BRANCH_SECRET";
        Logger logger = (Logger) LoggerFactory.getLogger(PomMergeGuardServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            guard(mr).handlePushHook(pushPayload("refs/heads/" + sensitive, "ordinary", List.of()));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
        assertEquals(true, logs.contains("projectId=42"));
        assertEquals(true, logs.contains("MR_QUERY_FAILED"));
        assertEquals(false, logs.contains(sensitive));
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> decision(Map<String, Object> result) { return (Map<String, Object>) ((List<?>) result.get("decisions")).get(0); }
    private static LogCapture captureLogs() { return new LogCapture((Logger) LoggerFactory.getLogger(PomMergeGuardServiceImpl.class)); }
    private static PomMergeGuardServiceImpl guard(FixtureMrService service) { return new PomMergeGuardServiceImpl(service, new PomChangePolicy("merge pom file go"), () -> List.of(42L), true, "master", 20); }
    private static FixtureMrService pomFixture() { FixtureMrService service = new FixtureMrService(); service.changes = List.of(new MergeRequestChange("module/pom.xml", "module/pom.xml", false, false, false)); service.commits = List.of(new MergeRequestCommit("a1", "ordinary")); return service; }
    private static Map<String, Object> mrPayload(long project, long iid, String action, String state, String target) { return Map.of("object_kind", "merge_request", "project", Map.of("id", project), "object_attributes", Map.of("iid", iid, "action", action, "state", state, "source_branch", "feature/a", "target_branch", target, "title", "merge pom file go", "description", "merge pom file go")); }
    private static Map<String, Object> pushPayload(String ref, String message, List<String> modified) { return Map.of("object_kind", "push", "ref", ref, "project", Map.of("id", 42), "commits", List.of(Map.of("id", "abcdef123", "message", message, "added", List.of(), "modified", modified, "removed", List.of()))); }

    private static final class LogCapture implements AutoCloseable {
        private final Logger logger;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        private LogCapture(Logger logger) { this.logger = logger; appender.start(); logger.addAppender(appender); }
        private String text() { return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right); }
        @Override public void close() { logger.detachAppender(appender); appender.stop(); }
    }

    private static final class FixtureMrService implements GitLabMergeRequestService {
        List<MergeRequestRef> open = List.of(); List<MergeRequestChange> changes = List.of(); List<MergeRequestCommit> commits = List.of();
        MergeRequestRef current = new MergeRequestRef(42, 7, "feature/a", "master", "opened", "1");
        List<String> notes = new ArrayList<>(); List<Long> changedIids = new ArrayList<>(); int calls; int changeCalls; int commitCalls; int closeCalls; boolean failNote; boolean failClose; boolean failList;
        @Override public MergeRequestRef get(long projectId, long iid) { calls++; return current; }
        @Override public List<MergeRequestRef> listOpen(long projectId, String sourceBranch, String targetBranch) { calls++; if (failList) throw new GitLabMergeRequestAccessException("MR_QUERY_FAILED"); return open; }
        @Override public List<MergeRequestChange> changes(long projectId, long iid, String expectedDiffCount) { calls++; changeCalls++; changedIids.add(iid); return changes; }
        @Override public List<MergeRequestCommit> commits(long projectId, long iid) { calls++; commitCalls++; return commits; }
        @Override public void createNote(long projectId, long iid, String body) { calls++; if (failNote) throw new GitLabMergeRequestAccessException("MR_NOTE_FAILED"); notes.add(body); }
        @Override public void close(long projectId, long iid) { calls++; closeCalls++; if (failClose) throw new GitLabMergeRequestAccessException("MR_CLOSE_FAILED"); }
    }
}
