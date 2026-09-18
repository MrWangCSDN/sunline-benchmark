# GitLab POM Webhook Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在既有 GitLab Webhook 中加入目标为 `master` 的 POM 合并门禁：MR 或源分支 push 涉及严格小写 `pom.xml` 时自动留言并关闭开放 MR，仅 commit message 严格包含 `merge pom file go` 时豁免。

**Architecture:** `/api/webhook/gitlab` 先执行 Secret 常量时间鉴权，再把 Merge Request Hook 或 Push Hook 委托给独立 `PomMergeGuardService`。服务只接受允许项目列表，通过受信任 `GitLabApiClient` 查询 MR changes/commits 并执行 note/close；原有 Push Hook 的 flowtrans 解析和调用关系扫描保持兼容。直接 push 到 `master` 只做安全告警，不改写 Git 历史。

**Tech Stack:** Java 17、Spring Boot 3.1.5、Jackson、JDK `HttpClient`、JUnit 5、Mockito、Spring MockMvc

**Spec:** `/Users/java/obsidian/.worktrees/codex-pom-webhook-guard-design/01 Engineering/sunline-benchmark/GitLab-POM合并门禁-系统设计.md`（同目录的数据模型、API 接口共同构成约束）

## Global Constraints

- 只处理 GitLab Webhook；GitHub 和通用 `/api/webhook/git` 入口不成为 POM 门禁事实源。
- 只处理 `git.projects.list` 明确列出的工程；目标 MR 必须为 `master`、`opened`。
- POM 路径仅匹配 `pom.xml` 或以 `/pom.xml` 结尾，严格区分大小写。
- 豁免仅检查 MR commits 或 Push payload commits 的 `message`，严格包含 `merge pom file go`；不得检查 MR title、description 或 note。
- `gitlab.webhook-secret` 未配置返回 HTTP 503；缺失或错误返回 HTTP 401；两者均不得调用业务服务或 GitLab API。
- Secret 使用 `MessageDigest.isEqual` 比较；Token、Secret、完整 payload、commit message、响应正文不得进入日志或错误结果。
- GitLab API 只访问 `git.gitlab.url`，禁止重定向，路径/查询/form 参数分别编码。
- MR changes/list/commits 分页；MR IID 去重升序，POM 路径去重字典序。
- 未豁免的 MR 先 note 后 close；note 失败仍尝试 close；只有 close 成功才能返回 `CLOSED`。
- 直接 push 到 `master` 命中未豁免 POM 时只记录安全 WARN，不 revert/reset/force-push。
- 不新增数据库表，不把 Webhook 重新接入 flowtrans 接口变动历史。
- 所有生产行为遵循 TDD：测试先失败，再写最小实现使其通过。

---

### Task 1: 扩展受信任 GitLab HTTP 客户端

**Files:**
- Modify: `src/main/java/com/sunline/dict/service/flowchange/GitLabApiClient.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/GitLabApiClientImpl.java`
- Modify: `src/test/java/com/sunline/dict/service/impl/GitLabApiClientImplTest.java`

**Interfaces:**
- Produces: `ApiResponse postForm(String apiPath, Map<String,String> form)`
- Produces: `ApiResponse putForm(String apiPath, Map<String,String> form)`
- Both methods share the existing same-origin URI validation, `PRIVATE-TOKEN`, timeout, redirect prohibition, status classification and sanitized errors.

- [ ] **Step 1: Write failing HTTP contract tests**

Add tests that start the existing local `HttpServer`, call `postForm` and `putForm`, and assert literal behavior:

```java
@Test
void postsEncodedFormToConfiguredOriginWithoutFollowingRedirects() {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> contentType = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    server.createContext("/api/v4/projects/42/merge_requests/7/notes", exchange -> {
        method.set(exchange.getRequestMethod());
        contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 201, "{}");
    });

    ApiResponse response = client("secret-token").postForm(
            "/projects/42/merge_requests/7/notes", Map.of("body", "禁止提交 pom 文件\n路径 a/pom.xml"));

    assertEquals(Status.SUCCESS, response.status());
    assertEquals("POST", method.get());
    assertEquals("application/x-www-form-urlencoded", contentType.get());
    assertEquals("body=%E7%A6%81%E6%AD%A2%E6%8F%90%E4%BA%A4%20pom%20%E6%96%87%E4%BB%B6%0A%E8%B7%AF%E5%BE%84%20a%2Fpom.xml", body.get());
}

@Test
void putsEncodedCloseEvent() {
    AtomicReference<String> method = new AtomicReference<>();
    AtomicReference<String> body = new AtomicReference<>();
    server.createContext("/api/v4/projects/42/merge_requests/7", exchange -> {
        method.set(exchange.getRequestMethod());
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, "{}");
    });

    ApiResponse response = client("secret-token").putForm(
            "/projects/42/merge_requests/7", Map.of("state_event", "close"));

    assertEquals(Status.SUCCESS, response.status());
    assertEquals("PUT", method.get());
    assertEquals("state_event=close", body.get());
}
```

