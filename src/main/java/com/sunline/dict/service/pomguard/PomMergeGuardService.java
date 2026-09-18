package com.sunline.dict.service.pomguard;

import java.util.Map;

/** Handles GitLab MR and push payloads for the POM merge guard. */
public interface PomMergeGuardService {
    Map<String, Object> handleMergeRequestHook(Map<String, Object> payload);
    Map<String, Object> handlePushHook(Map<String, Object> payload);
}
