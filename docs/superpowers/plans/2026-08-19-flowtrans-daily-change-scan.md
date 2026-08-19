# 交易接口每日变动扫描 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 每天 22:00（Asia/Shanghai）扫描配置 GitLab 工程 master 分支的每个 commit，只把 `*.flowtrans.xml` 中 input/output 下 field 的新增、删除、属性修改及整文件新增/删除落库，并通过字段日视图和扫描状态页面查询。

**Architecture:** 受信任 GitLab API 客户端统一认证、安全边界、分页和有限重试；commit 历史服务把 GitLab project/commit/diff 转换为稳定领域对象；扫描编排器按工程游标逐 commit 读取第一父和当前文件版本，复用安全 XML 解析器与纯字段 diff，事务写入历史并推进游标。每日扫描是接口变动历史唯一写入口，Webhook 只保留当前交易解析。

**Tech Stack:** Java 17、Spring Boot 3.1.1、MyBatis-Plus 3.5.4、Jackson、JDK `HttpClient`、JUnit 5、Mockito、HTML/CSS/ES6、Vue 3 CDN、axios CDN、MariaDB/MySQL。

**Spec:** `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易接口变动历史-系统设计.md`（同时遵循同目录 `交易接口变动历史-数据模型.md` 与 `交易接口变动历史-API接口.md`）

## Global Constraints

- 调度 cron 固定默认 `0 0 22 * * ?`，时区固定默认 `Asia/Shanghai`，仅 `flow-field-change.scan.enabled=true` 执行。
- 工程范围只读取 `application.yml` 的 `git.projects.list`；空配置跳过，不回退到 Token 可见的全部项目。
- 只扫描 `master`，只匹配大小写敏感的严格后缀 `.flowtrans.xml`。
- 扫描窗口为 `(windowStart, windowEnd]`；首次是当天 `00:00` 到 `22:00`，后续从该工程上次成功结束时间到本次 `22:00`。
- 每个 commit 只与第一父 commit 比较；根提交把命中文件作为新增；commit 按提交时间、SHA 稳定升序处理。
- 只认定 input/output 下具有非空 `id` 的 `<field>`；字段身份是 `(ioType, fieldPath, fieldId)`，名称、值和路径严格区分大小写。
- 字段顺序、交易名称、注释及其他非 field 内容不算接口变化；身份变化算删除加新增；一个字段多属性变化只生成一个 `MODIFY`。
- 整文件新增/删除即使零字段也写主记录；重命名拆成旧路径 `DELETE` 和新路径 `ADD`。
- 历史幂等材料固定为 `projectId + "|master|" + commitSha + "|" + effectiveFilePath`，不得加入文件变化类型。
- 确定性 XML/重复身份错误留失败历史并允许游标推进；GitLab 网络、429、401/403、5xx 和数据库错误不推进游标。
- GitLab 请求只发往 `git.gitlab.url` 配置源，禁重定向；Token、认证头、完整 URL、响应正文、SQL 和堆栈不得进入日志/API 错误摘要。
- 不新增 Maven 或前端依赖；历史页面和 API 只读，不增加手工扫描、修改或删除入口。
- 保留旧表物理兼容列，但新领域模型和新写入不再使用 `webhook_uuid`、`before_sha`、`after_sha`。
- 当前工作区存在用户未提交内容。必须保留 `FlowFieldDetailService*` 与 `WebhookServiceImpl` 的当前交易字段清理改动；允许重写历史页面及其测试；绝不修改或暂存 `clash-config.yaml`，绝不修改或暂存 `src/main/resources/sql/create_flow_field_detail.sql`。
- 每次提交只显式暂存本任务列出的文件，禁止 `git add .`、`git add -A` 或清理工作树。
- 所有生产行为遵循严格 TDD：先写会因缺少行为而失败的测试，确认 RED，再实现最小 GREEN 并运行相关回归。

---

## File Map

**GitLab adapter**

- Create `src/main/java/com/sunline/dict/service/flowchange/GitLabApiClient.java`: 受信任 GET、响应分类和分页头。
- Create `src/main/java/com/sunline/dict/service/impl/GitLabApiClientImpl.java`: URL 编码、Token、禁重定向、超时、429/5xx 三次有限重试。
- Modify `src/main/java/com/sunline/dict/service/flowchange/GitLabFileVersionService.java`: 使用可分类失败状态。
- Modify `src/main/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImpl.java`: 复用 `GitLabApiClient`。
- Create `src/main/java/com/sunline/dict/service/flowchange/ConfiguredGitLabProjectProvider.java`: 配置工程 ID。
- Create `src/main/java/com/sunline/dict/service/impl/ConfiguredGitLabProjectProviderImpl.java`: 严格解析 `git.projects.list`。
- Create `src/main/java/com/sunline/dict/service/flowchange/GitLabCommitHistoryService.java`: project/commit/diff 领域契约。
- Create `src/main/java/com/sunline/dict/service/impl/GitLabCommitHistoryServiceImpl.java`: GitLab JSON 和分页适配。

**Scan state and persistence**

- Create `src/main/java/com/sunline/dict/entity/FlowFieldScanRun.java` and `FlowFieldScanCursor.java`.
- Create `src/main/java/com/sunline/dict/mapper/FlowFieldScanRunMapper.java` and `FlowFieldScanCursorMapper.java`.
- Create `src/main/java/com/sunline/dict/service/flowchange/FlowFieldScanStateService.java`.
- Create `src/main/java/com/sunline/dict/service/impl/FlowFieldScanStateServiceImpl.java`.
- Create `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeMeta.java`.
- Modify `FlowFieldChangeLog`, `FlowFieldChangeLogService`, `FlowFieldChangeLogServiceImpl` and both history mappers for daily commit persistence and failed-row upgrade.
- Modify `src/main/resources/sql/create_flow_field_change_tables.sql` and create `src/main/resources/sql/migrate_flow_field_change_daily_scan.sql`.

**Scan orchestration and scheduling**