Also cover absolute paths and redirect responses for mutation methods, and assert no request reaches a redirect origin.

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
mvn -DskipTests=false -Dtest=GitLabApiClientImplTest test
```

Expected: compilation fails because `postForm` and `putForm` do not exist.

- [ ] **Step 3: Implement the minimal shared request method**

Extend the interface:

```java
ApiResponse postForm(String apiPath, Map<String, String> form);
ApiResponse putForm(String apiPath, Map<String, String> form);
```

In `GitLabApiClientImpl`, route GET/POST/PUT through one internal sender. For form methods build the request as:

```java
HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofString(encodeParameters(form), StandardCharsets.UTF_8);
builder.header("Content-Type", "application/x-www-form-urlencoded");
builder.method(method, body);
```

Mutation methods do not blindly retry an ambiguous response; perform one attempt. Existing GET retains its three-attempt transient retry behavior. Keep error messages status-only and sanitized.

- [ ] **Step 4: Run targeted tests and verify GREEN**

Run the command from Step 2. Expected: all `GitLabApiClientImplTest` tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sunline/dict/service/flowchange/GitLabApiClient.java \
  src/main/java/com/sunline/dict/service/impl/GitLabApiClientImpl.java \
  src/test/java/com/sunline/dict/service/impl/GitLabApiClientImplTest.java
git commit -m "feat: extend trusted GitLab API client"
```

### Task 2: 增加 POM 规则和 GitLab MR 数据适配

**Files:**
- Create: `src/main/java/com/sunline/dict/service/pomguard/PomChangePolicy.java`
- Create: `src/main/java/com/sunline/dict/service/pomguard/GitLabMergeRequestService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/GitLabMergeRequestServiceImpl.java`
- Create: `src/test/java/com/sunline/dict/service/pomguard/PomChangePolicyTest.java`
- Create: `src/test/java/com/sunline/dict/service/impl/GitLabMergeRequestServiceImplTest.java`

**Interfaces:**
- Produces records `MergeRequestRef(long projectId,long iid,String sourceBranch,String targetBranch,String state)`, `MergeRequestChange(String oldPath,String newPath,boolean newFile,boolean deletedFile,boolean renamedFile)`, and `MergeRequestCommit(String sha,String message)`.
- Produces `listOpen(long projectId,String sourceBranch,String targetBranch)`, `changes(long projectId,long iid)`, `commits(long projectId,long iid)`, `createNote(long projectId,long iid,String body)`, `close(long projectId,long iid)`.
- Produces pure `PomChangePolicy.isPomPath`, `pomPaths`, and `firstBypassCommitSha` behavior.

- [ ] **Step 1: Write failing policy tests**

Use literal parameter cases:

```java
@ParameterizedTest
@CsvSource({"pom.xml,true", "module/pom.xml,true", "POM.xml,false", "pom.XML,false", "pom.xml.bak,false", "docs/pom.xml.md,false"})
void matchesOnlyStrictLowercasePomPath(String path, boolean expected) {
    assertEquals(expected, policy.isPomPath(path));
}

@Test
void titleAndDescriptionCannotParticipateBecausePolicyAcceptsOnlyCommitMessages() {
    assertEquals(Optional.empty(), policy.firstBypassCommitSha(List.of(
            new MergeRequestCommit("a1", "MERGE POM FILE GO"),
            new MergeRequestCommit("b2", "ordinary change"))));
    assertEquals(Optional.of("c3"), policy.firstBypassCommitSha(List.of(
            new MergeRequestCommit("c3", "build: merge pom file go"))));
}
```

Add renamed/deleted cases where either old or new exact path is included, de-duplicated, sorted.

- [ ] **Step 2: Verify policy tests fail, implement minimal pure policy, verify pass**

Run:

```bash
mvn -DskipTests=false -Dtest=PomChangePolicyTest test
```

Implement exact `String.equals("pom.xml") || String.endsWith("/pom.xml")` and exact `message.contains(bypassPhrase)` without lowercasing or trimming the message.

- [ ] **Step 3: Write failing adapter tests**

Use a fake `GitLabApiClient` with ordered `ApiResponse` values. Assert:

