package com.sunline.dict.service.pomguard;

import java.util.List;

/** Trusted GitLab operations required to inspect and close merge requests. */
public interface GitLabMergeRequestService {
    MergeRequestRef get(long projectId, long iid);
    List<MergeRequestRef> listOpen(long projectId, String sourceBranch, String targetBranch);
    List<MergeRequestChange> changes(long projectId, long iid);
    List<MergeRequestCommit> commits(long projectId, long iid);
    void createNote(long projectId, long iid, String body);
    void close(long projectId, long iid);

    record MergeRequestRef(long projectId, long iid, String sourceBranch, String targetBranch, String state) { }
    record MergeRequestChange(String oldPath, String newPath, boolean newFile, boolean deletedFile, boolean renamedFile) { }
    record MergeRequestCommit(String sha, String message) { }

    class GitLabMergeRequestAccessException extends RuntimeException {
        private final String errorCode;
        public GitLabMergeRequestAccessException(String errorCode) {
            super(errorCode);
            this.errorCode = errorCode;
        }
        public String errorCode() { return errorCode; }
    }
}
