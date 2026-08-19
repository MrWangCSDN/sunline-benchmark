# 交易接口变动历史 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 仅在 GitLab master Push Webhook 中，按 `before/after` commit SHA 捕获每个 `.flowtrans.xml` 的 input/output 最终差异，可靠落库并通过“交易接口变动历史”菜单页面查询。

**Architecture:** 新增纯解析器与纯 diff 服务，以 GitLab 历史版本文件为事实源；Capture Service 负责每个 Webhook/文件的聚合、最后 commit 元信息、幂等和失败隔离；Persistence Service 负责主明细事务写入及只读查询；现有 Webhook 当前状态解析继续保留。页面沿用项目 Vue 3 CDN + axios + iframe 菜单模式。

**Tech Stack:** Java 17、Spring Boot 3.1.x、MyBatis-Plus、Jackson、JDK `HttpClient`、JUnit 5、Mockito、HTML/CSS/ES6、Vue 3 CDN、axios CDN、MariaDB/MySQL。

**Spec:** `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易接口变动历史-系统设计.md`（同时遵循同目录的数据模型和 API 接口文档）

## Global Constraints

- 只处理 GitLab `refs/heads/master` Push Webhook；GitHub 和其他分支不记录。
- 一个 Webhook 中每个 `.flowtrans.xml` 最多形成一条汇总历史；同文件使用最后一次触及它的 commit 元信息。
- 最终差异必须来自 GitLab `before/after` SHA 对应的原始 XML，不以 `flow_field_detail` 作为历史事实源。
- 解析 input/output 下所有具有非空 `id` 的 `<field>`，包括无 `ref` 字段和任意深度嵌套字段。
- 字段身份固定为 `(ioType, fieldPath, fieldId)`；排序变化不算差异；路径或 ID 变化算删除加新增。
- 同一字段多个属性变化只保存一条 `MODIFY` 明细；主表计数按字段计，不按属性计。
- 整文件新增/删除即使没有字段也必须保存主历史。
- GitLab 请求目标只允许来自受信任配置 `git.gitlab.url`；不得使用 payload 的 `project.web_url` 作为 Token 请求目标。
- 失败必须留痕但不得保存 Token、认证头、SQL 或堆栈到 `error_message`。
- 历史页面和 API 只读；不增加修改、删除或手工补录接口。
- 不新增 Maven 或前端依赖。
- 当前工作区已有相关未提交原型。允许重写 `FlowFieldChange*` 新文件及 `WebhookServiceImpl` 中字段 diff 原型片段；不得覆盖无关用户改动。
- 不修改或暂存 `clash-config.yaml`；不清理 `create_flow_field_detail.sql` 中现有用户内容；提交时逐文件显式 `git add`。

---

## File Map

**New production files**

- `src/main/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshot.java`
- `src/main/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshotParser.java`
- `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeSet.java`
- `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeDiffService.java`
- `src/main/java/com/sunline/dict/service/flowchange/GitLabFileVersionService.java`
- `src/main/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImpl.java`
- `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureMeta.java`
- `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureResult.java`
- `src/main/java/com/sunline/dict/service/FlowFieldChangeCaptureService.java`
- `src/main/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImpl.java`
- `src/main/java/com/sunline/dict/dto/FlowFieldChangeDtos.java`
- `src/main/resources/static/flow-field-change-history.html`
- `src/main/resources/sql/add_flow_field_change_history_menu.sql`

**Existing prototype files to rewrite/complete**

- `src/main/java/com/sunline/dict/entity/FlowFieldChangeLog.java`
- `src/main/java/com/sunline/dict/entity/FlowFieldChangeDetail.java`
- `src/main/java/com/sunline/dict/mapper/FlowFieldChangeLogMapper.java`
- `src/main/java/com/sunline/dict/mapper/FlowFieldChangeDetailMapper.java`
- `src/main/java/com/sunline/dict/service/FlowFieldChangeLogService.java`
- `src/main/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImpl.java`
- `src/main/java/com/sunline/dict/controller/FlowFieldChangeController.java`
- `src/main/resources/sql/create_flow_field_change_tables.sql`

**Existing production files to modify**