- `listOpen` sends `state=opened`, literal source branch, target `master`, and follows `x-next-page`.
- `changes` parses all old/new paths and booleans.
- `commits` follows pagination and preserves GitLab order/message exactly.
- `createNote` sends `body`; `close` sends `state_event=close`.
- malformed JSON or non-success responses throw a sanitized `GitLabMergeRequestAccessException` containing only the stable error code.

- [ ] **Step 4: Verify RED, implement adapter, verify GREEN**

Run:

```bash
mvn -DskipTests=false -Dtest=GitLabMergeRequestServiceImplTest test
```

Implement with Jackson and `@ConditionalOnProperty(name="git.gitlab.url")`. Use constructor property `${gitlab.pom-guard.page-size:100}` and reject non-positive values. Follow pagination using normalized `x-next-page`; return immutable lists.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sunline/dict/service/pomguard \
  src/main/java/com/sunline/dict/service/impl/GitLabMergeRequestServiceImpl.java \
  src/test/java/com/sunline/dict/service/pomguard/PomChangePolicyTest.java \
  src/test/java/com/sunline/dict/service/impl/GitLabMergeRequestServiceImplTest.java
git commit -m "feat: add GitLab pom merge request adapter"
```

### Task 3: 实现 MR 与 Push 门禁编排

**Files:**
- Create: `src/main/java/com/sunline/dict/service/pomguard/PomMergeGuardService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/PomMergeGuardServiceImpl.java`
- Create: `src/test/java/com/sunline/dict/service/impl/PomMergeGuardServiceImplTest.java`

**Interfaces:**
- Produces: `Map<String,Object> handleMergeRequestHook(Map<String,Object> payload)`.
- Produces: `Map<String,Object> handlePushHook(Map<String,Object> payload)`.
- Consumes Task 2 service, policy, and `ConfiguredGitLabProjectProvider`.
- Result keys: `eventType`, `projectId`, `attempted`, `closed`, `bypassed`, `ignored`, `errors`, `decisions`.

- [ ] **Step 1: Write failing service tests for MR Hook**

Cover each observable branch:

1. project not in allow-list: `ignored=1`, zero GitLab calls.
2. non-master, non-opened, or action outside `open/update/reopen`: ignored.
3. no exact POM path: ignored, commits are not queried.
4. title/description containing phrase but commits do not: note and close.
5. any commit message containing exact phrase: bypass; no write calls.
6. note succeeds + close succeeds: outcome `CLOSED`, counts correct.
7. note fails + close succeeds: still `CLOSED`, `noteCreated=false`.
8. close fails: `ERROR`, never report `closed=true`.

The expected message prefix is literal:

```text
禁止提交 pom 文件。本合并请求包含 pom.xml，已自动关闭。
如确需提交，请在 commit message 中加入：merge pom file go
```

Only append at most `max-comment-paths` sorted paths; append an omitted-count line when truncated.

- [ ] **Step 2: Verify MR tests fail, implement, verify pass**

Run:

```bash
mvn -DskipTests=false -Dtest=PomMergeGuardServiceImplTest test
```

Validate payload types defensively. Do not read title/description. Before each action use authoritative changes/commits from the service. Return stable error codes only.

- [ ] **Step 3: Add failing Push Hook tests**

Cover:

- source branch push queries every opened MR to master, de-duplicates IID, sorts ascending, and applies the same MR check.
- direct master push exact POM + no bypass returns `WARNED_DIRECT_PUSH` and performs no MR write call.
- direct master push exact POM + exact commit-message bypass returns `BYPASSED`.
- uppercase path or uppercase phrase does not match.
- branch deletion (`after` all zeros) is ignored.

- [ ] **Step 4: Verify RED, implement Push flow, verify GREEN**

Parse `refs/heads/<branch>` only. For direct master payload collect `commits[].added/modified/removed`; never log path values, messages, or payload. For source branches call `listOpen(projectId, branch, targetBranch)` and apply the MR function to all unique IIDs.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sunline/dict/service/pomguard/PomMergeGuardService.java \
  src/main/java/com/sunline/dict/service/impl/PomMergeGuardServiceImpl.java \
  src/test/java/com/sunline/dict/service/impl/PomMergeGuardServiceImplTest.java
git commit -m "feat: enforce pom merge request guard"
```

### Task 4: 接入 Secret 鉴权和 GitLab Webhook 路由

