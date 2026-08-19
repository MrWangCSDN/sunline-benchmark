package com.sunline.dict.service.impl;

import com.sunline.dict.entity.FlowFieldScanCursor;
import com.sunline.dict.entity.FlowFieldScanRun;
import com.sunline.dict.mapper.FlowFieldScanCursorMapper;
import com.sunline.dict.mapper.FlowFieldScanRunMapper;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class FlowFieldScanStateServiceImpl implements FlowFieldScanStateService {

    private static final int MAX_ERROR_LENGTH = 2000;
    private static final Pattern UNSAFE_ERROR = Pattern.compile(
            "(?is)(?:"
                    + "authorization|bearer|token|secret|password"
                    + "|\\b[a-z][a-z0-9+.-]*://|\\bjdbc:"
                    + "|\\b(?:select\\b.+?\\bfrom|insert\\s+into|update\\s+\\S+\\s+set"
                    + "|delete\\s+from|merge\\s+into"
                    + "|(?:create|alter|drop|truncate)\\s+(?:table|database|schema|index|view|user|role)"
                    + "|call\\s+[\\w.$]+|grant\\b.+?\\bto|revoke\\b.+?\\bfrom)\\b"
                    + "|(?:^|\\R)\\s*at\\s+(?:[^\\s/]+/)?[\\w.$]+\\([^\\r\\n)]*\\)"
                    + "|\\bcaused\\s+by:|\\b[\\w.$]+(?:exception|error):"
                    + ")");

    private final FlowFieldScanRunMapper runMapper;
    private final FlowFieldScanCursorMapper cursorMapper;

    @Autowired
    public FlowFieldScanStateServiceImpl(FlowFieldScanRunMapper runMapper,
                                         FlowFieldScanCursorMapper cursorMapper) {
        this.runMapper = runMapper;
        this.cursorMapper = cursorMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<ScanClaim> claim(long projectId, String branch,
                                     LocalDateTime windowEnd, LocalDateTime startedAt) {
        runMapper.failStaleRuns(projectId, branch, windowEnd, startedAt);
        FlowFieldScanCursor cursor = cursorMapper.selectCursor(projectId, branch);
        LocalDateTime windowStart;
        if (cursor != null) {
            windowStart = cursor.getLastSuccessEnd();
        } else {
            LocalDateTime failedStart = runMapper.selectEarliestUnadvancedWindowStart(projectId, branch);
            windowStart = failedStart == null ? windowEnd.toLocalDate().atStartOfDay() : failedStart;
        }

        FlowFieldScanRun run = new FlowFieldScanRun();
        run.setProjectId(projectId);
        run.setBranch(branch);
        run.setWindowStart(windowStart);
        run.setWindowEnd(windowEnd);
        run.setStatus(CompletionStatus.RUNNING.name());
        run.setCommitCount(0);
        run.setChangedFileCount(0);
        run.setHistoryCount(0);
        run.setFailedFileCount(0);
        run.setSkippedCount(0);
        run.setCursorAdvanced(false);
        run.setStartedAt(startedAt);
        run.setCreateTime(startedAt);
        run.setUpdateTime(startedAt);
        try {
            runMapper.insert(run);
        } catch (DuplicateKeyException duplicate) {
            return Optional.empty();
        }
        if (run.getId() == null) {
            throw new IllegalStateException("未生成扫描运行主键");
        }
        return Optional.of(new ScanClaim(run.getId(), projectId, branch, windowStart, windowEnd));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(long runId, ProjectIdentity project, RunCounters counters,
                       Completion completion, String safeError, LocalDateTime finishedAt) {
        FlowFieldScanRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new IllegalArgumentException("扫描运行记录不存在");
        }

        boolean advancesCursor = completion == Completion.SUCCESS
                || completion == Completion.COMPLETED_WITH_ERRORS;
        run.setProjectName(project.projectName());
        run.setProjectPath(project.projectPath());
        run.setStatus(completion.name());
        run.setCommitCount(counters.commitCount());
        run.setChangedFileCount(counters.changedFileCount());
        run.setHistoryCount(counters.historyCount());
        run.setFailedFileCount(counters.failedFileCount());
        run.setSkippedCount(counters.skippedCount());
        run.setCursorAdvanced(advancesCursor);
        run.setErrorMessage(normalizeError(safeError, completion));
        run.setFinishedAt(finishedAt);
        run.setUpdateTime(finishedAt);
        if (runMapper.finishRunning(run) != 1) {
            throw new IllegalStateException("扫描运行执行权已失效");
        }

        if (advancesCursor) {
            FlowFieldScanCursor cursor = new FlowFieldScanCursor();
            cursor.setProjectId(run.getProjectId());
            cursor.setBranch(run.getBranch());
            cursor.setProjectName(project.projectName());
            cursor.setProjectPath(project.projectPath());
            cursor.setLastSuccessEnd(run.getWindowEnd());
            cursor.setLastRunId(runId);
            cursorMapper.upsertCursor(cursor);
        }
    }

    private static String normalizeError(String error, Completion completion) {
        if (error == null || error.isBlank()) {
            return completion == Completion.FAILED ? "扫描失败" : null;
        }
        if (UNSAFE_ERROR.matcher(error).find()) {
            return "扫描失败（敏感信息已隐藏）";
        }
        String normalized = error.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replaceAll("\\p{Cntrl}", "");
        return normalized.length() <= MAX_ERROR_LENGTH
                ? normalized
                : normalized.substring(0, MAX_ERROR_LENGTH);
    }

    private enum CompletionStatus {
        RUNNING
    }
}