- `src/main/java/com/sunline/dict/controller/WebhookController.java`
- `src/main/java/com/sunline/dict/service/WebhookService.java`
- `src/main/java/com/sunline/dict/service/impl/WebhookServiceImpl.java`
- `src/main/resources/static/index.html`
- `src/main/resources/sql/create_menu_table.sql`

**New tests**

- `src/test/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshotParserTest.java`
- `src/test/java/com/sunline/dict/service/flowchange/FlowFieldChangeDiffServiceTest.java`
- `src/test/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImplTest.java`
- `src/test/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImplTest.java`
- `src/test/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImplTest.java`
- `src/test/java/com/sunline/dict/controller/FlowFieldChangeControllerTest.java`
- `src/test/java/com/sunline/dict/controller/WebhookControllerEventUuidTest.java`
- `src/test/java/com/sunline/dict/frontend/FlowFieldChangeFrontendContractTest.java`
- `src/test/java/com/sunline/dict/sql/FlowFieldChangeSchemaContractTest.java`

---

### Task 1: Parse Complete flowtrans Interface Snapshots

**Files:**
- Create: `src/test/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshotParserTest.java`
- Create: `src/main/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshot.java`
- Create: `src/main/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshotParser.java`

**Interfaces:**
- Produces `FlowtransInterfaceSnapshot parse(String xmlContent)`.
- Produces nested records `FieldIdentity(String ioType, String fieldPath, String fieldId)` and `FieldSnapshot(FieldIdentity identity, SortedMap<String,String> attributes)`.

- [ ] **Step 1: Write failing parser tests**

```java
@Test
void parses_input_output_nested_fields_without_requiring_ref() {
    String xml = """
        <flowtran><interface id="TC045" longname="双公定期手工转存">
          <input>
            <field id="top" type="T1" required="true"/>
            <fields id="accounts"><field id="amount" fixed="2"/></fields>
          </input>
          <output><field id="result" ref="MDict.R.result"/></output>
        </interface></flowtran>
        """;
    FlowtransInterfaceSnapshot snapshot = parser.parse(xml);
    assertEquals("TC045", snapshot.flowId());
    assertTrue(snapshot.fields().containsKey(
            new FieldIdentity("input", "/fields[accounts]", "amount")));
    assertEquals("2", snapshot.fields().get(
            new FieldIdentity("input", "/fields[accounts]", "amount"))
            .attributes().get("fixed"));
}
```

Also test: every attribute is captured case-sensitively; `<fields>` itself is skipped; empty input/output; container without id uses stable sorted-attribute digest; sibling order does not change identities; duplicate identity throws; DOCTYPE/external entity is rejected.

- [ ] **Step 2: Run test and verify RED**

```bash
mvn -DskipTests=false -Dtest=FlowtransInterfaceSnapshotParserTest test
```

Expected: compilation fails because parser/snapshot types do not exist.

- [ ] **Step 3: Implement immutable records**

```java
public record FlowtransInterfaceSnapshot(
        String flowId, String flowLongname,
        Map<FieldIdentity, FieldSnapshot> fields) {
    public FlowtransInterfaceSnapshot {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
    public record FieldIdentity(String ioType, String fieldPath, String fieldId) {}
    public record FieldSnapshot(FieldIdentity identity,
                                SortedMap<String, String> attributes) {
        public FieldSnapshot {
            attributes = Collections.unmodifiableSortedMap(new TreeMap<>(attributes));
        }
    }
}
```

- [ ] **Step 4: Implement secure parsing**

Disable DOCTYPE, external general/parameter entities, XInclude and entity expansion. Walk only the interface's input/output trees. Use `/` for top-level fields, `fields[id]` for identified containers, and `fields[#<first12Sha256>]` for anonymous containers. Require nonblank field id, copy all XML attributes, and fail on duplicate identity.

- [ ] **Step 5: Run parser and existing extractor tests**

```bash
mvn -DskipTests=false -Dtest=FlowtransInterfaceSnapshotParserTest,FlowFieldExtractorTest test
```

Expected: all pass; existing current-state extractor behavior is unchanged.

- [ ] **Step 6: Commit parser slice**