**Files:**
- Create: `src/main/java/com/sunline/dict/service/pomguard/GitLabWebhookAuthenticator.java`
- Modify: `src/main/java/com/sunline/dict/controller/WebhookController.java`
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/com/sunline/dict/controller/GitLabPomWebhookControllerTest.java`
- Modify: `src/test/java/com/sunline/dict/controller/WebhookControllerCurrentStateTest.java`
- Modify: `src/test/java/com/sunline/dict/integration/FlowFieldDailyScanArchitectureTest.java`

**Interfaces:**
- Authenticator produces `AuthenticationResult` enum: `AUTHORIZED`, `SECRET_NOT_CONFIGURED`, `UNAUTHORIZED`.
- `/api/webhook/gitlab` accepts `X-Gitlab-Token`, handles `Push Hook`/`push` and `Merge Request Hook`, and returns real HTTP 401/503 through `ResponseEntity<Result<Map<String,Object>>>`.
- `/api/webhook/github`, `/api/webhook/git`, and `/api/webhook/health` keep their existing public behavior and do not call POM guard.

- [ ] **Step 1: Write failing authenticator/controller tests**

Use standalone MockMvc with mocked dependencies and assert:

```java
mockMvc.perform(post("/api/webhook/gitlab")
        .header("X-Gitlab-Event", "Merge Request Hook")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
    .andExpect(status().isUnauthorized())
    .andExpect(jsonPath("$.code").value(401))
    .andExpect(jsonPath("$.message").value("WEBHOOK_UNAUTHORIZED"));
```

Separate fixture with blank server secret expects HTTP 503 and `WEBHOOK_SECRET_MISSING`. Verify zero interactions with `PomMergeGuardService`, `WebhookService`, and `CallRelationScanService` for both failures.

Authorized MR expects only `handleMergeRequestHook`. Authorized Push expects `handlePushHook` plus existing `handleGitLabPushEvent` and Java incremental scan behavior. Unknown events return HTTP 200 ignored. Generic `/git` with GitLab headers must not call `PomMergeGuardService`.

- [ ] **Step 2: Verify RED, implement authenticator and route, verify GREEN**

Run:

```bash
mvn -DskipTests=false -Dtest=GitLabPomWebhookControllerTest,WebhookControllerCurrentStateTest test
```

Authenticator compares UTF-8 bytes using:

```java
MessageDigest.isEqual(configuredSecret.getBytes(StandardCharsets.UTF_8),
        suppliedToken.getBytes(StandardCharsets.UTF_8));
```

Authenticate before `isScanning()` or any logging that depends on the payload. For authorized MR, do not invoke legacy flowtrans handling or relation scanning. For authorized Push, invoke POM guard regardless of relation full-scan state; legacy flowtrans/relation work may retain its existing pause semantics. Merge guard result is the response data; legacy processing remains a side effect.

- [ ] **Step 3: Add safe configuration**

Add only environment-backed/non-secret values:

```yaml
gitlab:
  webhook-secret: ${GITLAB_WEBHOOK_SECRET:}
  pom-guard:
    enabled: true
    target-branch: master
    bypass-phrase: merge pom file go
    max-comment-paths: 20
    page-size: 100
```

Do not print or alter existing credentials unrelated to this feature.

- [ ] **Step 4: Run architecture and complete regression tests**

Run:

```bash
mvn -DskipTests=false test
mvn -DskipTests=false package
```

Expected: all existing 225 tests plus new tests pass; package succeeds.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sunline/dict/service/pomguard/GitLabWebhookAuthenticator.java \
  src/main/java/com/sunline/dict/controller/WebhookController.java \
  src/main/resources/application.yml \
  src/test/java/com/sunline/dict/controller/GitLabPomWebhookControllerTest.java \
  src/test/java/com/sunline/dict/controller/WebhookControllerCurrentStateTest.java \
  src/test/java/com/sunline/dict/integration/FlowFieldDailyScanArchitectureTest.java
git commit -m "feat: secure GitLab pom webhook guard"
```

### Task 5: Final verification and delivery audit

**Files:**
- Modify only if verification exposes a tested defect.

**Interfaces:**
- Confirms the complete branch behavior against the design documents.

- [ ] **Step 1: Inspect changed files and secret safety**

```bash
git diff --check codex/flowtrans-daily-scan...HEAD
git status --short
git diff --name-only codex/flowtrans-daily-scan...HEAD
```

Confirm there is no SQL migration, no database entity/mapper change, and no hard-coded Webhook Secret.

- [ ] **Step 2: Run full verification**

```bash
mvn -DskipTests=false test
mvn -DskipTests=false package
```

- [ ] **Step 3: Perform one whole-branch review**

Review the full diff against all Global Constraints, with priority on authentication ordering, false exemptions, MR pagination, write-side failure truthfulness, and regression of legacy Push Hook behavior. Fix any Critical/Important finding through a failing test first and rerun full verification.

- [ ] **Step 4: Record final state**

```bash
git log --oneline codex/flowtrans-daily-scan..HEAD
git status --short
```

The branch must be clean and all implementation commits must be present before delivery.
