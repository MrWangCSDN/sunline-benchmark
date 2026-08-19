package com.sunline.dict.service.flowchange;

import java.util.List;

/** Provides the explicitly configured GitLab project scope for daily scans. */
public interface ConfiguredGitLabProjectProvider {

    List<Long> projectIds();
}