```bash
git add src/main/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshot.java \
        src/main/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshotParser.java \
        src/test/java/com/sunline/dict/service/flowchange/FlowtransInterfaceSnapshotParserTest.java
git commit -m "feat: parse flowtrans interface snapshots"
```

---

### Task 2: Calculate File and Field Differences

**Files:**
- Create: `src/test/java/com/sunline/dict/service/flowchange/FlowFieldChangeDiffServiceTest.java`
- Create: `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeSet.java`
- Create: `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeDiffService.java`

**Interfaces:**
- `Optional<FlowFieldChangeSet> diff(FlowtransInterfaceSnapshot before, FlowtransInterfaceSnapshot after)`; either snapshot may be null.

- [ ] **Step 1: Write failing field and file diff tests**

```java
@Test
void one_field_with_two_changed_attributes_counts_as_one_modify() {
    FlowtransInterfaceSnapshot before = snapshot("TC045", field(
            "input", "/", "amount", attrs("type", "T1", "required", "false")));
    FlowtransInterfaceSnapshot after = snapshot("TC045", field(
            "input", "/", "amount", attrs("type", "T2", "required", "true")));
    FlowFieldChangeSet change = service.diff(before, after).orElseThrow();
    assertEquals(1, change.modifyCount());
    assertEquals(2, change.details().get(0).changedAttributes().size());
}

@Test
void empty_file_add_and_delete_still_produce_history() {
    FlowtransInterfaceSnapshot empty = snapshot("TC000");
    assertEquals(FileChangeType.ADD, service.diff(null, empty).orElseThrow().fileChangeType());
    assertEquals(FileChangeType.DELETE, service.diff(empty, null).orElseThrow().fileChangeType());
}
```

Also test field ADD/DELETE; input/output same ID isolation; path/ID movement as REMOVE+ADD; attribute add/remove uses null; order-only changes return empty; case changes are significant.

- [ ] **Step 2: Run test and verify RED**

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeDiffServiceTest test
```

Expected: compilation fails because diff types do not exist.

- [ ] **Step 3: Implement change records**

```java
public record FlowFieldChangeSet(
        FileChangeType fileChangeType, String flowId, String flowLongname,
        List<FieldChange> details,
        int addCount, int modifyCount, int removeCount,
        int inputChangeCount, int outputChangeCount) {
    public enum FileChangeType { ADD, MODIFY, DELETE }
    public enum FieldChangeType { ADD, MODIFY, DELETE }
    public record ValueChange(String oldValue, String newValue) {}
    public record FieldChange(
            FieldChangeType changeType, FieldIdentity identity,
            SortedMap<String,String> oldSnapshot,
            SortedMap<String,String> newSnapshot,
            SortedMap<String,ValueChange> changedAttributes) {}
}
```

- [ ] **Step 4: Implement union-key diff**

Compare the union of identities. For matching fields compare the sorted union of attribute names except `id`; create exactly one MODIFY detail containing every changed property. Stable-sort details by ioType/path/id/type and derive counts from details.

- [ ] **Step 5: Run diff/parser tests and commit**

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeDiffServiceTest,FlowtransInterfaceSnapshotParserTest test
git add src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeSet.java \
        src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeDiffService.java \
        src/test/java/com/sunline/dict/service/flowchange/FlowFieldChangeDiffServiceTest.java
git commit -m "feat: calculate flowtrans interface diffs"
```

---

### Task 3: Fetch Trusted GitLab File Versions

**Files:**
- Create: `src/test/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImplTest.java`
- Create: `src/main/java/com/sunline/dict/service/flowchange/GitLabFileVersionService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImpl.java`

**Interfaces:**
- `FileVersionResult fetch(long projectId, String pathWithNamespace, String filePath, String ref)`.
- Status is `FOUND`, `NOT_FOUND`, or `FAILED`; FOUND carries content and FAILED carries only a sanitized error.

- [ ] **Step 1: Write failing real-HTTP tests**

Use JDK `com.sun.net.httpserver.HttpServer` on `127.0.0.1`:

