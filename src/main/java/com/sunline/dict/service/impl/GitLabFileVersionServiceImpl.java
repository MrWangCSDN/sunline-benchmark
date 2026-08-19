package com.sunline.dict.service.impl;

import com.sunline.dict.service.flowchange.GitLabApiClient;
import com.sunline.dict.service.flowchange.GitLabFileVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Reads raw GitLab repository files through the trusted API boundary. */
@Service
@ConditionalOnProperty(name = "git.gitlab.url")
public class GitLabFileVersionServiceImpl implements GitLabFileVersionService {

    private final GitLabApiClient apiClient;

    @Autowired
    public GitLabFileVersionServiceImpl(GitLabApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public FileVersionResult fetch(long projectId, String pathWithNamespace, String filePath, String ref) {
        if (filePath == null || ref == null) {
            return new FileVersionResult(Status.PERMANENT_FAILURE, null, "GitLab request parameters are invalid");
        }
        GitLabApiClient.ApiResponse response = apiClient.get(
                "/projects/" + projectId + "/repository/files/" + encodePath(filePath) + "/raw",
                Map.of("ref", ref));
        return switch (response.status()) {
            case SUCCESS -> new FileVersionResult(Status.FOUND, response.body(), null);
            case NOT_FOUND -> new FileVersionResult(Status.NOT_FOUND, null, null);
            case TRANSIENT_FAILURE -> new FileVersionResult(Status.TRANSIENT_FAILURE, null, response.errorMessage());
            case PERMANENT_FAILURE -> new FileVersionResult(Status.PERMANENT_FAILURE, null, response.errorMessage());
        };
    }

    private static String encodePath(String filePath) {
        StringBuilder encoded = new StringBuilder();
        for (byte byteValue : filePath.getBytes(StandardCharsets.UTF_8)) {
            int unsigned = byteValue & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z') || (unsigned >= 'A' && unsigned <= 'Z')
                    || (unsigned >= '0' && unsigned <= '9') || unsigned == '-' || unsigned == '.'
                    || unsigned == '_' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%')
                        .append(Character.toUpperCase(Character.forDigit(unsigned >>> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(unsigned & 0xf, 16)));
            }
        }
        return encoded.toString();
    }
}