- Create `src/main/java/com/sunline/dict/service/FlowFieldDailyScanService.java`.
- Create `src/main/java/com/sunline/dict/service/impl/FlowFieldDailyScanServiceImpl.java`.
- Create `src/main/java/com/sunline/dict/scheduler/DailyFlowtransChangeScheduler.java`.
- Modify `DictManagerApplication`, `application.yml`, `WebhookService`, `WebhookController`, `WebhookServiceImpl`.
- Delete obsolete Webhook history capture types only after scheduled scanning is wired: `FlowFieldChangeCaptureService`, `FlowFieldChangeCaptureServiceImpl`, `FlowFieldChangeCaptureMeta`, `FlowFieldChangeCaptureResult` and their capture-only test.

**Read API and page**

- Replace `FlowFieldChangeDtos` with field-row, detail and scan-run DTOs.
- Create `src/main/java/com/sunline/dict/mapper/FlowFieldChangeQueryMapper.java` and `src/main/resources/mapper/FlowFieldChangeQueryMapper.xml`.
- Modify `FlowFieldChangeController` and `flow-field-change-history.html`.
- Reuse existing menu wiring in `index.html`, `create_menu_table.sql`, and `add_flow_field_change_history_menu.sql` unless a contract test proves it incomplete.

---

### Task 1: Build the trusted GitLab API boundary

**Files:**
- Create: `src/main/java/com/sunline/dict/service/flowchange/GitLabApiClient.java`
- Create: `src/main/java/com/sunline/dict/service/impl/GitLabApiClientImpl.java`
- Modify: `src/main/java/com/sunline/dict/service/flowchange/GitLabFileVersionService.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImpl.java`
- Create: `src/test/java/com/sunline/dict/service/impl/GitLabApiClientImplTest.java`
- Modify: `src/test/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImplTest.java`

**Interfaces:**
- Produces `GitLabApiClient.ApiResponse get(String apiPath, Map<String,String> query)`.
- `ApiResponse` is `record ApiResponse(Status status, int httpStatus, String body, Map<String,List<String>> headers, String errorMessage)`.
- `Status` values are `SUCCESS`, `NOT_FOUND`, `TRANSIENT_FAILURE`, `PERMANENT_FAILURE`.
- `GitLabFileVersionService.FileVersionResult fetch(long projectId, String pathWithNamespace, String filePath, String ref)` retains its signature; its status becomes `FOUND`, `NOT_FOUND`, `TRANSIENT_FAILURE`, `PERMANENT_FAILURE`.

- [ ] **Step 1: Write failing real-HTTP boundary tests**

Use JDK `HttpServer` on `127.0.0.1`. Tests must prove behavior, not source text:

```java
@Test
void encodes_path_and_query_and_returns_pagination_headers() {
    ApiResponse response = client.get("/projects/42/repository/commits",
            Map.of("ref_name", "master", "path", "目录/A B.flowtrans.xml"));
    assertEquals(Status.SUCCESS, response.status());
    assertEquals("2", response.headers().get("x-next-page").get(0));
    assertEquals("/api/v4/projects/42/repository/commits?path=%E7%9B%AE%E5%BD%95%2FA%20B.flowtrans.xml&ref_name=master",
            capturedRequest.getRawPath() + "?" + capturedRequest.getRawQuery());
}

@Test
void retries_429_and_5xx_at_most_three_attempts_then_classifies_transient_failure() {
    ApiResponse response = client.get("/projects/42", Map.of());
    assertEquals(Status.TRANSIENT_FAILURE, response.status());
    assertEquals(3, requestCount.get());
    assertFalse(response.errorMessage().contains("secret-token"));
}
```

Also cover: `PRIVATE-TOKEN` is sent only to the configured origin; 200, 404, 401/403 and other 4xx classification; redirects are not followed; timeout/interruption/IO summaries are sanitized; query order is deterministic; base URI with query/fragment is rejected.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:

```bash
mvn -DskipTests=false -Dtest=GitLabApiClientImplTest,GitLabFileVersionServiceImplTest test
```

Expected: compilation fails because `GitLabApiClient` does not exist and file result statuses are incomplete.

- [ ] **Step 3: Implement the API contract and secure client**

Define:

```java
public interface GitLabApiClient {
    ApiResponse get(String apiPath, Map<String, String> query);
    enum Status { SUCCESS, NOT_FOUND, TRANSIENT_FAILURE, PERMANENT_FAILURE }
    record ApiResponse(Status status, int httpStatus, String body,
                       Map<String, List<String>> headers, String errorMessage) {}
}
```

`GitLabApiClientImpl` must normalize only an HTTP(S) configured base URI, append `/api/v4`, reject absolute/unrooted API paths, sort query keys, percent-encode UTF-8 components, use `HttpClient.Redirect.NEVER`, 10-second connect timeout, 30-second request timeout, and at most three attempts for 429/5xx/IO/timeout. Package-private constructor injection supplies `HttpClient` and `RetrySleeper`; tests use a no-op sleeper while production uses bounded `200ms`, `400ms` delays. Never retain response bodies in an error message.

- [ ] **Step 4: Refactor raw file reads through the client**

`GitLabFileVersionServiceImpl.fetch` calls:

```java
apiClient.get("/projects/" + projectId + "/repository/files/" + encodePath(filePath) + "/raw",
        Map.of("ref", ref));
```

The path encoder must encode `/` inside repository file paths as `%2F`; map API statuses without collapsing transient and permanent failures. `pathWithNamespace` remains accepted for binary compatibility but is never used to choose the request origin.

- [ ] **Step 5: Run GREEN and regression tests**

```bash
mvn -DskipTests=false -Dtest=GitLabApiClientImplTest,GitLabFileVersionServiceImplTest test
```

Expected: all tests pass with three-attempt bounds and no secret values in output.

- [ ] **Step 6: Commit the trusted adapter slice**

```bash
git add src/main/java/com/sunline/dict/service/flowchange/GitLabApiClient.java \
        src/main/java/com/sunline/dict/service/impl/GitLabApiClientImpl.java \
        src/main/java/com/sunline/dict/service/flowchange/GitLabFileVersionService.java \
        src/main/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/GitLabApiClientImplTest.java \
        src/test/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImplTest.java
git commit -m "feat: add trusted GitLab API client"
```

---

### Task 2: Read configured projects and stable commit history