```java
@Test
void fetches_encoded_path_at_commit_sha_and_sends_private_token() throws Exception {
    AtomicReference<String> requestUri = new AtomicReference<>();
    AtomicReference<String> token = new AtomicReference<>();
    server.createContext("/api/v4/projects/123/repository/files/", exchange -> {
        requestUri.set(exchange.getRequestURI().toString());
        token.set(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
        respond(exchange, 200, "<flowtran/>");
    });
    GitLabFileVersionServiceImpl service = new GitLabFileVersionServiceImpl(
            baseUrl(), "secret-token", HttpClient.newHttpClient());
    FileVersionResult result = service.fetch(
            123L, "group/project", "src/a b/TC045.flowtrans.xml", "abc123");
    assertEquals(Status.FOUND, result.status());
    assertEquals("secret-token", token.get());
    assertTrue(requestUri.get().contains("src%2Fa%20b%2FTC045.flowtrans.xml"));
    assertTrue(requestUri.get().endsWith("ref=abc123"));
}
```

Also test 404→NOT_FOUND; 401/403/500→FAILED; errors never contain token; host always equals constructor/configured base URL; timeout is sanitized.

- [ ] **Step 2: Run test and verify RED**

```bash
mvn -DskipTests=false -Dtest=GitLabFileVersionServiceImplTest test
```

Expected: compilation fails because client types do not exist.

- [ ] **Step 3: Implement result contract and HttpClient adapter**

```java
public interface GitLabFileVersionService {
    FileVersionResult fetch(long projectId, String pathWithNamespace,
                            String filePath, String ref);
    enum Status { FOUND, NOT_FOUND, FAILED }
    record FileVersionResult(Status status, String content, String errorMessage) {}
}
```

Production constructor reads `${git.gitlab.url}` and `${gitlab.access-token:}`; package-private test constructor accepts base URL, token and `HttpClient`. Normalize configured base URL once, encode file/ref UTF-8, set 10-second connect and 30-second request timeout, and never accept a payload host.

- [ ] **Step 4: Run tests and commit**

```bash
mvn -DskipTests=false -Dtest=GitLabFileVersionServiceImplTest test
git add src/main/java/com/sunline/dict/service/flowchange/GitLabFileVersionService.java \
        src/main/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImplTest.java
git commit -m "feat: fetch trusted GitLab file versions"
```

---

### Task 4: Persist History and Serve Read APIs

**Files:**
- Create: `src/test/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImplTest.java`
- Create: `src/test/java/com/sunline/dict/controller/FlowFieldChangeControllerTest.java`
- Create: `src/test/java/com/sunline/dict/sql/FlowFieldChangeSchemaContractTest.java`
- Create: `src/main/java/com/sunline/dict/dto/FlowFieldChangeDtos.java`
- Create: `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureMeta.java`
- Rewrite: `src/main/java/com/sunline/dict/entity/FlowFieldChangeLog.java`
- Rewrite: `src/main/java/com/sunline/dict/entity/FlowFieldChangeDetail.java`
- Keep/complete: both `FlowFieldChange*Mapper.java`
- Rewrite: `src/main/java/com/sunline/dict/service/FlowFieldChangeLogService.java`
- Rewrite: `src/main/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImpl.java`
- Rewrite: `src/main/java/com/sunline/dict/controller/FlowFieldChangeController.java`
- Rewrite: `src/main/resources/sql/create_flow_field_change_tables.sql`

**Interfaces:**
- `boolean existsByDedupKey(String dedupKey)`.
- `FlowFieldChangeLog recordSuccess(FlowFieldChangeCaptureMeta meta, FlowFieldChangeSet changeSet)`.
- `FlowFieldChangeLog recordFailure(FlowFieldChangeCaptureMeta meta, String errorMessage)`.
- `Page<FlowFieldChangeLog> pageLogs(FlowFieldChangeQuery query)`.
- `FlowFieldChangeHistoryDetail getDetail(long logId)`.

- [ ] **Step 1: Write failing persistence tests**

```java
@Test
void record_success_inserts_one_header_and_one_row_per_changed_field() {
    when(logMapper.insert(any())).thenAnswer(invocation -> {
        FlowFieldChangeLog row = invocation.getArgument(0);
        row.setId(88L);
        return 1;
    });
    FlowFieldChangeLog saved = service.recordSuccess(meta(), changeSetWithTwoDetails());
    assertEquals(88L, saved.getId());
    assertEquals(2, saved.getModifyCount());
    verify(detailMapper, times(2)).insert(detailCaptor.capture());
    assertTrue(detailCaptor.getAllValues().stream()
            .allMatch(row -> row.getLogId().equals(88L)));
}
```

