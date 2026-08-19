package com.sunline.dict.service.impl;

import com.sunline.dict.entity.FlowFieldScanCursor;
import com.sunline.dict.entity.FlowFieldScanRun;
import com.sunline.dict.mapper.FlowFieldScanCursorMapper;
import com.sunline.dict.mapper.FlowFieldScanRunMapper;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.Completion;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.ProjectIdentity;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.RunCounters;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.ScanClaim;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowFieldScanStateServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 19, 22, 0);

    private RunMapperFake runMapper;
    private CursorMapperFake cursorMapper;
    private FlowFieldScanStateService service;

    @BeforeEach
    void setUp() {
        runMapper = new RunMapperFake();
        cursorMapper = new CursorMapperFake();
        service = new FlowFieldScanStateServiceImpl(runMapper.mapper(), cursorMapper.mapper());
    }

    @Test
    void first_claim_starts_at_midnight_and_duplicate_window_has_no_execution_right() {
        ScanClaim first = service.claim(42L, "master", NOW, NOW).orElseThrow();

        assertEquals(LocalDateTime.of(2026, 8, 19, 0, 0), first.windowStart());
        assertTrue(service.claim(42L, "master", first.windowEnd(), NOW).isEmpty());
        assertEquals("RUNNING", runMapper.saved(first.runId()).getStatus());
    }

    @Test
    void later_claim_starts_at_cursor_and_each_project_branch_cursor_is_isolated() {
        ScanClaim first = service.claim(42L, "master", NOW, NOW).orElseThrow();
        service.finish(first.runId(), new ProjectIdentity("payments", "core/payments"),
                new RunCounters(1, 2, 3, 0, 0), Completion.SUCCESS, null, NOW.plusMinutes(3));

        LocalDateTime nextEnd = NOW.plusDays(1);
        ScanClaim next = service.claim(42L, "master", nextEnd, nextEnd).orElseThrow();
        ScanClaim otherProject = service.claim(7L, "master", nextEnd, nextEnd).orElseThrow();
        ScanClaim otherBranch = service.claim(42L, "release", nextEnd, nextEnd).orElseThrow();

        assertEquals(NOW, next.windowStart());
        assertEquals(nextEnd.toLocalDate().atStartOfDay(), otherProject.windowStart());
        assertEquals(nextEnd.toLocalDate().atStartOfDay(), otherBranch.windowStart());
    }

    @Test
    void stale_running_rows_are_failed_only_for_older_windows_of_the_same_project_branch() {
        LocalDateTime olderEnd = NOW.minusDays(1);
        ScanClaim stale = service.claim(42L, "master", olderEnd, olderEnd).orElseThrow();
        ScanClaim otherProject = service.claim(7L, "master", olderEnd, olderEnd).orElseThrow();
        ScanClaim otherBranch = service.claim(42L, "release", olderEnd, olderEnd).orElseThrow();

        ScanClaim current = service.claim(42L, "master", NOW, NOW).orElseThrow();

        assertEquals("FAILED", runMapper.saved(stale.runId()).getStatus());
        assertEquals(NOW, runMapper.saved(stale.runId()).getFinishedAt());
        assertEquals("RUNNING", runMapper.saved(current.runId()).getStatus());
        assertEquals("RUNNING", runMapper.saved(otherProject.runId()).getStatus());
        assertEquals("RUNNING", runMapper.saved(otherBranch.runId()).getStatus());
    }

    @Test
    void failed_run_keeps_cursor_while_completed_with_errors_advances_it_and_persists_details() {
        ScanClaim failed = service.claim(42L, "master", NOW.minusDays(1), NOW.minusDays(1)).orElseThrow();
        ProjectIdentity project = new ProjectIdentity("payments", "core/payments");
        RunCounters counters = new RunCounters(11, 8, 6, 1, 2);
        service.finish(failed.runId(), project, counters, Completion.FAILED,
                "GitLab request timed out", NOW.minusDays(1).plusMinutes(4));

        assertNull(cursorMapper.select(42L, "master"));
        FlowFieldScanRun failedRow = runMapper.saved(failed.runId());
        assertEquals("FAILED", failedRow.getStatus());
        assertEquals("payments", failedRow.getProjectName());
        assertEquals("core/payments", failedRow.getProjectPath());
        assertEquals(11, failedRow.getCommitCount());
        assertEquals(8, failedRow.getChangedFileCount());
        assertEquals(6, failedRow.getHistoryCount());
        assertEquals(1, failedRow.getFailedFileCount());
        assertEquals(2, failedRow.getSkippedCount());
        assertFalse(failedRow.getCursorAdvanced());
        assertEquals("GitLab request timed out", failedRow.getErrorMessage());

        LocalDateTime nextEnd = NOW;
        ScanClaim next = service.claim(42L, "master", nextEnd, NOW).orElseThrow();
        service.finish(next.runId(), project, counters, Completion.COMPLETED_WITH_ERRORS,
                "1 file failed", NOW.plusMinutes(5));

        FlowFieldScanCursor cursor = cursorMapper.select(42L, "master");
        assertEquals(nextEnd, cursor.getLastSuccessEnd());
        assertEquals(next.runId(), cursor.getLastRunId());
        assertEquals("payments", cursor.getProjectName());
        assertEquals("core/payments", cursor.getProjectPath());
        assertTrue(runMapper.saved(next.runId()).getCursorAdvanced());
        assertEquals("COMPLETED_WITH_ERRORS", runMapper.saved(next.runId()).getStatus());
    }

    @Test
    void finish_sanitizes_sensitive_errors_and_truncates_plain_errors() {
        ScanClaim sensitive = service.claim(42L, "master", NOW.minusHours(2), NOW.minusHours(2)).orElseThrow();
        service.finish(sensitive.runId(), new ProjectIdentity("payments", "core/payments"),
                new RunCounters(0, 0, 0, 0, 0), Completion.FAILED,
                "download failed with Authorization: Bearer secret-token", NOW.minusHours(1));

        assertEquals("扫描失败（敏感信息已隐藏）", runMapper.saved(sensitive.runId()).getErrorMessage());
        assertFalse(runMapper.saved(sensitive.runId()).getErrorMessage().contains("secret-token"));

        ScanClaim longError = service.claim(42L, "master", NOW, NOW).orElseThrow();
        service.finish(longError.runId(), new ProjectIdentity("payments", "core/payments"),
                new RunCounters(0, 0, 0, 0, 0), Completion.FAILED,
                "bad\r\u0000" + "x".repeat(3000), NOW.plusMinutes(1));

        String saved = runMapper.saved(longError.runId()).getErrorMessage();
        assertEquals(2000, saved.length());
        assertFalse(saved.contains("\r"));
        assertFalse(saved.contains("\u0000"));
    }

    @Test
    void cursor_failure_aborts_finish_without_leaving_run_and_cursor_disagreeing() throws Exception {
        ScanClaim claim = service.claim(42L, "master", NOW, NOW).orElseThrow();
        cursorMapper.failUpsert = true;

        assertThrows(IllegalStateException.class, () -> service.finish(
                claim.runId(), new ProjectIdentity("payments", "core/payments"),
                new RunCounters(1, 1, 1, 0, 0), Completion.SUCCESS,
                null, NOW.plusMinutes(2)));

        assertEquals("RUNNING", runMapper.saved(claim.runId()).getStatus());
        assertNull(cursorMapper.select(42L, "master"));
        Method finish = FlowFieldScanStateServiceImpl.class.getMethod("finish", long.class,
                ProjectIdentity.class, RunCounters.class, Completion.class,
                String.class, LocalDateTime.class);
        Transactional transactional = finish.getAnnotation(Transactional.class);
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }

    private static final class RunMapperFake implements InvocationHandler {
        private final Map<Long, FlowFieldScanRun> rows = new LinkedHashMap<>();
        private long nextId = 1;

        FlowFieldScanRunMapper mapper() {
            return (FlowFieldScanRunMapper) Proxy.newProxyInstance(
                    FlowFieldScanRunMapper.class.getClassLoader(),
                    new Class<?>[]{FlowFieldScanRunMapper.class}, this);
        }

        FlowFieldScanRun saved(long runId) {
            return copy(rows.get(runId));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "insert" -> insert((FlowFieldScanRun) args[0]);
                case "selectById" -> copy(rows.get(((Number) args[0]).longValue()));
                case "updateById" -> update((FlowFieldScanRun) args[0]);
                case "failStaleRuns" -> failStale((long) args[0], (String) args[1],
                        (LocalDateTime) args[2], (LocalDateTime) args[3]);
                case "toString" -> "RunMapperFake";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private int insert(FlowFieldScanRun row) {
            boolean duplicate = rows.values().stream().anyMatch(existing ->
                    existing.getProjectId().equals(row.getProjectId())
                            && existing.getBranch().equals(row.getBranch())
                            && existing.getWindowEnd().equals(row.getWindowEnd()));
            if (duplicate) {
                throw new DuplicateKeyException("duplicate scan claim");
            }
            row.setId(nextId++);
            rows.put(row.getId(), copy(row));
            return 1;
        }

        private int update(FlowFieldScanRun row) {
            if (!rows.containsKey(row.getId())) {
                return 0;
            }
            rows.put(row.getId(), copy(row));
            return 1;
        }

        private int failStale(long projectId, String branch, LocalDateTime olderWindowEnd,
                              LocalDateTime finishedAt) {
            int changed = 0;
            for (Map.Entry<Long, FlowFieldScanRun> entry : rows.entrySet()) {
                FlowFieldScanRun row = entry.getValue();
                if (row.getProjectId() == projectId && row.getBranch().equals(branch)
                        && "RUNNING".equals(row.getStatus())
                        && row.getWindowEnd().isBefore(olderWindowEnd)) {
                    FlowFieldScanRun failed = copy(row);
                    failed.setStatus("FAILED");
                    failed.setErrorMessage("扫描窗口超时，已由后续任务终止");
                    failed.setFinishedAt(finishedAt);
                    entry.setValue(failed);
                    changed++;
                }
            }
            return changed;
        }

        private static FlowFieldScanRun copy(FlowFieldScanRun source) {
            if (source == null) {
                return null;
            }
            FlowFieldScanRun target = new FlowFieldScanRun();
            target.setId(source.getId());
            target.setProjectId(source.getProjectId());
            target.setProjectName(source.getProjectName());
            target.setProjectPath(source.getProjectPath());
            target.setBranch(source.getBranch());
            target.setWindowStart(source.getWindowStart());
            target.setWindowEnd(source.getWindowEnd());
            target.setStatus(source.getStatus());
            target.setCommitCount(source.getCommitCount());
            target.setChangedFileCount(source.getChangedFileCount());
            target.setHistoryCount(source.getHistoryCount());
            target.setFailedFileCount(source.getFailedFileCount());
            target.setSkippedCount(source.getSkippedCount());
            target.setCursorAdvanced(source.getCursorAdvanced());
            target.setErrorMessage(source.getErrorMessage());
            target.setStartedAt(source.getStartedAt());
            target.setFinishedAt(source.getFinishedAt());
            target.setCreateTime(source.getCreateTime());
            target.setUpdateTime(source.getUpdateTime());
            return target;
        }
    }

    private static final class CursorMapperFake implements InvocationHandler {
        private final Map<String, FlowFieldScanCursor> rows = new LinkedHashMap<>();
        private boolean failUpsert;

        FlowFieldScanCursorMapper mapper() {
            return (FlowFieldScanCursorMapper) Proxy.newProxyInstance(
                    FlowFieldScanCursorMapper.class.getClassLoader(),
                    new Class<?>[]{FlowFieldScanCursorMapper.class}, this);
        }

        FlowFieldScanCursor select(long projectId, String branch) {
            return copy(rows.get(key(projectId, branch)));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "selectCursor" -> select((long) args[0], (String) args[1]);
                case "upsertCursor" -> upsert((FlowFieldScanCursor) args[0]);
                case "toString" -> "CursorMapperFake";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private int upsert(FlowFieldScanCursor cursor) {
            if (failUpsert) {
                throw new IllegalStateException("cursor write failed");
            }
            rows.put(key(cursor.getProjectId(), cursor.getBranch()), copy(cursor));
            return 1;
        }

        private static String key(long projectId, String branch) {
            return projectId + "\u0000" + branch;
        }

        private static FlowFieldScanCursor copy(FlowFieldScanCursor source) {
            if (source == null) {
                return null;
            }
            FlowFieldScanCursor target = new FlowFieldScanCursor();
            target.setProjectId(source.getProjectId());
            target.setBranch(source.getBranch());
            target.setProjectName(source.getProjectName());
            target.setProjectPath(source.getProjectPath());
            target.setLastSuccessEnd(source.getLastSuccessEnd());
            target.setLastRunId(source.getLastRunId());
            target.setUpdateTime(source.getUpdateTime());
            return target;
        }
    }
}