**Files:**
- Create: `src/main/java/com/sunline/dict/service/flowchange/ConfiguredGitLabProjectProvider.java`
- Create: `src/main/java/com/sunline/dict/service/impl/ConfiguredGitLabProjectProviderImpl.java`
- Create: `src/main/java/com/sunline/dict/service/flowchange/GitLabCommitHistoryService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/GitLabCommitHistoryServiceImpl.java`
- Create: `src/test/java/com/sunline/dict/service/impl/ConfiguredGitLabProjectProviderImplTest.java`
- Create: `src/test/java/com/sunline/dict/service/impl/GitLabCommitHistoryServiceImplTest.java`

**Interfaces:**
- Consumes `GitLabApiClient` from Task 1.
- Produces `List<Long> ConfiguredGitLabProjectProvider.projectIds()`.
- Produces `GitLabProjectInfo project(long projectId)`, `List<GitLabCommitInfo> commits(long projectId, String branch, OffsetDateTime exclusiveStart, OffsetDateTime inclusiveEnd)`, and `List<FlowtransFileWorkItem> changedFlowtransFiles(long projectId, String commitSha)`.
- `GitLabProjectInfo` is `(long projectId, String projectName, String projectPath)`.
- `GitLabCommitInfo` is `(long projectId, String projectName, String projectPath, String commitSha, String parentSha, String message, String authorName, String authorEmail, OffsetDateTime committedAt)`.
- `FlowtransFileWorkItem` is `(FileChangeType fileChangeType, String effectivePath, String beforePath, String afterPath)` using existing `FlowFieldChangeSet.FileChangeType`.
- GitLab access failures throw `GitLabAccessException(Status status, String safeMessage)`; callers use its status to fail a scan without exposing response content.

- [ ] **Step 1: Write RED tests for explicit project scope**

```java
@Test
void parses_deduplicates_and_preserves_configured_project_order() {
    assertEquals(List.of(42L, 7L), provider(" 42,7,42 ").projectIds());
}

@Test
void blank_project_configuration_means_no_projects() {
    assertEquals(List.of(), provider(" ").projectIds());
}
```

Also prove zero/negative/non-numeric IDs fail fast with a safe `IllegalArgumentException`; there is no fallback API call.

- [ ] **Step 2: Write RED tests for project, commit and diff pagination**

Use a deterministic fake `GitLabApiClient` returning complete GitLab JSON fixtures. Assert:

```java
assertEquals(List.of("early-b", "early-c", "late-a"),
        service.commits(42L, "master", start, end).stream()
                .map(GitLabCommitInfo::commitSha).toList());
assertEquals("first-parent", commits.get(0).parentSha());
```

Fixtures must include two commit pages, duplicated inclusive boundary commits, a merge commit with two `parent_ids`, a root commit, and commits exactly at start/end. Only `(start,end]` remains, duplicates are removed by SHA, and final order is `committedAt ASC, commitSha ASC`.

Diff fixtures must prove:

```java
assertEquals(List.of(
        new FlowtransFileWorkItem(DELETE, "old/T001.flowtrans.xml", "old/T001.flowtrans.xml", null),
        new FlowtransFileWorkItem(ADD, "new/T001.flowtrans.xml", null, "new/T001.flowtrans.xml")),
        renamedItems);
```

Also cover exact lowercase `.flowtrans.xml`, ADD/MODIFY/DELETE flags, rename split, irrelevant files, diff pagination and safe propagation of API failures.

- [ ] **Step 3: Verify RED**

```bash
mvn -DskipTests=false -Dtest=ConfiguredGitLabProjectProviderImplTest,GitLabCommitHistoryServiceImplTest test
```

Expected: compilation fails because provider/history types do not exist.

- [ ] **Step 4: Implement configuration parsing and history adapter**

Use Jackson `ObjectMapper`; require project response fields `id`, `name`, `path_with_namespace`; parse commit `id`, `parent_ids[0]`, `message`, `author_name`, `author_email`, `committed_date`. Request commits with `ref_name=master`, ISO `since/until`, `per_page=${flow-field-change.scan.page-size:100}`, `page`; request each commit diff with the same page size and follow `x-next-page` until blank. Deduplicate commits by SHA before stable sorting.

- [ ] **Step 5: Run GREEN and adapter regression**

```bash
mvn -DskipTests=false -Dtest=ConfiguredGitLabProjectProviderImplTest,GitLabCommitHistoryServiceImplTest,GitLabApiClientImplTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit the commit-history slice**

```bash
git add src/main/java/com/sunline/dict/service/flowchange/ConfiguredGitLabProjectProvider.java \
        src/main/java/com/sunline/dict/service/impl/ConfiguredGitLabProjectProviderImpl.java \
        src/main/java/com/sunline/dict/service/flowchange/GitLabCommitHistoryService.java \
        src/main/java/com/sunline/dict/service/impl/GitLabCommitHistoryServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/ConfiguredGitLabProjectProviderImplTest.java \
        src/test/java/com/sunline/dict/service/impl/GitLabCommitHistoryServiceImplTest.java
git commit -m "feat: read configured GitLab commit history"
```

---

### Task 3: Persist scan runs, independent cursors and execution claims

**Files:**
- Create: `src/main/java/com/sunline/dict/entity/FlowFieldScanRun.java`
- Create: `src/main/java/com/sunline/dict/entity/FlowFieldScanCursor.java`
- Create: `src/main/java/com/sunline/dict/mapper/FlowFieldScanRunMapper.java`
- Create: `src/main/java/com/sunline/dict/mapper/FlowFieldScanCursorMapper.java`
- Create: `src/main/java/com/sunline/dict/service/flowchange/FlowFieldScanStateService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/FlowFieldScanStateServiceImpl.java`
- Create: `src/test/java/com/sunline/dict/service/impl/FlowFieldScanStateServiceImplTest.java`
- Modify: `src/main/resources/sql/create_flow_field_change_tables.sql`
- Create: `src/test/java/com/sunline/dict/sql/FlowFieldDailyScanSchemaContractTest.java`

**Interfaces:**
- Produces `Optional<ScanClaim> claim(long projectId, String branch, LocalDateTime windowEnd, LocalDateTime startedAt)`.
- `ScanClaim` is `(long runId, long projectId, String branch, LocalDateTime windowStart, LocalDateTime windowEnd)`.
- Produces `void finish(long runId, ProjectIdentity project, RunCounters counters, Completion completion, String safeError, LocalDateTime finishedAt)`.
- `ProjectIdentity` is `(String projectName, String projectPath)`; `RunCounters` is `(int commitCount, int changedFileCount, int historyCount, int failedFileCount, int skippedCount)`; `Completion` is `SUCCESS`, `COMPLETED_WITH_ERRORS`, `FAILED`.
- `SUCCESS` and `COMPLETED_WITH_ERRORS` update the project/branch cursor in the same transaction; `FAILED` never updates it.
- `FlowFieldScanCursorMapper` provides `selectCursor(long projectId, String branch)` and `upsertCursor(FlowFieldScanCursor cursor)`; do not use MyBatis-Plus `selectById` for the composite key.
- `FlowFieldScanRunMapper` provides `failStaleRuns(long projectId, String branch, LocalDateTime olderWindowEnd, LocalDateTime finishedAt)` for the conditional stale-run update.

- [ ] **Step 1: Write RED service tests**

Using mapper fakes that preserve inserted entities and throw `DuplicateKeyException` on duplicate `(projectId,branch,windowEnd)`, prove:

```java
@Test
void first_claim_starts_at_midnight_and_duplicate_window_has_no_execution_right() {
    ScanClaim first = service.claim(42L, "master",
            LocalDateTime.of(2026, 8, 19, 22, 0), now).orElseThrow();
    assertEquals(LocalDateTime.of(2026, 8, 19, 0, 0), first.windowStart());
    assertTrue(service.claim(42L, "master", first.windowEnd(), now).isEmpty());
}