Also test JSON round-trip to DTO Maps; failure writes UNKNOWN/FAILED with zero counts and truncated safe error; dedup lookup; detail not found; list filters/order.

- [ ] **Step 2: Write failing Controller tests**

```java
@Test
void rejects_end_time_before_start_time() {
    Result<Page<FlowFieldChangeLog>> result = controller.list(
            1, 20, null, null, null, null, null,
            LocalDateTime.parse("2026-08-20T00:00:00"),
            LocalDateTime.parse("2026-08-19T00:00:00"));
    assertEquals(400, result.getCode());
}
```

Also test valid filters reach the service, detail returns decoded JSON objects, missing ID maps to body code 404, and no stack/SQL is exposed.

- [ ] **Step 3: Write failing SQL contract test and run RED**

Read the SQL resource and assert both tables plus `dedup_key`, unique key, statuses, error, before/after SHA, five counts, field path and three JSON text columns.

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeLogServiceImplTest,FlowFieldChangeControllerTest,FlowFieldChangeSchemaContractTest test
```

Expected: prototype model/schema fails compilation or assertions.

- [ ] **Step 4: Rewrite entities and DTOs**

`FlowFieldChangeLog` must match the approved DDL: dedup/event/project/branch/file/flow/fileChangeType/captureStatus/error/before/after/commit metadata/five counts/createTime. `FlowFieldChangeDetail` stores snapshots and changed attributes as JSON strings.

Create the persistence input before the service that consumes it:

```java
public record FlowFieldChangeCaptureMeta(
        String dedupKey, String webhookUuid, long projectId, String projectName,
        String branch, String filePath, String beforeSha, String afterSha,
        String commitSha, String commitMessage, String commitAuthor,
        String commitEmail, LocalDateTime commitTime) {}
```

```java
public final class FlowFieldChangeDtos {
    public record FlowFieldChangeQuery(
            int current, int size, String flowId, String filePath,
            String commitAuthor, String fileChangeType, String captureStatus,
            LocalDateTime startTime, LocalDateTime endTime) {}
    public record DetailView(
            Long id, String ioType, String fieldPath, String fieldId,
            String changeType, Map<String,String> oldSnapshot,
            Map<String,String> newSnapshot,
            Map<String,ValueChange> changedAttributes) {}
    public record FlowFieldChangeHistoryDetail(
            FlowFieldChangeLog log, List<DetailView> details) {}
}
```

- [ ] **Step 5: Implement persistence, query and Controller**

Use `@Transactional(rollbackFor = Exception.class)` for success/failure insertion. Serialize sorted Maps with injected `ObjectMapper`; insert header, require generated ID, then insert one detail per changed field. Query validates page size `1..100`, enum values and time range. Detail loads header then ordered detail rows and parses JSON. Controller exposes only GET list/detail and maps validation/not-found to body code 400/404.

- [ ] **Step 6: Rewrite DDL and run GREEN**

Use exact MariaDB/MySQL definitions from the approved data model. Do not append shell/curl text.

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeLogServiceImplTest,FlowFieldChangeControllerTest,FlowFieldChangeSchemaContractTest test
```

Expected: all pass.

- [ ] **Step 7: Commit persistence slice explicitly**

```bash
git add src/main/java/com/sunline/dict/dto/FlowFieldChangeDtos.java \
        src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureMeta.java \
        src/main/java/com/sunline/dict/entity/FlowFieldChangeLog.java \
        src/main/java/com/sunline/dict/entity/FlowFieldChangeDetail.java \
        src/main/java/com/sunline/dict/mapper/FlowFieldChangeLogMapper.java \
        src/main/java/com/sunline/dict/mapper/FlowFieldChangeDetailMapper.java \
        src/main/java/com/sunline/dict/service/FlowFieldChangeLogService.java \
        src/main/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImpl.java \
        src/main/java/com/sunline/dict/controller/FlowFieldChangeController.java \
        src/main/resources/sql/create_flow_field_change_tables.sql \
        src/test/java/com/sunline/dict/service/impl/FlowFieldChangeLogServiceImplTest.java \
        src/test/java/com/sunline/dict/controller/FlowFieldChangeControllerTest.java \
        src/test/java/com/sunline/dict/sql/FlowFieldChangeSchemaContractTest.java
git commit -m "feat: persist flowtrans interface history"
```

