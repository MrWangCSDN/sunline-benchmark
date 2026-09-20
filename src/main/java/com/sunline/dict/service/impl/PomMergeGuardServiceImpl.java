package com.sunline.dict.service.impl;

import com.sunline.dict.service.flowchange.ConfiguredGitLabProjectProvider;
import com.sunline.dict.service.pomguard.GitLabMergeRequestService;
import com.sunline.dict.service.pomguard.PomChangePolicy;
import com.sunline.dict.service.pomguard.PomMergeGuardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Coordinates authoritative MR inspection and conservative GitLab write actions. */
@Service
@ConditionalOnBean(GitLabMergeRequestService.class)
public class PomMergeGuardServiceImpl implements PomMergeGuardService {
    private static final Logger log = LoggerFactory.getLogger(PomMergeGuardServiceImpl.class);
    private static final String NOTE_PREFIX = "禁止提交 pom 文件。本合并请求包含 pom.xml，已自动关闭。\n如确需提交，请在 commit message 中加入：merge pom file go";
    private final GitLabMergeRequestService mergeRequests;
    private final PomChangePolicy policy;
    private final Set<Long> allowedProjects;
    private final boolean enabled;
    private final String targetBranch;
    private final int maxCommentPaths;

    public PomMergeGuardServiceImpl(GitLabMergeRequestService mergeRequests, PomChangePolicy policy,
                                    ConfiguredGitLabProjectProvider projects,
                                    @Value("${gitlab.pom-guard.enabled:true}") boolean enabled,
                                    @Value("${gitlab.pom-guard.target-branch:master}") String targetBranch,
                                    @Value("${gitlab.pom-guard.max-comment-paths:20}") int maxCommentPaths) {
        if (maxCommentPaths <= 0) throw new IllegalArgumentException("gitlab.pom-guard.max-comment-paths must be positive");
        this.mergeRequests = mergeRequests;
        this.policy = policy;
        this.allowedProjects = Set.copyOf(projects.projectIds());
        this.enabled = enabled;
        this.targetBranch = targetBranch;
        this.maxCommentPaths = maxCommentPaths;
    }

    @Override
    public Map<String, Object> handleMergeRequestHook(Map<String, Object> payload) {
        long projectId = projectId(payload);
        Result result = new Result("merge_request", projectId);
        Map<String, Object> attrs = map(payload, "object_attributes");
        Long iid = longValue(attrs, "iid");
        String action = text(attrs, "action");
        String state = text(attrs, "state");
        String target = text(attrs, "target_branch");
        log.info("POM guard MR received: projectId={}, iid={}, action={}, state={}, targetBranch={}", projectId,
                iid == null ? "none" : iid, safeLogValue(action), safeLogValue(state), safeLogValue(target));
        String ignoreReason = mergeRequestIgnoreReason(projectId, payload, attrs);
        if (ignoreReason != null) {
            log.info("POM guard MR ignored: projectId={}, iid={}, reason={}", projectId,
                    iid == null ? "none" : iid, ignoreReason);
            return result.ignored(null).map();
        }
        if (iid == null) {
            log.info("POM guard MR ignored: projectId={}, iid=none, reason=IID_MISSING", projectId);
            return result.ignored(null).map();
        }
        result.add(inspect(projectId, iid));
        return result.map();
    }

