package com.sunline.dict.service.flowchange;

/** Fetches a file's raw content from the configured GitLab instance at a specific Git ref. */
public interface GitLabFileVersionService {

    FileVersionResult fetch(long projectId, String pathWithNamespace, String filePath, String ref);

    enum Status {
        FOUND,
        NOT_FOUND,
        FAILED
    }

    record FileVersionResult(Status status, String content, String errorMessage) {
    }
}