@Test
void failed_run_keeps_cursor_while_completed_with_errors_advances_it() {
    service.finish(failedRun, project, counters, FAILED, "GitLab request timed out", now);
    assertNull(cursorMapper.selectCursor(42L, "master"));
    service.finish(nextRun, project, counters, COMPLETED_WITH_ERRORS, "1 file failed", now);
    assertEquals(nextEnd, cursorMapper.selectCursor(42L, "master").getLastSuccessEnd());
}
```

Also cover: later claims start at cursor; each project cursor is isolated; stale RUNNING rows with older `window_end` become FAILED before new claim; counters/project metadata are persisted; error text is truncated and sanitized; finish transaction throws on cursor failure so run/cursor cannot disagree.

- [ ] **Step 2: Write RED deployment-schema contract tests**

Read the SQL resource, normalize comments/whitespace, split complete statements, and assert one complete table definition for each of `flow_field_scan_run` and `flow_field_scan_cursor`, every approved column/type/default, primary key `(project_id,branch)`, and unique key `(project_id,branch,window_end)`. This test protects the shipped deployment artifact; mapper fakes in Step 1 separately prove concurrency and transaction behavior without adding an embedded-database dependency.

- [ ] **Step 3: Verify RED**

```bash
mvn -DskipTests=false -Dtest=FlowFieldScanStateServiceImplTest,FlowFieldDailyScanSchemaContractTest test
```

Expected: compilation fails because scan state classes do not exist.

- [ ] **Step 4: Implement entities, mappers and transactional state service**

Map exactly the run/cursor columns from the data-model spec. `claim` marks only older RUNNING rows for the same project/branch as FAILED, reads the cursor, computes midnight on first run, inserts a RUNNING row, and returns empty on a unique-key collision. `finish` updates run status/counters/timestamps; for successful completions it upserts cursor `(project_id,branch)` to the run window end and sets `cursor_advanced=true` within one `@Transactional(rollbackFor=Exception.class)` boundary.

- [ ] **Step 5: Add run/cursor DDL and run GREEN**

Append the exact approved run/cursor table definitions to `create_flow_field_change_tables.sql`, then run:

```bash
mvn -DskipTests=false -Dtest=FlowFieldScanStateServiceImplTest,FlowFieldDailyScanSchemaContractTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit scan-state slice**

```bash
git add src/main/java/com/sunline/dict/entity/FlowFieldScanRun.java \
        src/main/java/com/sunline/dict/entity/FlowFieldScanCursor.java \
        src/main/java/com/sunline/dict/mapper/FlowFieldScanRunMapper.java \
        src/main/java/com/sunline/dict/mapper/FlowFieldScanCursorMapper.java \
        src/main/java/com/sunline/dict/service/flowchange/FlowFieldScanStateService.java \
        src/main/java/com/sunline/dict/service/impl/FlowFieldScanStateServiceImpl.java \
        src/main/resources/sql/create_flow_field_change_tables.sql \
        src/test/java/com/sunline/dict/service/impl/FlowFieldScanStateServiceImplTest.java \
        src/test/java/com/sunline/dict/sql/FlowFieldDailyScanSchemaContractTest.java
git commit -m "feat: persist flowtrans scan state"
```

---

### Task 4: Store commit/file history idempotently and expose field-day queries

**Files:**
- Create: `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeMeta.java`
- Modify: `src/main/java/com/sunline/dict/entity/FlowFieldChangeLog.java`
- Modify: `src/main/java/com/sunline/dict/service/FlowFieldChangeLogService.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImpl.java`
- Modify: `src/main/java/com/sunline/dict/entity/FlowFieldChangeLog.java`
- Modify: `src/main/java/com/sunline/dict/mapper/FlowFieldChangeLogMapper.java`
- Modify: `src/main/java/com/sunline/dict/mapper/FlowFieldChangeDetailMapper.java`
- Create: `src/main/java/com/sunline/dict/mapper/FlowFieldChangeQueryMapper.java`
- Create: `src/main/resources/mapper/FlowFieldChangeQueryMapper.xml`
- Modify: `src/main/java/com/sunline/dict/dto/FlowFieldChangeDtos.java`
- Modify: `src/main/java/com/sunline/dict/controller/FlowFieldChangeController.java`
- Modify: `src/main/resources/sql/create_flow_field_change_tables.sql`
- Create: `src/main/resources/sql/migrate_flow_field_change_daily_scan.sql`
- Modify: `src/test/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImplTest.java`
- Modify: `src/test/java/com/sunline/dict/controller/FlowFieldChangeControllerTest.java`
- Modify: `src/test/java/com/sunline/dict/sql/FlowFieldChangeSchemaContractTest.java`

