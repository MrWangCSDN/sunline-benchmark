package com.sunline.dict.service.flowchange;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FlowFieldScanStateService {

    Optional<ScanClaim> claim(long projectId, String branch,
                              LocalDateTime windowEnd, LocalDateTime startedAt);

    void finish(long runId, ProjectIdentity project, RunCounters counters,
                Completion completion, String safeError, LocalDateTime finishedAt);

    record ScanClaim(long runId, long projectId, String branch,
                     LocalDateTime windowStart, LocalDateTime windowEnd) {
    }

    record ProjectIdentity(String projectName, String projectPath) {
    }

    record RunCounters(int commitCount, int changedFileCount, int historyCount,
                       int failedFileCount, int skippedCount) {
    }

    enum Completion {
        SUCCESS,
        COMPLETED_WITH_ERRORS,
        FAILED
    }
}