---

### Task 5: Capture One Aggregate History per Webhook and File

**Files:**
- Create: `src/test/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImplTest.java`
- Create: `src/test/java/com/sunline/dict/controller/WebhookControllerEventUuidTest.java`
- Create: `src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureResult.java`
- Create: `src/main/java/com/sunline/dict/service/FlowFieldChangeCaptureService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImpl.java`
- Modify: `src/main/java/com/sunline/dict/controller/WebhookController.java:74-145`
- Modify: `src/main/java/com/sunline/dict/service/WebhookService.java:15-23`
- Modify: `src/main/java/com/sunline/dict/service/impl/WebhookServiceImpl.java:442-788`

**Interfaces:**
- `FlowFieldChangeCaptureResult capture(Map<String,Object> payload, String eventUuid)`.
- Result carries `afterContents`, success/failure/skipped counts.
- `WebhookService.handleGitLabPushEvent(payload, eventUuid)`.

- [ ] **Step 1: Write failing aggregation tests**

```java
@Test
void same_file_in_multiple_commits_creates_one_history_with_last_commit_metadata() {
    Map<String,Object> payload = gitLabPush("refs/heads/master", "oldSha", "newSha",
            commit("c1", "first", List.of("a/TC045.flowtrans.xml")),
            commit("c2", "last", List.of("a/TC045.flowtrans.xml")));
    when(fileService.fetch(anyLong(), anyString(), anyString(), eq("oldSha")))
            .thenReturn(found(oldXml()));
    when(fileService.fetch(anyLong(), anyString(), anyString(), eq("newSha")))
            .thenReturn(found(newXml()));
    service.capture(payload, "event-1");
    verify(logService, times(1)).recordSuccess(metaCaptor.capture(), any());
    assertEquals("c2", metaCaptor.getValue().commitSha());
    assertEquals("last", metaCaptor.getValue().commitMessage());
}
```

Also test: non-master no fetch/write; non-flowtrans ignored; file add/delete; no interface diff no success record; failure records and continues; existing dedup skips fetch; after content returned for current parser; author/email/message/time and before/after metadata.

- [ ] **Step 2: Write failing UUID forwarding test and run RED**

```java
@Test
void forwards_gitlab_event_uuid_to_service() {
    controller.handleGitLabWebhook(payload(), "Push Hook", "event-uuid-1");
    verify(webhookService).handleGitLabPushEvent(payload(), "event-uuid-1");
}
```

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeCaptureServiceImplTest,WebhookControllerEventUuidTest test
```

Expected: capture contracts and Controller parameter do not exist.

- [ ] **Step 3: Implement result record and Capture Service**

```java
public record FlowFieldChangeCaptureResult(
        Map<String,String> afterContents,
        int successCount, int failedCount, int skippedCount) {}
```

Guard master before fetch. Collect `commits[].added/modified/removed` into a LinkedHashMap where later occurrences replace commit metadata. Compute dedup SHA-256 from project/event identity/path. Treat all-zero SHA as absent. Fetch/parse/diff each file inside an error-isolated loop; cache FOUND after content; call recordFailure on network/XML errors; do not record unchanged interface snapshots.

- [ ] **Step 4: Wire GitLab Controller and Webhook Service**

Read optional `X-Gitlab-Event-UUID` in both `/gitlab` and generic `/git` endpoints. Change only GitLab service signature. In `WebhookServiceImpl`, invoke Capture Service after existing master and commits guards. Remove the prototype DB before/after diff calls. Reuse `captureResult.afterContents().get(filePath)` for flowtrans additions/modifications and fallback to existing download only when absent. GitHub behavior remains unchanged.

- [ ] **Step 5: Run GREEN/context tests and commit**

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeCaptureServiceImplTest,WebhookControllerEventUuidTest,DictManagerApplicationTests test
git add src/main/java/com/sunline/dict/service/flowchange/FlowFieldChangeCaptureResult.java \
        src/main/java/com/sunline/dict/service/FlowFieldChangeCaptureService.java \
        src/main/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImpl.java \
        src/main/java/com/sunline/dict/controller/WebhookController.java \
        src/main/java/com/sunline/dict/service/WebhookService.java \
        src/main/java/com/sunline/dict/service/impl/WebhookServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImplTest.java \
        src/test/java/com/sunline/dict/controller/WebhookControllerEventUuidTest.java
git commit -m "feat: capture GitLab flowtrans webhook history"
```

