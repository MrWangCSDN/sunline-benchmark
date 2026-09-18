package com.sunline.dict.service.flowchange;

import java.util.List;
import java.util.Map;

/** Trusted HTTP boundary for requests to the configured GitLab API origin. */
public interface GitLabApiClient {

    ApiResponse get(String apiPath, Map<String, String> query);

    default ApiResponse postForm(String apiPath, Map<String, String> form) {
        throw new UnsupportedOperationException("GitLab form mutations are not supported by this client");
    }

    default ApiResponse putForm(String apiPath, Map<String, String> form) {
        throw new UnsupportedOperationException("GitLab form mutations are not supported by this client");
    }

    enum Status {
        SUCCESS,
        NOT_FOUND,
        TRANSIENT_FAILURE,
        PERMANENT_FAILURE
    }

    record ApiResponse(Status status, int httpStatus, String body,
                       Map<String, List<String>> headers, String errorMessage) {
    }
}
