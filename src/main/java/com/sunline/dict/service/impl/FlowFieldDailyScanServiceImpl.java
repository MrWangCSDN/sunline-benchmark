package com.sunline.dict.service.impl;

import com.sunline.dict.service.FlowFieldChangeLogService;
import com.sunline.dict.service.FlowFieldChangeLogService.HistoryState;
import com.sunline.dict.service.FlowFieldChangeLogService.WriteDisposition;
import com.sunline.dict.service.FlowFieldChangeLogService.WriteOutcome;
import com.sunline.dict.service.FlowFieldDailyScanService;
import com.sunline.dict.service.flowchange.ConfiguredGitLabProjectProvider;
import com.sunline.dict.service.flowchange.FlowFieldChangeDiffService;
import com.sunline.dict.service.flowchange.FlowFieldChangeMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.Completion;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.ProjectIdentity;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.RunCounters;
import com.sunline.dict.service.flowchange.FlowFieldScanStateService.ScanClaim;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshotParser;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.FlowtransFileWorkItem;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabAccessException;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabCommitInfo;
import com.sunline.dict.service.flowchange.GitLabCommitHistoryService.GitLabProjectInfo;
import com.sunline.dict.service.flowchange.GitLabFileVersionService;
import com.sunline.dict.service.flowchange.GitLabFileVersionService.FileVersionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Project-isolated scanner for configured GitLab master branches. */
@Service
@ConditionalOnProperty(name = "git.gitlab.url")
public class FlowFieldDailyScanServiceImpl implements FlowFieldDailyScanService {

    private static final String BRANCH = "master";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Comparator<GitLabCommitInfo> COMMIT_ORDER = Comparator
            .comparing(GitLabCommitInfo::committedAt)
            .thenComparing(GitLabCommitInfo::commitSha);

    private final ConfiguredGitLabProjectProvider projectProvider;
    private final GitLabCommitHistoryService commitHistory;
    private final GitLabFileVersionService fileVersions;
    private final FlowFieldScanStateService scanState;
    private final FlowFieldChangeLogService historyWriter;
    private final FlowtransInterfaceSnapshotParser parser;
    private final FlowFieldChangeDiffService diffService;

    @Autowired
    public FlowFieldDailyScanServiceImpl(ConfiguredGitLabProjectProvider projectProvider,
                                         GitLabCommitHistoryService commitHistory,
                                         GitLabFileVersionService fileVersions,
                                         FlowFieldScanStateService scanState,
                                         FlowFieldChangeLogService historyWriter) {
        this.projectProvider = projectProvider;
        this.commitHistory = commitHistory;
        this.fileVersions = fileVersions;
        this.scanState = scanState;
        this.historyWriter = historyWriter;
        this.parser = new FlowtransInterfaceSnapshotParser();
        this.diffService = new FlowFieldChangeDiffService();
    }

    @Override
    public BatchScanResult scanAll(LocalDateTime windowEnd) {
        Objects.requireNonNull(windowEnd, "windowEnd 不能为空");
        int attemptedProjects = 0;
        int successfulProjects = 0;
        int errorProjects = 0;
        for (long projectId : projectProvider.projectIds()) {
            attemptedProjects++;
            ProjectOutcome outcome = scanProject(projectId, windowEnd);
            if (outcome == ProjectOutcome.SUCCESS) {
                successfulProjects++;
            } else if (outcome == ProjectOutcome.ERROR) {
                errorProjects++;
            }
        }
        return new BatchScanResult(attemptedProjects, successfulProjects, errorProjects);
    }

    private ProjectOutcome scanProject(long projectId, LocalDateTime windowEnd) {
        ScanClaim claim = null;
        ProjectIdentity projectIdentity = new ProjectIdentity(null, null);
        MutableCounters counters = new MutableCounters();
        try {
            Optional<ScanClaim> claimed = scanState.claim(
                    projectId, BRANCH, windowEnd, nowShanghai());
            if (claimed.isEmpty()) {
                return ProjectOutcome.NOT_CLAIMED;
            }
            claim = claimed.orElseThrow();
            GitLabProjectInfo project = commitHistory.project(projectId);
            projectIdentity = new ProjectIdentity(project.projectName(), project.projectPath());

            List<GitLabCommitInfo> commits = commitHistory.commits(
                            projectId, BRANCH, shanghaiOffset(claim.windowStart()),
                            shanghaiOffset(claim.windowEnd())).stream()
                    .sorted(COMMIT_ORDER)
                    .toList();
            for (GitLabCommitInfo commit : commits) {
                counters.commitCount++;
                List<FlowtransFileWorkItem> workItems =
                        commitHistory.changedFlowtransFiles(projectId, commit.commitSha());
                for (FlowtransFileWorkItem workItem : workItems) {
                    counters.changedFileCount++;
                    processFile(claim, project, commit, workItem, counters);
                }
            }

            Completion completion = counters.failedFileCount == 0
                    ? Completion.SUCCESS : Completion.COMPLETED_WITH_ERRORS;
            String safeError = counters.failedFileCount == 0
                    ? null : counters.failedFileCount + " XML file(s) failed";
            scanState.finish(claim.runId(), projectIdentity, counters.snapshot(),
                    completion, safeError, nowShanghai());
            return completion == Completion.SUCCESS ? ProjectOutcome.SUCCESS : ProjectOutcome.ERROR;
        } catch (RuntimeException exception) {
            if (claim != null) {
                finishFailed(claim, projectIdentity, counters, safeProjectError(exception));
            }
            return ProjectOutcome.ERROR;
        }
    }

