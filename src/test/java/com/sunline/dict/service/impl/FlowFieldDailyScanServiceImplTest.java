package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowView;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunView;
import com.sunline.dict.service.FlowFieldChangeLogService;
import com.sunline.dict.service.FlowFieldChangeLogService.HistoryState;
import com.sunline.dict.service.FlowFieldChangeLogService.WriteDisposition;
import com.sunline.dict.service.FlowFieldChangeLogService.WriteOutcome;
import com.sunline.dict.service.FlowFieldDailyScanService.BatchScanResult;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.Completion;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.ProjectIdentity;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.RunCounters;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.ScanClaim;
import com.sunline.dict.service.flowchange.GitLabApiClient;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.FlowtransFileWorkItem;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabAccessException;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabCommitInfo;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabProjectInfo;
import com.sunline.dict.service.flowchange.GitLabFileVersionService;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.FileVersionResult;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFieldDailyScanServiceImplTest {

    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2026, 8, 18, 22, 0);
    private static final LocalDateTime WINDOW_END = LocalDateTime.of(2026, 8, 19, 22, 0);

    private List<Long> projectIds;
    private HistoryFake history;
    private FileVersionFake files;
    private StateFake state;
    private HistoryWriterFake historyWriter;
    private FlowFieldDailyScanServiceImpl scanner;

    @BeforeEach
    void setUp() {
        projectIds = new ArrayList<>(List.of(42L));
        history = new HistoryFake();
        files = new FileVersionFake();
        state = new StateFake();
        historyWriter = new HistoryWriterFake();
        scanner = new FlowFieldDailyScanServiceImpl(
                () -> List.copyOf(projectIds), history, files, state, historyWriter);
    }

    @Test
    void records_each_commit_separately_in_stable_order_and_uses_claim_window() {
        state.windowStarts.put(42L, WINDOW_START);
        GitLabCommitInfo later = commit(42L, "commit-b", "parent-b",
                OffsetDateTime.parse("2026-08-19T13:30:00+08:00"));
        GitLabCommitInfo earlier = commit(42L, "commit-a", "parent-a",
                OffsetDateTime.parse("2026-08-19T09:30:00+08:00"));
        history.commits.put(42L, List.of(later, earlier));
        history.workItems.put(key(42L, "commit-a"), List.of(modify("src/T001.flowtrans.xml")));
        history.workItems.put(key(42L, "commit-b"), List.of(modify("src/T001.flowtrans.xml")));
        found(42L, "src/T001.flowtrans.xml", "parent-a", xml("T001", "T1"));
        found(42L, "src/T001.flowtrans.xml", "commit-a", xml("T001", "T2"));
        found(42L, "src/T001.flowtrans.xml", "parent-b", xml("T001", "T2"));
        found(42L, "src/T001.flowtrans.xml", "commit-b", xml("T001", "T3"));

        BatchScanResult result = scanner.scanAll(WINDOW_END);

        assertEquals(List.of("commit-a", "commit-b"), historyWriter.recordedCommitShas());
        assertEquals(1, result.attemptedProjects());
        assertEquals(1, result.successfulProjects());
        assertEquals(0, result.errorProjects());
        assertEquals(new CommitRequest(42L, "master",
                        OffsetDateTime.of(WINDOW_START, ZoneOffset.ofHours(8)),
                        OffsetDateTime.of(WINDOW_END, ZoneOffset.ofHours(8))),
                history.commitRequests.get(0));
        assertEquals(new RunCounters(2, 2, 2, 0, 0), state.finished(42L).counters());
    }

    @Test
    void rename_becomes_old_delete_and_new_add_with_distinct_dedup_paths() {
        GitLabCommitInfo commit = commit(42L, "commit-r", "parent-r",
                OffsetDateTime.parse("2026-08-19T10:00:00+08:00"));
        history.commits.put(42L, List.of(commit));
        history.workItems.put(key(42L, "commit-r"), List.of(
                new FlowtransFileWorkItem(FileChangeType.DELETE, "old/T001.flowtrans.xml",
                        "old/T001.flowtrans.xml", null),
                new FlowtransFileWorkItem(FileChangeType.ADD, "new/T001.flowtrans.xml",
                        null, "new/T001.flowtrans.xml")));
        found(42L, "old/T001.flowtrans.xml", "parent-r", emptyXml("T001"));
        found(42L, "new/T001.flowtrans.xml", "commit-r", emptyXml("T001"));

        scanner.scanAll(WINDOW_END);

        assertEquals(List.of("old/T001.flowtrans.xml", "new/T001.flowtrans.xml"),
                historyWriter.recordedPaths());
        assertEquals(List.of(FileChangeType.DELETE, FileChangeType.ADD),
                historyWriter.successChanges.stream().map(FlowFieldChangeSet::fileChangeType).toList());
        assertEquals(2, historyWriter.successMetas.stream()
                .map(FlowFieldChangeMeta::dedupKey).distinct().count());
        assertEquals(List.of(
                        new FetchRequest(42L, "group/project-42", "old/T001.flowtrans.xml", "parent-r"),
                        new FetchRequest(42L, "group/project-42", "new/T001.flowtrans.xml", "commit-r")),
                files.requests);
    }

    @Test
    void root_commit_and_add_modify_delete_use_only_their_required_first_parent_refs() {
        GitLabCommitInfo root = commit(42L, "root", null,
                OffsetDateTime.parse("2026-08-19T08:00:00+08:00"));
        GitLabCommitInfo ordinary = commit(42L, "ordinary", "first-parent",
                OffsetDateTime.parse("2026-08-19T09:00:00+08:00"));
        history.commits.put(42L, List.of(ordinary, root));
        history.workItems.put(key(42L, "root"), List.of(
                new FlowtransFileWorkItem(FileChangeType.ADD, "root-empty.flowtrans.xml",
                        null, "root-empty.flowtrans.xml")));
        history.workItems.put(key(42L, "ordinary"), List.of(
                new FlowtransFileWorkItem(FileChangeType.ADD, "add-empty.flowtrans.xml",
                        null, "add-empty.flowtrans.xml"),
                modify("modify.flowtrans.xml"),
                new FlowtransFileWorkItem(FileChangeType.DELETE, "delete-empty.flowtrans.xml",
                        "delete-empty.flowtrans.xml", null)));
        found(42L, "root-empty.flowtrans.xml", "root", emptyXml("ROOT"));
        found(42L, "add-empty.flowtrans.xml", "ordinary", emptyXml("ADD"));
        found(42L, "modify.flowtrans.xml", "first-parent", xml("MOD", "before"));
        found(42L, "modify.flowtrans.xml", "ordinary", xml("MOD", "after"));
        found(42L, "delete-empty.flowtrans.xml", "first-parent", emptyXml("DELETE"));

        scanner.scanAll(WINDOW_END);

        assertEquals(List.of(
                        new FetchRequest(42L, "group/project-42", "root-empty.flowtrans.xml", "root"),
                        new FetchRequest(42L, "group/project-42", "add-empty.flowtrans.xml", "ordinary"),
                        new FetchRequest(42L, "group/project-42", "modify.flowtrans.xml", "first-parent"),
                        new FetchRequest(42L, "group/project-42", "modify.flowtrans.xml", "ordinary"),
                        new FetchRequest(42L, "group/project-42", "delete-empty.flowtrans.xml", "first-parent")),
                files.requests);
        assertEquals(List.of(FileChangeType.ADD, FileChangeType.ADD,
                        FileChangeType.MODIFY, FileChangeType.DELETE),
                historyWriter.successChanges.stream().map(FlowFieldChangeSet::fileChangeType).toList());
        assertNull(historyWriter.successMetas.get(0).parentSha());
        assertEquals(new RunCounters(2, 4, 4, 0, 0), state.finished(42L).counters());
    }

    @Test
    void success_dedup_skips_download_failed_history_retries_and_noop_modify_is_skipped() {
        GitLabCommitInfo commit = commit(42L, "commit-a", "parent-a",
                OffsetDateTime.parse("2026-08-19T09:30:00+08:00"));
        history.commits.put(42L, List.of(commit));
        history.workItems.put(key(42L, "commit-a"), List.of(
                modify("already-success.flowtrans.xml"),
                modify("retry.flowtrans.xml"),
                modify("noop.flowtrans.xml"),
                new FlowtransFileWorkItem(FileChangeType.ADD, "empty-add.flowtrans.xml",
                        null, "empty-add.flowtrans.xml")));
        historyWriter.states.put(dedup(42L, "commit-a", "already-success.flowtrans.xml"),
                HistoryState.SUCCESS);
        historyWriter.states.put(dedup(42L, "commit-a", "retry.flowtrans.xml"),
                HistoryState.FAILED);
        found(42L, "retry.flowtrans.xml", "parent-a", xml("RETRY", "old"));
        found(42L, "retry.flowtrans.xml", "commit-a", xml("RETRY", "new"));
        found(42L, "noop.flowtrans.xml", "parent-a", xml("NOOP", "same"));
        found(42L, "noop.flowtrans.xml", "commit-a", xml("NOOP", "same"));
        found(42L, "empty-add.flowtrans.xml", "commit-a", emptyXml("EMPTY"));

        scanner.scanAll(WINDOW_END);

        assertFalse(files.requests.stream()
                .anyMatch(request -> request.filePath().equals("already-success.flowtrans.xml")));
        assertEquals(List.of("retry.flowtrans.xml"), historyWriter.upgradedPaths);
        assertEquals(List.of("retry.flowtrans.xml", "empty-add.flowtrans.xml"),
                historyWriter.recordedPaths());
        assertEquals(new RunCounters(1, 4, 2, 0, 2), state.finished(42L).counters());
        assertEquals(Completion.SUCCESS, state.finished(42L).completion());
    }

    @Test
    void deterministic_xml_error_records_failed_history_continues_and_completes_with_errors() {
        GitLabCommitInfo commit = commit(42L, "commit-a", "parent-a",
                OffsetDateTime.parse("2026-08-19T09:30:00+08:00"));
        history.commits.put(42L, List.of(commit));
        history.workItems.put(key(42L, "commit-a"), List.of(
                new FlowtransFileWorkItem(FileChangeType.ADD, "broken.flowtrans.xml",
                        null, "broken.flowtrans.xml"),
                new FlowtransFileWorkItem(FileChangeType.ADD, "good.flowtrans.xml",
                        null, "good.flowtrans.xml")));
        found(42L, "broken.flowtrans.xml", "commit-a",
                "<flowtran><interface><input><field id=\"secret-body\"></flowtran>");
        found(42L, "good.flowtrans.xml", "commit-a", emptyXml("GOOD"));

        BatchScanResult result = scanner.scanAll(WINDOW_END);

        assertEquals(List.of("broken.flowtrans.xml"), historyWriter.failedPaths());
        assertEquals(List.of("good.flowtrans.xml"), historyWriter.recordedPaths());
        assertEquals(List.of("XML snapshot parse failed"), historyWriter.failureErrors);
        assertFalse(historyWriter.failureErrors.get(0).contains("secret-body"));
        assertEquals(new RunCounters(1, 2, 1, 1, 0), state.finished(42L).counters());
        assertEquals(Completion.COMPLETED_WITH_ERRORS, state.finished(42L).completion());
        assertTrue(state.finished(42L).cursorAdvanced());
        assertEquals(0, result.successfulProjects());
        assertEquals(1, result.errorProjects());
    }

    @ParameterizedTest
    @EnumSource(value = Status.class, names = {"NOT_FOUND", "TRANSIENT_FAILURE", "PERMANENT_FAILURE"})
    void every_unavailable_required_gitlab_file_version_fails_project_without_cursor(Status status) {
        GitLabCommitInfo commit = commit(42L, "commit-a", "parent-a",
                OffsetDateTime.parse("2026-08-19T09:30:00+08:00"));
        history.commits.put(42L, List.of(commit));
        history.workItems.put(key(42L, "commit-a"), List.of(
                new FlowtransFileWorkItem(FileChangeType.ADD, "required.flowtrans.xml",
                        null, "required.flowtrans.xml")));
        files.results.put(new FetchRequest(42L, "group/project-42",
                        "required.flowtrans.xml", "commit-a"),
                new FileVersionResult(status, null,
                        "Authorization: Bearer secret API response body"));

        BatchScanResult result = scanner.scanAll(WINDOW_END);

        assertEquals(Completion.FAILED, state.finished(42L).completion());
        assertFalse(state.finished(42L).cursorAdvanced());
        assertEquals(new RunCounters(1, 1, 0, 0, 0), state.finished(42L).counters());
        assertFalse(state.finished(42L).safeError().contains("secret"));
        assertTrue(historyWriter.successMetas.isEmpty());
        assertTrue(historyWriter.failureMetas.isEmpty());
        assertEquals(1, result.errorProjects());
    }

    @ParameterizedTest
    @EnumSource(value = GitLabApiClient.Status.class,
            names = {"NOT_FOUND", "TRANSIENT_FAILURE", "PERMANENT_FAILURE"})
    void project_commit_or_diff_gitlab_failure_is_isolated_and_does_not_advance_cursor(
            GitLabApiClient.Status status) {
        projectIds.add(7L);
        history.projectFailures.put(42L, new GitLabAccessException(status,
                "Authorization: Bearer secret API response body"));
        history.commits.put(7L, List.of());

        BatchScanResult result = scanner.scanAll(WINDOW_END);

        assertEquals(Completion.FAILED, state.finished(42L).completion());
        assertFalse(state.finished(42L).cursorAdvanced());
        assertFalse(state.finished(42L).safeError().contains("secret"));
        assertEquals(Completion.SUCCESS, state.finished(7L).completion());
        assertTrue(state.finished(7L).cursorAdvanced());
        assertEquals(new BatchScanResult(2, 1, 1), result);
    }

    @Test
    void database_write_failure_prevents_only_its_project_cursor_and_counters_stay_independent() {
        projectIds.add(7L);
        GitLabCommitInfo first = commit(42L, "commit-42", "parent-42",
                OffsetDateTime.parse("2026-08-19T09:00:00+08:00"));
        GitLabCommitInfo second = commit(7L, "commit-7", "parent-7",
                OffsetDateTime.parse("2026-08-19T10:00:00+08:00"));
        history.commits.put(42L, List.of(first));
        history.commits.put(7L, List.of(second));
        history.workItems.put(key(42L, "commit-42"), List.of(
                new FlowtransFileWorkItem(FileChangeType.ADD, "p42.flowtrans.xml",
                        null, "p42.flowtrans.xml")));
        history.workItems.put(key(7L, "commit-7"), List.of(
                new FlowtransFileWorkItem(FileChangeType.ADD, "p7.flowtrans.xml",
                        null, "p7.flowtrans.xml")));
        found(42L, "p42.flowtrans.xml", "commit-42", emptyXml("P42"));
        found(7L, "p7.flowtrans.xml", "commit-7", emptyXml("P7"));
        historyWriter.failProjectId = 42L;

        BatchScanResult result = scanner.scanAll(WINDOW_END);

        assertEquals(new BatchScanResult(2, 1, 1), result);
        assertEquals(Completion.FAILED, state.finished(42L).completion());
        assertFalse(state.finished(42L).cursorAdvanced());
        assertEquals(new RunCounters(1, 1, 0, 0, 0), state.finished(42L).counters());
        assertEquals(Completion.SUCCESS, state.finished(7L).completion());
        assertTrue(state.finished(7L).cursorAdvanced());
        assertEquals(new RunCounters(1, 1, 1, 0, 0), state.finished(7L).counters());
        assertEquals(List.of("p7.flowtrans.xml"), historyWriter.recordedPaths());
    }

    @Test
    void commit_change_date_and_time_are_converted_to_asia_shanghai() {
        LocalDateTime laterWindowEnd = LocalDateTime.of(2026, 8, 20, 22, 0);
        GitLabCommitInfo commit = commit(42L, "commit-a", "parent-a",
                OffsetDateTime.parse("2026-08-19T16:30:00Z"));
        history.commits.put(42L, List.of(commit));
        history.workItems.put(key(42L, "commit-a"), List.of(
                new FlowtransFileWorkItem(FileChangeType.ADD, "date.flowtrans.xml",
                        null, "date.flowtrans.xml")));
        found(42L, "date.flowtrans.xml", "commit-a", emptyXml("DATE"));

        scanner.scanAll(laterWindowEnd);

        FlowFieldChangeMeta meta = historyWriter.successMetas.get(0);
        assertEquals(LocalDate.of(2026, 8, 20), meta.changeDate());
        assertEquals(LocalDateTime.of(2026, 8, 20, 0, 30), meta.commitTime());
    }

    private void found(long projectId, String filePath, String ref, String content) {
        files.results.put(new FetchRequest(projectId, "group/project-" + projectId, filePath, ref),
                new FileVersionResult(Status.FOUND, content, null));
    }

    private String dedup(long projectId, String commitSha, String path) {
        return new FlowFieldChangeMeta(1L, LocalDate.of(2026, 8, 19), projectId,
                "project-" + projectId, "group/project-" + projectId, "master", path,
                "parent", commitSha, "message", "author", "author@example.com",
                LocalDateTime.of(2026, 8, 19, 10, 0)).dedupKey();
    }

    private static GitLabCommitInfo commit(long projectId, String sha, String parent,
                                            OffsetDateTime committedAt) {
        return new GitLabCommitInfo(projectId, "project-" + projectId,
                "group/project-" + projectId, sha, parent, "message " + sha,
                "Author", "author@example.com", committedAt);
    }

    private static FlowtransFileWorkItem modify(String path) {
        return new FlowtransFileWorkItem(FileChangeType.MODIFY, path, path, path);
    }

    private static String key(long projectId, String commitSha) {
        return projectId + "|" + commitSha;
    }

    private static String xml(String flowId, String type) {
        return "<flowtran><interface id=\"" + flowId + "\"><input>"
                + "<field id=\"amount\" type=\"" + type + "\"/>"
                + "</input></interface></flowtran>";
    }

    private static String emptyXml(String flowId) {
        return "<flowtran><interface id=\"" + flowId + "\"><input/><output/>"
                + "</interface></flowtran>";
    }

    private record CommitRequest(long projectId, String branch,
                                 OffsetDateTime exclusiveStart, OffsetDateTime inclusiveEnd) {
    }

    private static final class HistoryFake implements GitLabCommitHistoryService {
        private final Map<Long, GitLabProjectInfo> projects = new HashMap<>();
        private final Map<Long, RuntimeException> projectFailures = new HashMap<>();
        private final Map<Long, List<GitLabCommitInfo>> commits = new HashMap<>();
        private final Map<String, List<FlowtransFileWorkItem>> workItems = new HashMap<>();
        private final List<CommitRequest> commitRequests = new ArrayList<>();

        @Override
        public GitLabProjectInfo project(long projectId) {
            RuntimeException failure = projectFailures.get(projectId);
            if (failure != null) {
                throw failure;
            }
            return projects.getOrDefault(projectId, new GitLabProjectInfo(projectId,
                    "project-" + projectId, "group/project-" + projectId));
        }

        @Override
        public List<GitLabCommitInfo> commits(long projectId, String branch,
                                               OffsetDateTime exclusiveStart,
                                               OffsetDateTime inclusiveEnd) {
            commitRequests.add(new CommitRequest(projectId, branch, exclusiveStart, inclusiveEnd));
            return commits.getOrDefault(projectId, List.of());
        }

        @Override
        public List<FlowtransFileWorkItem> changedFlowtransFiles(long projectId, String commitSha) {
            return workItems.getOrDefault(key(projectId, commitSha), List.of());
        }
    }

    private record FetchRequest(long projectId, String projectPath,
                                String filePath, String ref) {
    }

    private static final class FileVersionFake implements GitLabFileVersionService {
        private final Map<FetchRequest, FileVersionResult> results = new LinkedHashMap<>();
        private final List<FetchRequest> requests = new ArrayList<>();

        @Override
        public FileVersionResult fetch(long projectId, String pathWithNamespace,
                                       String filePath, String ref) {
            FetchRequest request = new FetchRequest(projectId, pathWithNamespace, filePath, ref);
            requests.add(request);
            return results.getOrDefault(request, new FileVersionResult(
                    Status.PERMANENT_FAILURE, null, "missing test fixture"));
        }
    }

    private record FinishedProject(ProjectIdentity project, RunCounters counters,
                                   Completion completion, String safeError,
                                   boolean cursorAdvanced) {
    }

    private static final class StateFake implements FlowFieldScanStateService {
        private final Map<Long, LocalDateTime> windowStarts = new HashMap<>();
        private final Map<Long, Long> projectsByRun = new HashMap<>();
        private final Map<Long, FinishedProject> finished = new HashMap<>();

        @Override
        public Optional<ScanClaim> claim(long projectId, String branch,
                                         LocalDateTime windowEnd, LocalDateTime startedAt) {
            projectsByRun.put(projectId, projectId);
            return Optional.of(new ScanClaim(projectId, projectId, branch,
                    windowStarts.getOrDefault(projectId, WINDOW_START), windowEnd));
        }

        @Override
        public void finish(long runId, ProjectIdentity project, RunCounters counters,
                           Completion completion, String safeError, LocalDateTime finishedAt) {
            long projectId = projectsByRun.get(runId);
            finished.put(projectId, new FinishedProject(project, counters, completion, safeError,
                    completion != Completion.FAILED));
        }

        private FinishedProject finished(long projectId) {
            return finished.get(projectId);
        }
    }

    private static final class HistoryWriterFake implements FlowFieldChangeLogService {
        private final Map<String, HistoryState> states = new HashMap<>();
        private final List<FlowFieldChangeMeta> successMetas = new ArrayList<>();
        private final List<FlowFieldChangeSet> successChanges = new ArrayList<>();
        private final List<FlowFieldChangeMeta> failureMetas = new ArrayList<>();
        private final List<String> failureErrors = new ArrayList<>();
        private final List<String> upgradedPaths = new ArrayList<>();
        private Long failProjectId;
        private long nextId = 1L;

        @Override
        public HistoryState state(String dedupKey) {
            return states.getOrDefault(dedupKey, HistoryState.NONE);
        }

        @Override
        public WriteOutcome recordSuccess(FlowFieldChangeMeta meta, FlowFieldChangeSet changeSet) {
            if (Long.valueOf(meta.projectId()).equals(failProjectId)) {
                throw new IllegalStateException("INSERT INTO flow history failed with secret row body");
            }
            HistoryState previous = state(meta.dedupKey());
            if (previous == HistoryState.SUCCESS) {
                return new WriteOutcome(nextId++, WriteDisposition.SKIPPED);
            }
            WriteDisposition disposition = previous == HistoryState.FAILED
                    ? WriteDisposition.UPGRADED : WriteDisposition.INSERTED;
            states.put(meta.dedupKey(), HistoryState.SUCCESS);
            successMetas.add(meta);
            successChanges.add(changeSet);
            if (disposition == WriteDisposition.UPGRADED) {
                upgradedPaths.add(meta.effectiveFilePath());
            }
            return new WriteOutcome(nextId++, disposition);
        }

        @Override
        public WriteOutcome recordFailure(FlowFieldChangeMeta meta, String safeError) {
            if (Long.valueOf(meta.projectId()).equals(failProjectId)) {
                throw new IllegalStateException("INSERT INTO flow history failed with secret row body");
            }
            HistoryState previous = state(meta.dedupKey());
            if (previous == HistoryState.SUCCESS) {
                return new WriteOutcome(nextId++, WriteDisposition.SKIPPED);
            }
            WriteDisposition disposition = previous == HistoryState.FAILED
                    ? WriteDisposition.UPGRADED : WriteDisposition.INSERTED;
            states.put(meta.dedupKey(), HistoryState.FAILED);
            failureMetas.add(meta);
            failureErrors.add(safeError);
            return new WriteOutcome(nextId++, disposition);
        }

        private List<String> recordedCommitShas() {
            return successMetas.stream().map(FlowFieldChangeMeta::commitSha).toList();
        }

        private List<String> recordedPaths() {
            return successMetas.stream().map(FlowFieldChangeMeta::effectiveFilePath).toList();
        }

        private List<String> failedPaths() {
            return failureMetas.stream().map(FlowFieldChangeMeta::effectiveFilePath).toList();
        }

        @Override
        public Page<FieldChangeRowView> pageFieldChanges(FieldChangeQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FlowFieldChangeHistoryDetail getDetail(long logId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<ScanRunView> pageScanRuns(ScanRunQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("removal")
        public boolean existsByDedupKey(String dedupKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("removal")
        public WriteOutcome recordSuccess(FlowFieldChangeCaptureMeta meta,
                                          FlowFieldChangeSet changeSet) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("removal")
        public WriteOutcome recordFailure(FlowFieldChangeCaptureMeta meta, String safeError) {
            throw new UnsupportedOperationException();
        }
    }
}
