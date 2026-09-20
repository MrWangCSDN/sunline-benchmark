# GitLab POM Guard Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add safe, structured GitLab Webhook and POM guard decision logs to the existing `logs/app.log` output without changing guard behavior.

**Architecture:** Log at the two existing decision boundaries. `WebhookController` records receipt, authentication, routing, completion, and unexpected failure; `PomMergeGuardServiceImpl` records normalized MR/Push context, explicit ignore reasons, and final decisions. Tests attach Logback `ListAppender` instances to the real classes and prove that sensitive inputs never appear.

**Tech Stack:** Java 17, Spring Boot 3.1.5, SLF4J/Logback, JUnit 5, Spring MockMvc, Mockito, Maven

**Spec:** `/Users/java/obsidian/01 Engineering/sunline-benchmark/GitLab-POM合并守卫-系统设计.md`

## Global Constraints

- Continue using the SLF4J console stream redirected by `start.sh` to `logs/app.log`.
- Do not add dependencies, database tables, APIs, pages, or a separate log file.
- Never log Webhook Secret, request Token, Access Token, full payload, commit message, MR title/description, changed paths, or GitLab response body.
- Preserve HTTP responses and POM guard decisions.
- Use stable uppercase reason/error codes and structured `key=value` fields.

---

### Task 1: Webhook ingress and authentication logs

**Files:**
- Modify: `src/test/java/com/sunline/dict/controller/GitLabPomWebhookControllerTest.java`
- Modify: `src/main/java/com/sunline/dict/controller/WebhookController.java:84-110`

**Interfaces:**
- Consumes: `GitLabWebhookAuthenticator.authenticate(String)` and existing guard result maps.
- Produces: `GitLab Webhook received`, `authentication rejected`, `completed`, and `failed` log events.

- [ ] **Step 1: Write a failing authentication-log test**

Attach a `ListAppender<ILoggingEvent>` to the real `WebhookController` logger. Call MockMvc using a synthetic invalid token and event UUID. Assert `reason=TOKEN_MISMATCH` and `eventUuid=event-401` exist, and assert the supplied token does not.

```java
@Test
void logsAuthenticationRejectionWithoutLeakingSuppliedToken() throws Exception {
    String sensitiveToken = "SYNTHETIC_WEBHOOK_TOKEN";
    try (LogCapture logs = captureLogs()) {
        fixture("configured-secret").mockMvc.perform(post("/api/webhook/gitlab")
                .header("X-Gitlab-Event", "Merge Request Hook")
                .header("X-Gitlab-Event-UUID", "event-401")
                .header("X-Gitlab-Token", sensitiveToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"));
        assertTrue(logs.text().contains("reason=TOKEN_MISMATCH"));
        assertFalse(logs.text().contains(sensitiveToken));
    }
}
```

Implement `LogCapture` only in the test class; it attaches, returns formatted messages, and detaches in `close()`.

- [ ] **Step 2: Run the test and verify RED**

```bash
mvn -q -Dtest=GitLabPomWebhookControllerTest#logsAuthenticationRejectionWithoutLeakingSuppliedToken test
```

Expected: FAIL because `TOKEN_MISMATCH` is absent.

- [ ] **Step 3: Add minimal ingress/authentication logging**

At entry, log only sanitized event and event UUID. Before 503/401 returns, log `SECRET_NOT_CONFIGURED` or `TOKEN_MISMATCH` without the supplied token. Add a private helper mapping null/blank to `none` and escaping control characters.

```java
log.info("GitLab Webhook received: event={}, eventUuid={}", safeLogValue(event), safeLogValue(eventUuid));
log.warn("GitLab Webhook authentication rejected: event={}, eventUuid={}, reason=TOKEN_MISMATCH",
        safeLogValue(event), safeLogValue(eventUuid));
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Write failing completion and exception-log tests**

Return a literal guard result with `projectId`, `attempted`, `closed`, `bypassed`, `ignored`, and `errors`; assert those fields appear in one completion log. Make the guard throw `IllegalStateException("SENSITIVE_EXCEPTION_MESSAGE")`; assert the failure log identifies event/event UUID and does not contain the message or payload content.

- [ ] **Step 6: Run controller tests and verify RED**

```bash
mvn -q -Dtest=GitLabPomWebhookControllerTest test
```

Expected: FAIL because completion/failure summaries are absent.

- [ ] **Step 7: Implement completion and safe failure logging**

Store the guard result before returning and log numeric fields through a helper that supplies `-1` for missing `projectId` and `0` for missing counters. Log unexpected exceptions with stack traces but never payload or `e.getMessage()` in the template.

```java
log.info("GitLab Webhook completed: event={}, eventUuid={}, projectId={}, attempted={}, closed={}, bypassed={}, ignored={}, errors={}",
        safeLogValue(event), safeLogValue(eventUuid), number(result, "projectId", -1),
        number(result, "attempted", 0), number(result, "closed", 0), number(result, "bypassed", 0),
        number(result, "ignored", 0), number(result, "errors", 0));
log.error("GitLab Webhook failed: event={}, eventUuid={}", safeLogValue(event), safeLogValue(eventUuid), e);
```

- [ ] **Step 8: Verify and commit Task 1**

```bash
mvn -q -Dtest=GitLabPomWebhookControllerTest test
git add src/main/java/com/sunline/dict/controller/WebhookController.java src/test/java/com/sunline/dict/controller/GitLabPomWebhookControllerTest.java
git commit -m "feat: log GitLab webhook lifecycle"
```

Expected: all controller tests PASS.

---

### Task 2: POM guard ignore-reason and decision logs

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/PomMergeGuardServiceImplTest.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/PomMergeGuardServiceImpl.java:50-125`