**Interfaces:**
- `FlowFieldChangeMeta` fields: `scanRunId`, `changeDate`, project ID/name/path, branch, effective file path, parent SHA, commit SHA/message/author/email/time.
- Produces `HistoryState state(String dedupKey)` where `HistoryState` is `NONE`, `FAILED`, `SUCCESS`.
- Produces `WriteOutcome recordSuccess(FlowFieldChangeMeta meta, FlowFieldChangeSet changeSet)` and `WriteOutcome recordFailure(FlowFieldChangeMeta meta, String safeError)`; outcome contains log ID and whether a row was inserted/upgraded/skipped.
- Produces `Page<FieldChangeRowView> pageFieldChanges(FieldChangeQuery query)`, `FlowFieldChangeHistoryDetail getDetail(long logId)`, and `Page<ScanRunView> pageScanRuns(ScanRunQuery query)`.
- Until Task 6 removes Webhook history capture, retain deprecated `recordSuccess(FlowFieldChangeCaptureMeta, FlowFieldChangeSet)` and `recordFailure(FlowFieldChangeCaptureMeta,String)` overloads solely so the old capture classes compile. They delegate through an isolated legacy adapter and are deleted together with capture in Task 6; the new daily scanner never calls them.

- [ ] **Step 1: Write RED persistence tests for dedup and failed-row upgrade**

```java
@Test
void dedup_key_uses_project_branch_commit_and_effective_path_only() {
    assertEquals(sha256("42|master|abc123|src/T001.flowtrans.xml"), meta.dedupKey());
}

@Test
void successful_retry_upgrades_failed_header_and_replaces_details_atomically() {
    service.recordFailure(meta, "XML 解析失败");
    WriteOutcome outcome = service.recordSuccess(meta, oneAddedField());
    assertEquals(WriteDisposition.UPGRADED, outcome.disposition());
    assertEquals("SUCCESS", logMapper.byDedup(meta.dedupKey()).getCaptureStatus());
    assertEquals(1, detailMapper.byLogId(outcome.logId()).size());
}
```

Also cover: existing SUCCESS is skipped; repeated FAILED updates in place; success inserts all JSON attributes; zero-field ADD/DELETE persists header; modify with no field diff is not passed to writer; rollback removes partial header/detail changes; safe errors reject secrets/URLs/SQL/stack text.

- [ ] **Step 2: Write RED field-row and scan-run query tests**

Mapper/service fixtures must prove one row per detail, LEFT JOIN file-only row, default date is the Shanghai current date, exact project/flow/io/change/status filters, case-sensitive field-ID contains search, inclusive `startDate/endDate`, order `commit_time DESC, log_id DESC, detail_id ASC`, size `1..100`, and JSON strings converted to objects.

Controller tests call the real controller and assert:

```java
Result<Page<FieldChangeRowView>> result = controller.list(
        1, 20, LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19),
        42L, null, null, "TC001", "input", "Acct", "MODIFY", null, "SUCCESS");
assertEquals(200, result.getCode());
```

Also verify `/detail/{logId}` 404, scan-run filters/status, invalid enum/date/page body code 400, and database exceptions return a generic 500 without exception detail.