    private void processFile(ScanClaim claim, GitLabProjectInfo project,
                             GitLabCommitInfo commit, FlowtransFileWorkItem workItem,
                             MutableCounters counters) {
        FlowFieldChangeMeta meta = meta(claim, project, commit, workItem.effectivePath());
        if (historyWriter.state(meta.dedupKey()) == HistoryState.SUCCESS) {
            counters.skippedCount++;
            return;
        }

        SnapshotPair snapshots = snapshots(project, commit, workItem);
        FlowtransInterfaceSnapshot before;
        FlowtransInterfaceSnapshot after;
        try {
            before = parse(snapshots.beforeContent());
            after = parse(snapshots.afterContent());
        } catch (IllegalArgumentException parseFailure) {
            WriteOutcome outcome = historyWriter.recordFailure(meta, "XML snapshot parse failed");
            if (outcome.disposition() == WriteDisposition.SKIPPED) {
                counters.skippedCount++;
            } else {
                counters.failedFileCount++;
            }
            return;
        }

        Optional<FlowFieldChangeSet> changeSet = diffService.diff(before, after);
        if (changeSet.isEmpty()) {
            counters.skippedCount++;
            return;
        }
        WriteOutcome outcome = historyWriter.recordSuccess(meta, changeSet.orElseThrow());
        if (outcome.disposition() == WriteDisposition.SKIPPED) {
            counters.skippedCount++;
        } else {
            counters.historyCount++;
        }
    }

    private SnapshotPair snapshots(GitLabProjectInfo project, GitLabCommitInfo commit,
                                   FlowtransFileWorkItem workItem) {
        if (commit.parentSha() == null || commit.parentSha().isBlank()) {
            String path = firstText(workItem.afterPath(), workItem.effectivePath());
            return new SnapshotPair(null, fetchRequired(project, path, commit.commitSha()));
        }
        return switch (workItem.fileChangeType()) {
            case ADD -> new SnapshotPair(null,
                    fetchRequired(project, workItem.afterPath(), commit.commitSha()));
            case MODIFY -> new SnapshotPair(
                    fetchRequired(project, workItem.beforePath(), commit.parentSha()),
                    fetchRequired(project, workItem.afterPath(), commit.commitSha()));
            case DELETE -> new SnapshotPair(
                    fetchRequired(project, workItem.beforePath(), commit.parentSha()), null);
        };
    }

    private String fetchRequired(GitLabProjectInfo project, String filePath, String ref) {
        if (filePath == null || filePath.isBlank() || ref == null || ref.isBlank()) {
            throw new GitLabFileUnavailableException("INVALID_REQUEST");
        }
        FileVersionResult result = fileVersions.fetch(
                project.projectId(), project.projectPath(), filePath, ref);
        if (result == null) {
            throw new GitLabFileUnavailableException("EMPTY_RESPONSE");
        }
        if (result.status() != GitLabFileVersionService.Status.FOUND || result.content() == null) {
            throw new GitLabFileUnavailableException(result.status().name());
        }
        return result.content();
    }

    private FlowtransInterfaceSnapshot parse(String content) {
        return content == null ? null : parser.parse(content);
    }

    private FlowFieldChangeMeta meta(ScanClaim claim, GitLabProjectInfo project,
                                     GitLabCommitInfo commit, String effectiveFilePath) {
        ZonedDateTime committedAt = commit.committedAt().atZoneSameInstant(SHANGHAI);
        return new FlowFieldChangeMeta(claim.runId(), committedAt.toLocalDate(),
                project.projectId(), project.projectName(), project.projectPath(), BRANCH,
                effectiveFilePath, commit.parentSha(), commit.commitSha(), commit.message(),
                commit.authorName(), commit.authorEmail(), committedAt.toLocalDateTime());
    }

    private void finishFailed(ScanClaim claim, ProjectIdentity project,
                              MutableCounters counters, String safeError) {
        try {
            scanState.finish(claim.runId(), project, counters.snapshot(), Completion.FAILED,
                    safeError, nowShanghai());
        } catch (RuntimeException ignored) {
            // A state persistence failure cannot be repaired inside this project scan.
        }
    }

    private String safeProjectError(RuntimeException exception) {
        if (exception instanceof GitLabAccessException accessException) {
            return "GitLab request failed: " + accessException.status().name();
        }
        if (exception instanceof GitLabFileUnavailableException unavailable) {
            return "GitLab file version unavailable: " + unavailable.status;
        }
        return "Project scan failed";
    }

    private static String firstText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static OffsetDateTime shanghaiOffset(LocalDateTime localDateTime) {
        return localDateTime.atZone(SHANGHAI).toOffsetDateTime();
    }

    private static LocalDateTime nowShanghai() {
        return LocalDateTime.now(SHANGHAI);
    }

    private enum ProjectOutcome {
        SUCCESS,
        ERROR,
        NOT_CLAIMED
    }

    private record SnapshotPair(String beforeContent, String afterContent) {
    }

    private static final class GitLabFileUnavailableException extends RuntimeException {
        private final String status;

        private GitLabFileUnavailableException(String status) {
            super("GitLab file version unavailable");
            this.status = status;
        }
    }

    private static final class MutableCounters {
        private int commitCount;
        private int changedFileCount;
        private int historyCount;
        private int failedFileCount;
        private int skippedCount;

        private RunCounters snapshot() {
            return new RunCounters(commitCount, changedFileCount, historyCount,
                    failedFileCount, skippedCount);
        }
    }
}