**Interfaces:**
- Consumes: existing GitLab payload maps and `GitLabMergeRequestService` results.
- Produces: `POM guard MR received`, `MR ignored`, `MR decision`, `Push received`, and `Push completed` events.

- [ ] **Step 1: Write failing MR ignored-reason tests**

Capture the real service logger and exercise literal payloads. Assert these mappings independently:

```text
project outside list      → PROJECT_NOT_ALLOWED
target branch mismatch   → TARGET_BRANCH_MISMATCH
state not opened         → STATE_NOT_OPENED
action not open/update/reopen → ACTION_NOT_SUPPORTED
missing iid              → IID_MISSING
no POM diff              → NO_POM_CHANGE
```

Assert synthetic MR title, description, source branch, and file path values do not appear.

- [ ] **Step 2: Run guard tests and verify RED**

```bash
mvn -q -Dtest=PomMergeGuardServiceImplTest test
```

Expected: FAIL because explicit reasons are absent.

- [ ] **Step 3: Implement ordered reason evaluation**

Replace the compound entry filter with a side-effect-free helper returning one stable code or null:

```java
private String mergeRequestIgnoreReason(long projectId, Map<String, Object> payload, Map<String, Object> attrs) {
    if (!enabled) return "GUARD_DISABLED";
    if (!allowed(projectId)) return "PROJECT_NOT_ALLOWED";
    if (!"merge_request".equals(text(payload, "object_kind"))) return "OBJECT_KIND_MISMATCH";
    if (!targetBranch.equals(text(attrs, "target_branch"))) return "TARGET_BRANCH_MISMATCH";
    if (!"opened".equals(text(attrs, "state"))) return "STATE_NOT_OPENED";
    if (!Set.of("open", "update", "reopen").contains(text(attrs, "action"))) return "ACTION_NOT_SUPPORTED";
    return null;
}
```

Log MR receipt using only projectId, iid, action, state, targetBranch. Log the reason immediately before each ignored return, including current-MR state/target checks and no-POM outcome.

- [ ] **Step 4: Run guard tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Write failing final-decision tests**

Exercise CLOSED, BYPASSED, and close-failure fixtures. Assert exactly one decision log contains outcome, projectId, iid, POM count, note/close booleans, and stable error code. Assert commit message and actual POM path are absent.

- [ ] **Step 6: Run guard tests and verify RED**

Run the Step 2 command. Expected: FAIL because final summaries are absent.

- [ ] **Step 7: Centralize safe decision logging**

Wrap the existing decision map creation:

```java
private Map<String, Object> loggedDecision(String eventType, String outcome, long projectId, Long iid,
        List<String> paths, String bypass, boolean noted, boolean closed, String error) {
    Map<String, Object> value = decision(outcome, projectId, iid, paths, bypass, noted, closed, error);
    log.info("POM guard {} decision: projectId={}, iid={}, outcome={}, pomPathCount={}, noteCreated={}, closed={}, errorCode={}",
            eventType, projectId, iid == null ? "none" : iid, outcome, paths.size(), noted, closed,
            error == null ? "none" : error);
    return value;
}
```

Pass only literal `MR`/`Push`; never pass paths, bypass SHA, messages, title, description, source branch, or payload to logging calls.

- [ ] **Step 8: Add safe Push receipt/completion logs**

First add a failing test, then log projectId, whether the push targets the protected branch, and aggregate counters. Preserve the existing stable source-query failure log; do not log branch name, commit message, SHA, or file paths.

- [ ] **Step 9: Verify and commit Task 2**

```bash
mvn -q -Dtest=PomMergeGuardServiceImplTest test
git add src/main/java/com/sunline/dict/service/impl/PomMergeGuardServiceImpl.java src/test/java/com/sunline/dict/service/impl/PomMergeGuardServiceImplTest.java
git commit -m "feat: log POM guard decisions"
```

Expected: all guard tests PASS.

---

### Task 3: Regression and packaging

**Files:**
- Verify: files changed in Tasks 1-2
- Update only if behavior diverged: `/Users/java/obsidian/01 Engineering/sunline-benchmark/GitLab-POM合并守卫-系统设计.md`

**Interfaces:**
- Consumes: completed controller and guard logging.
- Produces: verified executable JAR whose console logs continue flowing into `logs/app.log`.

- [ ] **Step 1: Run focused regression**

```bash
mvn -q -Dtest=GitLabPomWebhookControllerTest,PomMergeGuardServiceImplTest,GitLabMergeRequestServiceImplTest,GitLabApiClientImplTest test
```

Expected: PASS.

- [ ] **Step 2: Audit changed logging calls**

```bash
git diff --check
git diff -- src/main/java/com/sunline/dict/controller/WebhookController.java src/main/java/com/sunline/dict/service/impl/PomMergeGuardServiceImpl.java
```

Manually confirm no log argument is a token, payload map, commit message, title/description, path collection, or raw GitLab body.

- [ ] **Step 3: Run the full suite**

```bash
mvn -q test
```

Expected: BUILD SUCCESS with zero failures/errors.

- [ ] **Step 4: Package and verify deployment path**

```bash
mvn -q -DskipTests package
test -f target/dict-manager-1.0.0.jar
rg -n 'logs/app.log' start.sh
```

Expected: JAR exists and `start.sh` still redirects stdout/stderr to the existing application log.

- [ ] **Step 5: Commit plan and any final cleanup**

```bash
git status --short
git add docs/superpowers/plans/2026-09-20-gitlab-pom-guard-observability.md
git commit -m "docs: plan POM guard observability"
```

If implementation files remain uncommitted, include only the four files named in Tasks 1-2. Do not stage unrelated work.