---

### Task 6: Add the Read-Only History Page and Menu

**Files:**
- Create: `src/test/java/com/sunline/dict/frontend/FlowFieldChangeFrontendContractTest.java`
- Create: `src/main/resources/static/flow-field-change-history.html`
- Create: `src/main/resources/sql/add_flow_field_change_history_menu.sql`
- Modify: `src/main/resources/static/index.html:903-936,1053-1085,2128-2164`
- Modify: `src/main/resources/sql/create_menu_table.sql:24-58`

**Interfaces:**
- Menu code `flow-field-change-history`.
- Page `/flow-field-change-history.html`.
- APIs `/api/flow-field-change/list` and `/api/flow-field-change/detail/{id}`.

- [ ] **Step 1: Write failing frontend contract tests**

```java
@Test
void index_wires_history_menu_permission_iframe_and_title() throws Exception {
    String index = resource("static/index.html");
    assertTrue(index.contains("hasMenuPermission('flow-field-change-history')"));
    assertTrue(index.contains("switchView('flow-field-change-history')"));
    assertTrue(index.contains("src=\"/flow-field-change-history.html\""));
    assertTrue(index.contains("'flow-field-change-history': '📜 交易接口变动历史'"));
}

@Test
void page_exposes_filters_read_api_and_input_output_details() throws Exception {
    String page = resource("static/flow-field-change-history.html");
    assertTrue(page.contains("/api/flow-field-change/list"));
    assertTrue(page.contains("/api/flow-field-change/detail/"));
    assertTrue(page.contains("交易码"));
    assertTrue(page.contains("提交人"));
    assertTrue(page.contains("input"));
    assertTrue(page.contains("output"));
    assertFalse(page.contains("删除历史"));
}
```

Also assert both menu SQL files contain code/name/`dict-management`/enabled status/idempotent update.

- [ ] **Step 2: Run test and verify RED**

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeFrontendContractTest test
```

Expected: page and menu wiring do not exist.

- [ ] **Step 3: Implement standalone Vue page**

```javascript
data() {
  return {
    filters: {
      flowId: '', filePath: '', commitAuthor: '',
      fileChangeType: '', captureStatus: '', startTime: '', endTime: ''
    },
    current: 1, size: 20, total: 0, records: [],
    loading: false, error: '',
    detailOpen: false, detailLoading: false, detail: null,
    activeIo: 'input'
  };
}
```

Behavior: mounted loads page 1; query sends only nonblank params; reset clears filters/current; pagination stays in range; detail defaults to input then output; failed rows show error without fake details; MODIFY shows changed attribute old/new rows; ADD/DELETE shows complete new/old snapshot; badges use green/yellow/red plus visible text; include loading/empty/error states, semantic buttons and Escape-to-close.

- [ ] **Step 4: Wire parent shell and SQL**

Add permission to the `dict-management` group condition, menu item after existing `change-history`, iframe and title. Create idempotent existing-environment SQL and add the row to fresh-install `create_menu_table.sql`:

```sql
INSERT INTO sys_menu
    (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status,
     create_time, update_time)
SELECT 'flow-field-change-history', '交易接口变动历史', id, 2, '📜', 5, 1,
       NOW(), NOW()
FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE
    menu_name = '交易接口变动历史',
    parent_id = VALUES(parent_id),
    icon = '📜', sort_order = 5, status = 1, update_time = NOW();