- [ ] **Step 3: Verify RED**

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeLogServiceImplTest,FlowFieldChangeControllerTest,FlowFieldChangeSchemaContractTest test
```

Expected: tests fail because daily meta, row queries and scan-run APIs are absent.

- [ ] **Step 4: Implement daily history writes**

Replace Webhook meta use with immutable `FlowFieldChangeMeta`. `recordSuccess` selects by dedup key; insert on NONE, return SKIPPED on SUCCESS, and on FAILED update the same header ID after deleting existing details. `recordFailure` inserts or updates only FAILED, never downgrades SUCCESS. Serialize snapshots with Jackson and keep one transaction around header/detail replacement.

- [ ] **Step 5: Implement field-day query mapper and DTOs**

Use XML dynamic SQL with bound parameters and `BINARY d.field_id LIKE CONCAT('%', #{query.fieldId}, '%')`. Define immutable DTOs matching the API design: `FieldChangeQuery`, `FieldChangeRowView`, `FlowFieldChangeHistoryDetail`, `DetailView`, `ScanRunQuery`, `ScanRunView`, and `Page` results. Failed logs and zero-field file ADD/DELETE return a LEFT JOIN row with null detail fields; for file-only rows set response `changeType=fileChangeType`.

- [ ] **Step 6: Implement controller contracts**

Expose only:

```text
GET /api/flow-field-change/list
GET /api/flow-field-change/detail/{logId}
GET /api/flow-field-change/scan-runs
```

Use `LocalDate` parameters and defaults from an injected Shanghai-zone `Clock`; validate values before mapper calls. Preserve `Result<T>` wrapping and safe 400/404/500 messages.

- [ ] **Step 7: Add compatible DDL/migration and run GREEN**

Create-table SQL must match the approved model. Migration adds `scan_run_id`, `change_date`, `project_path`, `parent_sha`, `update_time` and indexes while retaining legacy columns physically. For an existing table, add `scan_run_id` and `change_date` as nullable compatibility columns, backfill only `change_date=DATE(COALESCE(commit_time,create_time))`, and keep old rows out of the daily page with `scan_run_id IS NOT NULL`; all new writes provide non-null daily values. Do not fabricate a run, commit SHA or commit time for legacy Webhook rows. Run:

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeLogServiceImplTest,FlowFieldChangeControllerTest,FlowFieldChangeSchemaContractTest test
```

Expected: all tests pass.

- [ ] **Step 8: Commit persistence and read API slice**

```bash
git add src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeMeta.java \
        src/main/java/com/sunline/dict/entity/FlowFieldChangeLog.java \
        src/main/java/com/sunline/dict/service/FlowFieldChangeLogService.java \
        src/main/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImpl.java \
        src/main/java/com/sunline/dict/mapper/FlowFieldChangeLogMapper.java \
        src/main/java/com/sunline/dict/mapper/FlowFieldChangeDetailMapper.java \
        src/main/java/com/sunline/dict/mapper/FlowFieldChangeQueryMapper.java \
        src/main/resources/mapper/FlowFieldChangeQueryMapper.xml \
        src/main/java/com/sunline/dict/dto/FlowFieldChangeDtos.java \
        src/main/java/com/sunline/dict/controller/FlowFieldChangeController.java \
        src/main/resources/sql/create_flow_field_change_tables.sql \
        src/main/resources/sql/migrate_flow_field_change_daily_scan.sql \
        src/test/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImplTest.java \
        src/test/java/com/sunline/dict/controller/FlowFieldChangeControllerTest.java \
        src/test/java/com/sunline/dict/sql/FlowFieldChangeSchemaContractTest.java
git commit -m "feat: persist daily flowtrans change history"
```

---

### Task 5: Orchestrate every project, commit and flowtrans file

**Files:**
- Create: `src/main/java/com/sunline/dict/service/FlowFieldDailyScanService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/FlowFieldDailyScanServiceImpl.java`
- Create: `src/test/java/com/sunline/dict/service/impl/FlowFieldDailyScanServiceImplTest.java`
- Modify: `src/test/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshotParserTest.java`
- Modify: `src/test/java/com/sunline/dict/service/flowchange/FlowFieldChangeDiffServiceTest.java`

**Interfaces:**
- Consumes provider/history/file/state/log services from Tasks 1–4 plus existing `FlowtransInterfaceSnapshotParser` and `FlowFieldChangeDiffService`.
- Produces `BatchScanResult scanAll(LocalDateTime windowEnd)`; result has project attempted/success/error counts for scheduler logging, not for an HTTP endpoint.
- A project failure is isolated; scanning proceeds to the next configured project.

- [ ] **Step 1: Write RED parser/diff regression tests for the approved interface boundary**

Add literal XML fixtures proving: fields outside input/output are ignored; a field with no `ref` is included; all attributes and case changes are significant; field ID/path/io movement is DELETE+ADD; sibling reorder is empty; flow longname/comment-only change is empty; duplicate identity and malformed XML are deterministic parse failures.

- [ ] **Step 2: Write RED scan orchestration tests**

Use in-memory fakes around the real parser and diff. Required scenarios:

```java
@Test
void records_each_commit_separately_in_stable_order() {
    BatchScanResult result = scanner.scanAll(LocalDateTime.of(2026, 8, 19, 22, 0));
    assertEquals(List.of("commit-a", "commit-b"), historyWriter.recordedCommitShas());
    assertEquals(1, result.successfulProjects());
}

@Test
void rename_becomes_old_delete_and_new_add_with_distinct_dedup_paths() {
    scanner.scanAll(windowEnd);
    assertEquals(List.of("old/T001.flowtrans.xml", "new/T001.flowtrans.xml"),
            historyWriter.recordedPaths());
}
```

Also cover: root commit; ADD/MODIFY/DELETE version refs; empty-file ADD/DELETE; modified file with no field change is skipped; previous SUCCESS skips file downloads; previous FAILED retries and upgrades; XML error records UNKNOWN/FAILED and finishes COMPLETED_WITH_ERRORS; 404/auth/429/timeout/5xx abort only that project as FAILED without cursor; database write failure prevents cursor; two configured projects retain independent outcomes/counters; `(start,end]` comes from claim; commit `change_date` is `committedAt` converted to Asia/Shanghai.

- [ ] **Step 3: Verify RED**

```bash
mvn -DskipTests=false -Dtest=FlowFieldDailyScanServiceImplTest,FlowtransInterfaceSnapshotParserTest,FlowFieldChangeDiffServiceTest test
```

Expected: scanner tests fail because daily orchestration does not exist; parser/diff regression tests expose any old Webhook assumptions.

- [ ] **Step 4: Implement the scanner**

For each configured ID: claim execution; fetch project; list commits from claim window; list work items per commit; skip SUCCESS dedup; fetch before/after according to file change type and first parent; treat all GitLab non-FOUND statuses needed by the work item as project failure; parse snapshots; diff; skip empty MODIFY; write success or deterministic failure; accumulate counters; finish SUCCESS or COMPLETED_WITH_ERRORS. Catch a project-level exception, call `finish(...FAILED...)` when a run was claimed, and continue other IDs. Do not catch `Error`, do not log XML content or GitLab response bodies.

- [ ] **Step 5: Run GREEN and the full flowchange package**

```bash
mvn -DskipTests=false -Dtest='com.sunline.dict.service.flowchange.*Test,com.sunline.dict.service.impl.FlowFieldDailyScanServiceImplTest' test
```

Expected: all tests pass and counters match persisted outcomes.

- [ ] **Step 6: Commit orchestration slice**

```bash
git add src/main/java/com/sunline/dict/service/FlowFieldDailyScanService.java \
        src/main/java/com/sunline/dict/service/impl/FlowFieldDailyScanServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/FlowFieldDailyScanServiceImplTest.java \
        src/test/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshotParserTest.java \
        src/test/java/com/sunline/dict/service/flowchange/FlowFieldChangeDiffServiceTest.java
git commit -m "feat: scan daily flowtrans interface changes"
```

---

### Task 6: Schedule at 22:00 and remove Webhook history writes

**Files:**
- Create: `src/main/java/com/sunline/dict/scheduler/DailyFlowtransChangeScheduler.java`
- Modify: `src/main/java/com/sunline/dict/DictManagerApplication.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/sunline/dict/controller/WebhookController.java`
- Modify: `src/main/java/com/sunline/dict/service/WebhookService.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/WebhookServiceImpl.java`
- Modify: `src/main/java/com/sunline/dict/service/FlowFieldChangeLogService.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImpl.java`
- Delete: `src/main/java/com/sunline/dict/service/FlowFieldChangeCaptureService.java`
- Delete: `src/main/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImpl.java`
- Delete: `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureMeta.java`
- Delete: `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureResult.java`
- Delete: `src/test/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImplTest.java`
- Replace: `src/test/java/com/sunline/dict/controller/WebhookControllerEventUuidTest.java` with `src/test/java/com/sunline/dict/controller/WebhookControllerCurrentStateTest.java`
- Create: `src/test/java/com/sunline/dict/scheduler/DailyFlowtransChangeSchedulerTest.java`

**Interfaces:**
- Scheduler calls `dailyScanService.scanAll(LocalDate.now(clock.withZone(zone)).atTime(22, 0))`.
- `WebhookService.handleGitLabPushEvent(Map<String,Object> payload)` no longer accepts an event UUID.
- GitLab webhook endpoints may accept `X-Gitlab-Event-UUID` for wire compatibility but never forward it to a history writer.

- [ ] **Step 1: Write RED scheduler tests**

Instantiate scheduler with fixed Clock `2026-08-19T14:00:00Z` and assert one call with `2026-08-19T22:00`. Add Spring annotation reflection assertions for exact cron property default and `Asia/Shanghai` zone property default, plus a context test proving the bean is absent when `flow-field-change.scan.enabled=false`.

- [ ] **Step 2: Write RED Webhook decoupling/current-state tests**

Exercise a GitLab master push payload containing a changed flowtrans file and verify current `FlowTran`/`FlowStep`/`FlowFieldDetail` parse operations still happen while no history-capture bean is required. Exercise deletion and verify the user’s existing `deleteBySourceInfo` current-state cleanup remains. Assert the response no longer exposes capture counts or Webhook-history UUID semantics.

- [ ] **Step 3: Verify RED**

```bash
mvn -DskipTests=false -Dtest=DailyFlowtransChangeSchedulerTest,WebhookControllerCurrentStateTest test
```

Expected: scheduler is absent and Webhook still requires capture signatures.

- [ ] **Step 4: Wire scheduling and configuration**

Add `@EnableScheduling` to `DictManagerApplication`. Add only these non-secret defaults under `flow-field-change.scan`: `enabled: true`, `cron: "0 0 22 * * ?"`, `zone: "Asia/Shanghai"`, `page-size: 100`. The scheduler is `@ConditionalOnProperty(...havingValue="true", matchIfMissing=true)` and contains no scanning algorithm.

- [ ] **Step 5: Remove only Webhook history coupling**

Remove capture imports, optional capture field, capture invocation/result response keys, event UUID service parameter and obsolete capture classes. Preserve all current-state parsing and the dirty-worktree `FlowFieldDetailService.deleteBySourceInfo(sourceInfo)` behavior. Do not stage `FlowFieldDetailService.java`, `FlowFieldDetailServiceImpl.java` or `create_flow_field_detail.sql`; the production code must compile against those existing user changes without claiming them in this task.

Delete the deprecated `FlowFieldChangeCaptureMeta` overloads retained in Task 4 from `FlowFieldChangeLogService` and `FlowFieldChangeLogServiceImpl`, and remove legacy-only Java properties `webhookUuid`, `beforeSha`, and `afterSha` from `FlowFieldChangeLog`; after this step no production source references Webhook history types. The compatibility SQL columns remain untouched.

- [ ] **Step 6: Run GREEN and Webhook regression**

```bash
mvn -DskipTests=false -Dtest=DailyFlowtransChangeSchedulerTest,WebhookControllerCurrentStateTest,WebhookServiceImplCapturedContentTest test
```

If the named existing Webhook test class differs, run every existing `Webhook*Test` discovered by `rg --files src/test | rg '/Webhook.*Test.java$'` and record the exact class list in the task report. Expected: scheduler and current-state Webhook tests pass without a history writer.

- [ ] **Step 7: Commit scheduling/decoupling slice explicitly**

```bash
git add src/main/java/com/sunline/dict/scheduler/DailyFlowtransChangeScheduler.java \
        src/main/java/com/sunline/dict/DictManagerApplication.java \
        src/main/resources/application.yml \
        src/main/java/com/sunline/dict/controller/WebhookController.java \
        src/main/java/com/sunline/dict/service/WebhookService.java \
        src/main/java/com/sunline/dict/service/FlowFieldChangeLogService.java \
        src/main/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImpl.java \
        src/main/java/com/sunline/dict/entity/FlowFieldChangeLog.java \
        src/main/java/com/sunline/dict/service/FlowFieldChangeCaptureService.java \
        src/main/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImpl.java \
        src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureMeta.java \
        src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureResult.java \
        src/test/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImplTest.java \
        src/test/java/com/sunline/dict/controller/WebhookControllerEventUuidTest.java \
        src/test/java/com/sunline/dict/controller/WebhookControllerCurrentStateTest.java \
        src/test/java/com/sunline/dict/scheduler/DailyFlowtransChangeSchedulerTest.java
# WebhookServiceImpl already contains unrelated user hunks. Stage only capture-import,
# capture-field, capture-call and capture-response-key removals with interactive patch mode.
git add -p src/main/java/com/sunline/dict/service/impl/WebhookServiceImpl.java
git commit -m "feat: schedule daily flowtrans history scan"
```

Before committing, run `git diff --cached -- src/main/java/com/sunline/dict/service/impl/WebhookServiceImpl.java` and verify the user’s `FlowFieldDetailService` injection/deletion-cleanup hunks are absent from the index. Then run `git diff --cached --name-only` and abort the commit if it includes either protected SQL/config file or either `FlowFieldDetailService*` user file.

---

### Task 7: Replace the page with a field-day view and scan status

**Files:**
- Modify: `src/main/resources/static/flow-field-change-history.html`
- Modify: `src/test/java/com/sunline/dict/frontend/FlowFieldChangeFrontendContractTest.java`
- Verify unchanged: `src/main/resources/static/index.html`
- Verify unchanged: `src/main/resources/sql/create_menu_table.sql`
- Verify unchanged: `src/main/resources/sql/add_flow_field_change_history_menu.sql`

**Interfaces:**
- Consumes only the three GET endpoints from Task 4.
- Default list and scan-run dates are today in the browser; reset restores today and page 1.
- No POST/PUT/DELETE request is emitted.

- [ ] **Step 1: Load the required frontend skill before editing**

Read `/Users/wangshanhe/.codex/skills/frontend-skill/SKILL.md` completely and apply it within the established iframe/admin visual language. Record in the task report which layout and responsive decisions it changed.

- [ ] **Step 2: Write RED behavior contract tests**

Use resource parsing plus the existing Node harness to execute component methods. Assert:

- initial `/list` and `/scan-runs` requests use the same today `startDate/endDate`;
- filters include project/project ID, file, flow, input/output, field, ADD/MODIFY/DELETE, author and SUCCESS/FAILED;
- list rows render project, file, field identity/path, change type, changed attributes, SHA, author/message/time;
- zero-field file row renders `—`; failed row renders safe error and cannot open details;
- detail responses are generation-guarded, modal focus is trapped/restored, and background is inert;
- scan status renders RUNNING/SUCCESS/COMPLETED_WITH_ERRORS/FAILED, window, counters and error summary;
- pagination/reset/loading/empty/error states work independently for history and scan runs;
- templates use Vue interpolation, never `v-html`, and scripts contain no mutation HTTP methods;
- width 390px has no page-level horizontal overflow while wide tables use an internal scroll region.

- [ ] **Step 3: Verify RED**

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeFrontendContractTest test
```

Expected: old Webhook/file-history page contract fails field-row and scan-status expectations.

- [ ] **Step 4: Implement the field-day page**

Keep Vue 3/axios local assets. Provide a compact page header, filter form, history table, scan-status section and accessible detail drawer. Use semantic labels/status chips, visible focus, `aria-live` for loading/errors, request-generation guards for both list/detail, second-safe date-only API parameters, and `Intl.DateTimeFormat` for display. Render JSON changed attributes as escaped key/value rows.

- [ ] **Step 5: Run GREEN and verify menu shell contracts**

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeFrontendContractTest test
rg -n "flow-field-change-history|交易接口变动历史" \
  src/main/resources/static/index.html \
  src/main/resources/sql/create_menu_table.sql \
  src/main/resources/sql/add_flow_field_change_history_menu.sql
```

Expected: tests pass and existing menu key/title/iframe wiring remains complete, so those unchanged files are not staged.

- [ ] **Step 6: Commit the page slice**

```bash
git add src/main/resources/static/flow-field-change-history.html \
        src/test/java/com/sunline/dict/frontend/FlowFieldChangeFrontendContractTest.java
git commit -m "feat: show daily flowtrans field changes"
```

---

### Task 8: Full verification, executable packaging and design freshness

**Files:**
- Modify only if implementation differs: `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易接口变动历史-系统设计.md`
- Modify only if implementation differs: `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易接口变动历史-数据模型.md`
- Modify only if implementation differs: `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易接口变动历史-API接口.md`
- Modify: `/Users/java/obsidian/01 Engineering/sunline-benchmark/_overview.md`
- Modify: `/Users/java/obsidian/index.md`
- Modify: `/Users/java/obsidian/log.md`
- Create: `src/test/java/com/sunline/dict/integration/FlowFieldDailyScanArchitectureTest.java`

**Interfaces:**
- Verifies the complete system rather than adding a new production API.
- Obsidian remains the single source of truth; no duplicate design spec is added to the repository.

- [ ] **Step 1: Read verification and review skills before claiming completion**

Read `superpowers:verification-before-completion` and `superpowers:requesting-code-review` completely. Build the final review package from the plan’s merge base, not `HEAD~1`.

- [ ] **Step 2: Write a failing cross-component architecture test**

The Spring context test must use test properties with scanning disabled and a fake GitLab base, then assert: scheduler bean/property gating, no Webhook dependency on capture service, all three read endpoints mapped, scan services present, and no write mappings under `/api/flow-field-change`. It should fail before any missing wiring is corrected.

- [ ] **Step 3: Run focused integration RED/GREEN**

```bash
mvn -DskipTests=false -Dtest=FlowFieldDailyScanArchitectureTest test
```

Expected RED: any missing wiring is named. Make only the smallest production correction through a failing test/fix cycle, then rerun until PASS.

- [ ] **Step 4: Run the complete automated suite and package**

```bash
mvn -DskipTests=false test
mvn -DskipTests=false package
```

Expected: both commands exit 0; the package command produces the Spring Boot JAR in `target/`.

- [ ] **Step 5: Run static safety and scope checks**

```bash
rg -n "FlowFieldChangeCapture|webhookUuid|beforeSha|afterSha" src/main/java src/test/java
rg -n "PRIVATE-TOKEN|Authorization|access-token|response\.body" \
  src/main/java/com/sunline/dict/service/impl/GitLabApiClientImpl.java \
  src/main/java/com/sunline/dict/service/impl/FlowFieldDailyScanServiceImpl.java
git status --short
git diff --check
```

Expected: obsolete Webhook capture symbols have no active source use; security matches are reviewed and contain no logging/returned secrets; `git diff --check` is clean; protected user files remain unstaged and unchanged by this plan.

- [ ] **Step 6: Perform browser acceptance**

Start the packaged app with a non-production profile, scanning disabled, and test-safe database configuration. In the in-app browser verify desktop and 390px mobile: default date, filters, field rows, empty/error/loading, detail keyboard behavior and scan status. Save screenshots/report only inside this plan’s ignored SDD workspace, not the repository.

- [ ] **Step 7: Update Obsidian implementation status**

Change system design status from “待实施” to the actual completed state, record real class/table/API names, update `_overview.md`, ensure `index.md` points to the design, and append one `2026-08-19 [UPDATE]` log line. If implementation exactly matches the approved design, only status/overview/index/log change; do not rewrite unchanged decisions.

- [ ] **Step 8: Commit final repository verification slice and Obsidian freshness separately**

Repository:

```bash
git add src/test/java/com/sunline/dict/integration/FlowFieldDailyScanArchitectureTest.java
git commit -m "test: verify daily flowtrans scan architecture"
```

Obsidian repository, with explicit paths only:

```bash
git -C /Users/java/obsidian add \
  '01 Engineering/sunline-benchmark/交易接口变动历史-系统设计.md' \
  '01 Engineering/sunline-benchmark/交易接口变动历史-数据模型.md' \
  '01 Engineering/sunline-benchmark/交易接口变动历史-API接口.md' \
  '01 Engineering/sunline-benchmark/_overview.md' index.md log.md
git -C /Users/java/obsidian commit -m "docs: mark daily flowtrans scan implemented"
```

Before the Obsidian commit, inspect `git -C /Users/java/obsidian diff --cached --name-only` and unstage any unrelated pre-existing file. If a listed design file is unchanged, omit it from `git add` rather than forcing a no-op.

---

## Final Review Gate

After all eight tasks have task-scoped spec and quality approval:

1. Generate one full review package from the plan start commit to HEAD.
2. Dispatch the final reviewer on the most capable available model with the approved three design files, plan, ledger rulings/deferred minors and full diff package.
3. If findings exist, dispatch one fix agent for the complete finding list, run covering tests, then one scoped re-review.
4. Run `mvn -DskipTests=false test`, `mvn -DskipTests=false package`, `git diff --check`, and inspect `git status --short` again immediately before reporting completion.
5. Do not merge, push, publish, delete user files, or include protected dirty-worktree files without explicit user authority.