    @Override
    public Map<String, Object> handlePushHook(Map<String, Object> payload) {
        long projectId = projectId(payload);
        Result result = new Result("push", projectId);
        String ref = text(payload, "ref");
        boolean targetsProtectedBranch = ("refs/heads/" + targetBranch).equals(ref);
        log.info("POM guard Push received: projectId={}, targetsProtectedBranch={}", projectId, targetsProtectedBranch);
        if (!enabled || !allowed(projectId) || isBranchDeletion(payload) || ref == null || !ref.startsWith("refs/heads/")) {
            return completedPush(result.ignored(null));
        }
        String branch = ref.substring("refs/heads/".length());
        if (branch.isBlank()) return completedPush(result.ignored(null));
        if (targetBranch.equals(branch)) {
            List<String> paths = pushPomPaths(payload);
            if (paths.isEmpty()) return completedPush(result.ignored(null));
            Optional<String> bypass = policy.firstBypassCommitSha(pushCommits(payload));
            if (bypass.isPresent()) {
                log.info("POM guard direct master push bypassed: projectId={}, sha={}, pomPathCount={}", projectId, shortSha(bypass.get()), paths.size());
                result.add(loggedDecision("Push", "BYPASSED", projectId, null, paths, bypass.get(), false, false, null));
            }
            else {
                log.warn("POM guard direct master push detected: projectId={}, sha={}, pomPathCount={}", projectId, firstPushShortSha(payload), paths.size());
                result.add(loggedDecision("Push", "WARNED_DIRECT_PUSH", projectId, null, paths, null, false, false, null));
            }
            return completedPush(result);
        }
        try {
            mergeRequests.listOpen(projectId, branch, targetBranch).stream()
                    .filter(mr -> mr != null && mr.iid() > 0)
                    .collect(java.util.stream.Collectors.toMap(GitLabMergeRequestService.MergeRequestRef::iid, mr -> mr, (a, b) -> a))
                    .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> result.add(inspect(projectId, entry.getKey())));
        } catch (GitLabMergeRequestService.GitLabMergeRequestAccessException exception) {
            log.warn("POM guard source MR query failure: projectId={}, code={}", projectId, exception.errorCode());
            result.add(loggedDecision("Push", "ERROR", projectId, null, List.of(), null, false, false, exception.errorCode()));
        }
        return completedPush(result);
    }

    private Map<String, Object> inspect(long projectId, long iid) {
        try {
            GitLabMergeRequestService.MergeRequestRef current = mergeRequests.get(projectId, iid);
            if (!"opened".equals(current.state())) {
                log.info("POM guard MR ignored: projectId={}, iid={}, reason=CURRENT_MR_NOT_OPEN", projectId, iid);
                return loggedDecision("MR", "IGNORED", projectId, iid, List.of(), null, false, false, null);
            }
            if (!targetBranch.equals(current.targetBranch())) {
                log.info("POM guard MR ignored: projectId={}, iid={}, reason=CURRENT_TARGET_BRANCH_MISMATCH", projectId, iid);
                return loggedDecision("MR", "IGNORED", projectId, iid, List.of(), null, false, false, null);
            }
            List<String> paths = policy.pomPaths(mergeRequests.changes(projectId, iid, current.changesCount()));
            if (paths.isEmpty()) {
                log.info("POM guard MR ignored: projectId={}, iid={}, reason=NO_POM_CHANGE", projectId, iid);
                return loggedDecision("MR", "NO_POM_CHANGE", projectId, iid, paths, null, false, false, null);
            }
            Optional<String> bypass = policy.firstBypassCommitSha(mergeRequests.commits(projectId, iid));
            if (bypass.isPresent()) return loggedDecision("MR", "BYPASSED", projectId, iid, paths, bypass.get(), false, false, null);
            boolean noted = false;
            String noteError = null;
            try { mergeRequests.createNote(projectId, iid, note(paths)); noted = true; }
            catch (GitLabMergeRequestService.GitLabMergeRequestAccessException exception) { noteError = exception.errorCode(); log.warn("POM guard action failure: projectId={}, iid={}, code={}", projectId, iid, noteError); }
            try { mergeRequests.close(projectId, iid); return loggedDecision("MR", "CLOSED", projectId, iid, paths, null, noted, true, noteError); }
            catch (GitLabMergeRequestService.GitLabMergeRequestAccessException exception) { log.warn("POM guard action failure: projectId={}, iid={}, code={}", projectId, iid, exception.errorCode()); return loggedDecision("MR", "ERROR", projectId, iid, paths, null, noted, false, exception.errorCode()); }
        } catch (GitLabMergeRequestService.GitLabMergeRequestAccessException exception) {
            log.warn("POM guard query failure: projectId={}, iid={}, code={}", projectId, iid, exception.errorCode());
            return loggedDecision("MR", "ERROR", projectId, iid, List.of(), null, false, false, exception.errorCode());
        }
    }

    private String note(List<String> paths) {
        StringBuilder note = new StringBuilder(NOTE_PREFIX).append("\n\n命中的文件：");
        paths.stream().limit(maxCommentPaths).forEach(path -> note.append("\n- ").append(safePath(path)));
        if (paths.size() > maxCommentPaths) note.append("\n另有 ").append(paths.size() - maxCommentPaths).append(" 个文件");
        return note.toString();
    }

    private String mergeRequestIgnoreReason(long projectId, Map<String, Object> payload, Map<String, Object> attrs) {
        if (!enabled) return "GUARD_DISABLED";
        if (!allowed(projectId)) return "PROJECT_NOT_ALLOWED";
        if (!"merge_request".equals(text(payload, "object_kind"))) return "OBJECT_KIND_MISMATCH";
        if (!targetBranch.equals(text(attrs, "target_branch"))) return "TARGET_BRANCH_MISMATCH";
        if (!"opened".equals(text(attrs, "state"))) return "STATE_NOT_OPENED";
        String action = text(attrs, "action");
        if (!("open".equals(action) || "update".equals(action) || "reopen".equals(action))) return "ACTION_NOT_SUPPORTED";
        return null;
    }

    private Map<String, Object> loggedDecision(String eventType, String outcome, long projectId, Long iid,
                                               List<String> paths, String bypass, boolean noted,
                                               boolean closed, String error) {
        Map<String, Object> value = decision(outcome, projectId, iid, paths, bypass, noted, closed, error);
        log.info("POM guard {} decision: projectId={}, iid={}, outcome={}, pomPathCount={}, noteCreated={}, closed={}, errorCode={}",
                eventType, projectId, iid == null ? "none" : iid, outcome, paths.size(), noted, closed,
                error == null ? "none" : error);
        return value;
    }

    private Map<String, Object> completedPush(Result result) {
        log.info("POM guard Push completed: projectId={}, attempted={}, closed={}, bypassed={}, ignored={}, errors={}",
                result.projectId, result.attempted, result.closed, result.bypassed, result.ignored, result.errors);
        return result.map();
    }

    private static String safeLogValue(String value) {
        if (value == null || value.isBlank()) return "none";
        StringBuilder safe = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            safe.append(Character.isISOControl(character) ? '_' : character);
        }
        return safe.toString();
    }

    private List<String> pushPomPaths(Map<String, Object> payload) {
        TreeSet<String> paths = new TreeSet<>();
        for (Object commit : list(payload, "commits")) if (commit instanceof Map<?, ?> raw) for (String field : List.of("added", "modified", "removed"))
            for (Object path : list(cast(raw), field)) if (path instanceof String value && policy.isPomPath(value)) paths.add(value);
        return List.copyOf(paths);
    }
    private List<GitLabMergeRequestService.MergeRequestCommit> pushCommits(Map<String, Object> payload) {
        List<GitLabMergeRequestService.MergeRequestCommit> commits = new ArrayList<>();
        for (Object item : list(payload, "commits")) if (item instanceof Map<?, ?> raw) { Map<String, Object> commit = cast(raw); String sha = text(commit, "id"); String message = text(commit, "message"); if (sha != null && message != null) commits.add(new GitLabMergeRequestService.MergeRequestCommit(sha, message)); }
        return commits;
    }
    private static String safePath(String path) {
        StringBuilder value = new StringBuilder();
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (character == '\n') value.append("\\n");
            else if (character == '\r') value.append("\\r");
            else if (character == '\t') value.append("\\t");
            else if (Character.isISOControl(character) || character == '\u2028' || character == '\u2029') value.append(String.format("\\u%04x", (int) character));
            else value.append(character);
        }
        return value.toString();
    }
    private static String shortSha(String sha) { return sha == null ? "none" : sha.substring(0, Math.min(8, sha.length())); }
    private String firstPushShortSha(Map<String, Object> payload) { for (Object item : list(payload, "commits")) if (item instanceof Map<?, ?> raw) { String sha = text(cast(raw), "id"); if (sha != null) return shortSha(sha); } return "none"; }
    private boolean allowed(long projectId) { return projectId > 0 && allowedProjects.contains(projectId); }
    private static boolean isBranchDeletion(Map<String, Object> payload) { String after = text(payload, "after"); return after != null && after.matches("0{40}"); }
    private static long projectId(Map<String, Object> payload) { Long id = longValue(map(payload, "project"), "id"); return id == null ? -1 : id; }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Map<String, Object> value, String key) { Object result = value == null ? null : value.get(key); return result instanceof Map<?, ?> raw ? cast(raw) : Map.of(); }
    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Map<?, ?> raw) { return (Map<String, Object>) raw; }
    @SuppressWarnings("unchecked") private static List<Object> list(Map<String, Object> value, String key) { Object result = value == null ? null : value.get(key); return result instanceof List<?> list ? (List<Object>) list : List.of(); }
    private static String text(Map<String, Object> value, String key) { Object result = value == null ? null : value.get(key); return result instanceof String text ? text : null; }
    private static Long longValue(Map<String, Object> value, String key) { Object result = value == null ? null : value.get(key); return result instanceof Number number ? number.longValue() : null; }
    private static Map<String, Object> decision(String outcome, long projectId, Long iid, List<String> paths, String bypass, boolean noted, boolean closed, String error) { Map<String, Object> decision = new LinkedHashMap<>(); decision.put("outcome", outcome); decision.put("projectId", projectId); decision.put("mergeRequestIid", iid); decision.put("pomPaths", paths); decision.put("bypassCommitSha", bypass); decision.put("noteCreated", noted); decision.put("closed", closed); decision.put("safeErrorCode", error); return java.util.Collections.unmodifiableMap(decision); }

    private static final class Result {
        final String eventType; final long projectId; final List<Map<String, Object>> decisions = new ArrayList<>(); int attempted; int closed; int bypassed; int ignored; int errors;
        Result(String eventType, long projectId) { this.eventType = eventType; this.projectId = projectId; }
        Result ignored(Map<String, Object> decision) { ignored++; if (decision != null) add(decision); return this; }
        void add(Map<String, Object> decision) { decisions.add(decision); attempted++; String outcome = (String) decision.get("outcome"); if ("CLOSED".equals(outcome)) closed++; else if ("BYPASSED".equals(outcome)) bypassed++; else if ("ERROR".equals(outcome)) errors++; else if ("IGNORED".equals(outcome) || "NO_POM_CHANGE".equals(outcome)) ignored++; }
        Map<String, Object> map() { Map<String, Object> value = new LinkedHashMap<>(); value.put("eventType", eventType); value.put("projectId", projectId); value.put("attempted", attempted); value.put("closed", closed); value.put("bypassed", bypassed); value.put("ignored", ignored); value.put("errors", errors); value.put("decisions", List.copyOf(decisions)); return Map.copyOf(value); }
    }
}