```

- [ ] **Step 5: Run frontend test and browser QA**

```bash
mvn -DskipTests=false -Dtest=FlowFieldChangeFrontendContractTest test
```

Then use browser test data/interception to verify 1440×900 and 390×844 layouts, filters, list, input/output details, three change types, failed row, keyboard access and Escape close. Capture screenshots for review.

- [ ] **Step 6: Commit UI slice**

```bash
git add src/main/resources/static/flow-field-change-history.html \
        src/main/resources/static/index.html \
        src/main/resources/sql/add_flow_field_change_history_menu.sql \
        src/main/resources/sql/create_menu_table.sql \
        src/test/java/com/sunline/dict/frontend/FlowFieldChangeFrontendContractTest.java
git commit -m "feat: add flowtrans interface history page"
```

---

### Task 7: Full Regression, Security Checks, and Design Freshness

**Files:**
- Update after verification: `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易接口变动历史-系统设计.md`
- Update after verification: `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易接口变动历史-数据模型.md`
- Update after verification: `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易接口变动历史-API接口.md`
- Update: `/Users/java/obsidian/01 Engineering/sunline-benchmark/_overview.md`
- Update: `/Users/java/obsidian/index.md`
- Append: `/Users/java/obsidian/log.md`

- [ ] **Step 1: Run all feature tests together**

```bash
mvn -DskipTests=false -Dtest=FlowtransInterfaceSnapshotParserTest,FlowFieldChangeDiffServiceTest,GitLabFileVersionServiceImplTest,FlowFieldChangeLogServiceImplTest,FlowFieldChangeCaptureServiceImplTest,FlowFieldChangeControllerTest,WebhookControllerEventUuidTest,FlowFieldChangeFrontendContractTest,FlowFieldChangeSchemaContractTest test
```

Expected: 0 failures, 0 errors.

- [ ] **Step 2: Run complete suite and package**

```bash
mvn -DskipTests=false test
mvn clean package
```

Expected: both exit 0. If unrelated baseline failures exist, report exact tests and do not claim full-suite green.

- [ ] **Step 3: Verify packaged resources**

```bash
jar tf target/*.jar | rg 'flow-field-change-history.html|FlowFieldChange|GitLabFileVersion'
```

Expected: page and new classes are present.

- [ ] **Step 4: Verify trust boundary and read-only surface**

```bash
rg -n 'project\.web_url|PRIVATE-TOKEN|gitlabAccessToken|access-token' \
  src/main/java/com/sunline/dict/service/impl/GitLabFileVersionServiceImpl.java \
  src/main/java/com/sunline/dict/service/impl/FlowFieldChangeCaptureServiceImpl.java
rg -n '@(Post|Put|Delete)Mapping' src/main/java/com/sunline/dict/controller/FlowFieldChangeController.java
```

Confirm configured host only, no secrets in errors/responses, fetch only after master guard, and no write/delete history endpoint.

- [ ] **Step 5: Check worktree scope**

```bash
git status --short
git diff --check
git diff --stat
```

Confirm no unrelated user files are staged and no destructive cleanup occurred.

- [ ] **Step 6: Update design status and log exact evidence**

After code/browser verification, set the three feature pages to `status: implemented`, update `_overview.md`, ensure index entries remain present, and append an `[IMPL]` log line with exact test/build results. Preserve unrelated dirty Obsidian content and commit only independently owned files/hunks.

- [ ] **Step 7: Commit verification-only fixes when present**

Stage only files actually changed by verification fixes and commit `test: verify flowtrans interface history`. Do not create an empty commit.

---

## Plan Self-Review Checklist

- [x] Every approved requirement maps to a task and test.
- [x] Parser/diff are pure; network and persistence are behind interfaces.
- [x] GitLab host trust boundary is explicit and tested.
- [x] Whole-file add/delete and empty files are covered.
- [x] No-ref, nested, arbitrary attributes, case and order rules are covered.
- [x] One Webhook/file aggregation and last commit metadata are covered.
- [x] Counts are per field, not per modified attribute.
- [x] Idempotency and per-file failure isolation are covered.
- [x] Page, menu, read API, SQL schema and JAR packaging are covered.
- [x] Existing user changes and secrets are protected from accidental staging/output.
