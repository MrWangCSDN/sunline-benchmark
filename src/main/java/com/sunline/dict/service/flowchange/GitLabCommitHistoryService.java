package com.sunline.dict.service.flowchange;

import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType;

import java.time.OffsetDateTime;
import java.util.List;

/** Reads project metadata and stable Flowtrans-related commit history from GitLab. */
public interface GitLabCommitHistoryService {

    GitLabProjectInfo project(long projectId);

    List<GitLabCommitInfo> commits(long projectId, String branch,
                                   OffsetDateTime exclusiveStart, OffsetDateTime inclusiveEnd);

    List<FlowtransFileWorkItem> changedFlowtransFiles(long projectId, String commitSha);

    record GitLabProjectInfo(long projectId, String projectName, String projectPath) {
    }

    record GitLabCommitInfo(long projectId, String projectName, String projectPath,
                             String commitSha, String parentSha, String message,
                             String authorName, String authorEmail, OffsetDateTime committedAt) {
    }

    record FlowtransFileWorkItem(FileChangeType fileChangeType, String effectivePath,
                                 String beforePath, String afterPath) {
    }

    class GitLabAccessException extends RuntimeException {
        private final GitLabApiClient.Status status;
        private final String safeMessage;

        public GitLabAccessException(GitLabApiClient.Status status, String safeMessage) {
            super(safeMessage);
            this.status = status;
            this.safeMessage = safeMessage;
        }

        public GitLabApiClient.Status status() {
            return status;
        }

        public String safeMessage() {
            return safeMessage;
        }
    }
}
