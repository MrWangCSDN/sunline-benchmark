package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.dto.FlowFieldChangeDtos.DetailView;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowData;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowView;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunView;
import com.sunline.dict.entity.FlowFieldChangeDetail;
import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.mapper.FlowFieldChangeDetailMapper;
import com.sunline.dict.mapper.FlowFieldChangeLogMapper;
import com.sunline.dict.mapper.FlowFieldChangeQueryMapper;
import com.sunline.dict.service.FlowFieldChangeLogService;
import com.sunline.dict.service.FlowFieldChangeLogService.HistoryState;
import com.sunline.dict.service.FlowFieldChangeLogService.WriteDisposition;
import com.sunline.dict.service.FlowFieldChangeLogService.WriteOutcome;
import com.sunline.dict.service.flowchange.FlowFieldChangeMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FieldChange;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FieldChangeType;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.FileChangeType;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.ValueChange;
import com.sunline.dict.service.flowchange.FlowtransInterfaceSnapshot.FieldIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowFieldChangeLogServiceImplTest {

    private HistoryStore store;
    private FlowFieldChangeQueryMapper queryMapper;
    private FlowFieldChangeLogService service;

    @BeforeEach
    void setUp() {
        store = new HistoryStore();
        queryMapper = mock(FlowFieldChangeQueryMapper.class);
        FlowFieldChangeLogServiceImpl target = new FlowFieldChangeLogServiceImpl(
                store.logMapper(), store.detailMapper(), queryMapper, new ObjectMapper());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        TransactionInterceptor transactionInterceptor = new TransactionInterceptor();
        transactionInterceptor.setTransactionManager(new SnapshotTransactionManager(store));
        transactionInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxyFactory.addAdvice(transactionInterceptor);
        service = (FlowFieldChangeLogService) proxyFactory.getProxy();
    }

    @Test
    void dedup_key_uses_project_branch_commit_and_effective_path_only() {
        FlowFieldChangeMeta meta = meta("src/T001.flowtrans.xml", "abc123");

        assertEquals(sha256("42|master|abc123|src/T001.flowtrans.xml"), meta.dedupKey());
    }

    @Test
    void state_distinguishes_missing_failed_and_successful_history() {
        FlowFieldChangeMeta failed = meta("src/failed.flowtrans.xml", "failed-sha");
        FlowFieldChangeMeta success = meta("src/success.flowtrans.xml", "success-sha");

        assertEquals(HistoryState.NONE, service.state(failed.dedupKey()));
        service.recordFailure(failed, "XML 解析失败");
        service.recordSuccess(success, oneAddedField());

        assertEquals(HistoryState.FAILED, service.state(failed.dedupKey()));
        assertEquals(HistoryState.SUCCESS, service.state(success.dedupKey()));
    }

    @Test
    void successful_retry_upgrades_failed_header_and_replaces_details_atomically() {
        FlowFieldChangeMeta meta = meta("src/T001.flowtrans.xml", "abc123");
        WriteOutcome failed = service.recordFailure(meta, "XML 解析失败");

        WriteOutcome outcome = service.recordSuccess(meta, oneAddedField());

        assertEquals(failed.logId(), outcome.logId());
        assertEquals(WriteDisposition.UPGRADED, outcome.disposition());
        FlowFieldChangeLog saved = store.byDedup(meta.dedupKey());
        assertEquals("SUCCESS", saved.getCaptureStatus());
        assertNull(saved.getErrorMessage());
        assertEquals(9001L, saved.getScanRunId());
        assertEquals(LocalDate.of(2026, 8, 19), saved.getChangeDate());
        assertEquals("group/payments", saved.getProjectPath());
        assertEquals("parent-abc123", saved.getParentSha());
        assertEquals(1, store.details(outcome.logId()).size());
    }

    @Test
    void existing_success_is_skipped_without_replacing_header_or_details() {
        FlowFieldChangeMeta meta = meta("src/T001.flowtrans.xml", "abc123");
        WriteOutcome inserted = service.recordSuccess(meta, oneAddedField());

        WriteOutcome duplicate = service.recordSuccess(meta, zeroField(FileChangeType.DELETE));

        assertEquals(inserted.logId(), duplicate.logId());
        assertEquals(WriteDisposition.SKIPPED, duplicate.disposition());
        assertEquals("ADD", store.byDedup(meta.dedupKey()).getFileChangeType());
        assertEquals(1, store.details(inserted.logId()).size());
    }

    @Test
    void repeated_failure_updates_the_same_row_in_place_without_details() {
        FlowFieldChangeMeta meta = meta("src/T001.flowtrans.xml", "abc123");

        WriteOutcome inserted = service.recordFailure(meta, "第一次解析失败");
        WriteOutcome updated = service.recordFailure(meta, "第二次解析失败");

        assertEquals(WriteDisposition.INSERTED, inserted.disposition());
        assertEquals(WriteDisposition.UPGRADED, updated.disposition());
        assertEquals(inserted.logId(), updated.logId());
        assertEquals(1, store.logCount());
        assertEquals("第二次解析失败", store.byDedup(meta.dedupKey()).getErrorMessage());
        assertTrue(store.details(inserted.logId()).isEmpty());
    }

    @Test
    void failure_never_downgrades_an_existing_success() {
        FlowFieldChangeMeta meta = meta("src/T001.flowtrans.xml", "abc123");
        WriteOutcome success = service.recordSuccess(meta, oneAddedField());

        WriteOutcome retry = service.recordFailure(meta, "late parser error");

        assertEquals(WriteDisposition.SKIPPED, retry.disposition());
        assertEquals(success.logId(), retry.logId());
        assertEquals("SUCCESS", store.byDedup(meta.dedupKey()).getCaptureStatus());
        assertNull(store.byDedup(meta.dedupKey()).getErrorMessage());
    }

    @Test
    void success_inserts_complete_sorted_snapshot_and_changed_attribute_json() {
        WriteOutcome outcome = service.recordSuccess(
                meta("src/T001.flowtrans.xml", "abc123"), modifiedFieldWithAllAttributes());

        FlowFieldChangeDetail row = store.details(outcome.logId()).get(0);
        assertEquals("{\"id\":\"AcctNo\",\"required\":\"false\",\"type\":\"T1\"}",
                row.getOldSnapshot());
        assertEquals("{\"id\":\"AcctNo\",\"ref\":\"account.number\",\"required\":\"true\",\"type\":\"T2\"}",
                row.getNewSnapshot());
        assertEquals("{\"ref\":{\"new\":\"account.number\",\"old\":null},"
                        + "\"required\":{\"new\":\"true\",\"old\":\"false\"},"
                        + "\"type\":{\"new\":\"T2\",\"old\":\"T1\"}}",
                row.getChangedAttributes());
    }

    @Test
    void zero_field_add_and_delete_still_persist_file_headers() {
        WriteOutcome added = service.recordSuccess(
                meta("src/empty-add.flowtrans.xml", "add-sha"), zeroField(FileChangeType.ADD));
        WriteOutcome deleted = service.recordSuccess(
                meta("src/empty-delete.flowtrans.xml", "delete-sha"), zeroField(FileChangeType.DELETE));

        assertEquals("ADD", store.log(added.logId()).getFileChangeType());
        assertEquals("DELETE", store.log(deleted.logId()).getFileChangeType());
        assertTrue(store.details(added.logId()).isEmpty());
        assertTrue(store.details(deleted.logId()).isEmpty());
    }

    @Test
    void modify_with_no_field_diff_is_rejected_before_any_write() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.recordSuccess(meta("src/T001.flowtrans.xml", "abc123"),
                        zeroField(FileChangeType.MODIFY)));

        assertEquals("修改文件没有字段差异", error.getMessage());
        assertEquals(0, store.logCount());
    }

    @Test
    void detail_failure_rolls_back_failed_header_upgrade_and_detail_replacement() {
        FlowFieldChangeMeta meta = meta("src/T001.flowtrans.xml", "abc123");
        WriteOutcome failed = service.recordFailure(meta, "XML 解析失败");
        store.addStaleDetail(failed.logId());
        store.failNextDetailInsert();

        assertThrows(IllegalStateException.class,
                () -> service.recordSuccess(meta, oneAddedField()));

        assertEquals("FAILED", store.byDedup(meta.dedupKey()).getCaptureStatus());
        assertEquals("XML 解析失败", store.byDedup(meta.dedupKey()).getErrorMessage());
        assertEquals(List.of("stale"), store.details(failed.logId()).stream()
                .map(FlowFieldChangeDetail::getFieldId).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "password=hunter2",
            "client_secret: internal-secret",
            "Bearer bearer-secret",
            "PRIVATE-TOKEN=private-secret",
            "accessToken=camel-secret",
            "GET https://gitlab.example/api/projects/42",
            "jdbc:mysql://db.internal:3306/flow",
            "ssh://gitlab.example/internal/repository.git",
            "SELECT * FROM credentials WHERE user_id = 42",
            "TRUNCATE flow_field_change_log",
            "at java.base/java.util.ArrayList.forEach(ArrayList.java:1511)",
            "java.lang.IllegalStateException: boom\n\tat com.acme.Scanner.run(Scanner.java:42)"
    })
    void failure_replaces_secrets_urls_sql_and_stack_text_with_a_generic_summary(String unsafe) {
        FlowFieldChangeMeta meta = meta("src/" + Math.abs(unsafe.hashCode()) + ".flowtrans.xml",
                "sha-" + Math.abs(unsafe.hashCode()));

        WriteOutcome outcome = service.recordFailure(meta, unsafe);

        assertEquals("采集失败（敏感信息已隐藏）", store.log(outcome.logId()).getErrorMessage());
    }

    @Test
    void detail_converts_all_database_json_strings_to_objects() throws Exception {
        WriteOutcome outcome = service.recordSuccess(
                meta("src/T001.flowtrans.xml", "abc123"), modifiedFieldWithAllAttributes());

        FlowFieldChangeHistoryDetail detail = service.getDetail(outcome.logId());

        DetailView view = detail.details().get(0);
        assertEquals("false", view.oldSnapshot().get("required"));
        assertEquals("account.number", view.newSnapshot().get("ref"));
        assertNull(view.changedAttributes().get("ref").oldValue());
        assertEquals("account.number", view.changedAttributes().get("ref").newValue());
        ObjectMapper wireMapper = new ObjectMapper().findAndRegisterModules();
        JsonNode wire = wireMapper.readTree(wireMapper.writeValueAsString(detail));
        assertEquals("false", wire.at("/details/0/changedAttributes/required/old").asText());
        assertFalse(wire.at("/details/0/changedAttributes/required").has("oldValue"));
    }

    @Test
    void daily_detail_wire_contract_does_not_expose_legacy_webhook_fields() throws Exception {
        WriteOutcome outcome = service.recordSuccess(
                meta("src/T001.flowtrans.xml", "abc123"), oneAddedField());

        ObjectMapper wireMapper = new ObjectMapper().findAndRegisterModules();
        JsonNode log = wireMapper.readTree(
                wireMapper.writeValueAsString(service.getDetail(outcome.logId()))).get("log");

        assertFalse(log.has("webhookUuid"));
        assertFalse(log.has("beforeSha"));
        assertFalse(log.has("afterSha"));
    }

    @Test
    void response_json_maps_are_immutable() {
        WriteOutcome outcome = service.recordSuccess(
                meta("src/T001.flowtrans.xml", "abc123"), modifiedFieldWithAllAttributes());
        DetailView detail = service.getDetail(outcome.logId()).details().get(0);

        assertThrows(UnsupportedOperationException.class,
                () -> detail.newSnapshot().put("leak", "mutation"));
        assertThrows(UnsupportedOperationException.class,
                () -> detail.changedAttributes().put("leak", null));
    }

    @Test
    void missing_detail_raises_the_safe_not_found_error() {
        NoSuchElementException error = assertThrows(NoSuchElementException.class,
                () -> service.getDetail(404L));

        assertEquals("交易接口变动历史不存在", error.getMessage());
    }

    @Test
    void field_page_returns_one_view_per_detail_and_a_file_only_left_join_row() {
        FieldChangeQuery query = validFieldQuery();
        Page<FieldChangeRowData> databasePage = new Page<>(2, 20, 3);
        databasePage.setRecords(List.of(
                rowData(101L, 1001L, "input", "AcctNo", "MODIFY",
                        "{\"required\":{\"old\":\"false\",\"new\":\"true\"}}", "MODIFY"),
                rowData(101L, 1002L, "output", "Status", "ADD", "{}", "MODIFY"),
                rowData(102L, null, null, null, null, null, "DELETE")));
        when(queryMapper.selectFieldChanges(any(Page.class), eq(query))).thenReturn(databasePage);

        Page<FieldChangeRowView> result = service.pageFieldChanges(query);

        assertEquals(3, result.getTotal());
        assertEquals(3, result.getRecords().size());
        assertEquals("false", result.getRecords().get(0).changedAttributes()
                .get("required").oldValue());
        assertEquals("ADD", result.getRecords().get(1).changeType());
        assertNull(result.getRecords().get(2).detailId());
        assertNull(result.getRecords().get(2).fieldId());
        assertEquals("DELETE", result.getRecords().get(2).changeType());
    }

    @Test
    void field_page_passes_every_exact_and_contains_filter_to_the_query_mapper() {
        FieldChangeQuery query = validFieldQuery();
        when(queryMapper.selectFieldChanges(any(Page.class), any())).thenReturn(new Page<>(3, 25));

        Page<FieldChangeRowView> result = service.pageFieldChanges(query);

        assertEquals(3, result.getCurrent());
        assertEquals(25, result.getSize());
        ArgumentCaptor<FieldChangeQuery> captor = ArgumentCaptor.forClass(FieldChangeQuery.class);
        verify(queryMapper).selectFieldChanges(any(Page.class), captor.capture());
        assertEquals(42L, captor.getValue().projectId());
        assertEquals("payments", captor.getValue().projectName());
        assertEquals("src/T001", captor.getValue().filePath());
        assertEquals("TC001", captor.getValue().flowId());
        assertEquals("input", captor.getValue().ioType());
        assertEquals("Acct", captor.getValue().fieldId());
        assertEquals("MODIFY", captor.getValue().changeType());
        assertEquals("张三", captor.getValue().commitAuthor());
        assertEquals("SUCCESS", captor.getValue().captureStatus());
        assertEquals(LocalDate.of(2026, 8, 1), captor.getValue().startDate());
        assertEquals(LocalDate.of(2026, 8, 31), captor.getValue().endDate());
    }

    @ParameterizedTest
    @MethodSource("invalidFieldQueries")
    void field_page_rejects_invalid_page_enum_and_date_ranges_before_querying(FieldChangeQuery query) {
        assertThrows(IllegalArgumentException.class, () -> service.pageFieldChanges(query));
        verify(queryMapper, never()).selectFieldChanges(any(), any());
    }

    @Test
    void scan_run_page_preserves_filters_and_pagination() {
        ScanRunQuery query = new ScanRunQuery(2, 30, 42L, "FAILED",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        ScanRunView row = new ScanRunView(
                5001L, 42L, "payments", "group/payments", "master",
                LocalDateTime.of(2026, 8, 18, 22, 0), LocalDateTime.of(2026, 8, 19, 22, 0),
                "FAILED", 8, 3, 2, 1, 1, false, "1 file failed",
                LocalDateTime.of(2026, 8, 19, 22, 0), LocalDateTime.of(2026, 8, 19, 22, 0, 12));
        Page<ScanRunView> databasePage = new Page<>(2, 30, 1);
        databasePage.setRecords(List.of(row));
        when(queryMapper.selectScanRuns(any(Page.class), eq(query))).thenReturn(databasePage);

        Page<ScanRunView> result = service.pageScanRuns(query);

        assertEquals(1, result.getTotal());
        assertEquals("FAILED", result.getRecords().get(0).status());
        verify(queryMapper).selectScanRuns(any(Page.class), eq(query));
    }

    @ParameterizedTest
    @MethodSource("invalidScanRunQueries")
    void scan_run_page_rejects_invalid_page_status_and_date_ranges(ScanRunQuery query) {
        assertThrows(IllegalArgumentException.class, () -> service.pageScanRuns(query));
        verify(queryMapper, never()).selectScanRuns(any(), any());
    }

    private static Stream<Arguments> invalidFieldQueries() {
        FieldChangeQuery valid = validFieldQuery();
        return Stream.of(
                Arguments.of(copy(valid, 0, 20, "input", "MODIFY", "SUCCESS",
                        valid.startDate(), valid.endDate())),
                Arguments.of(copy(valid, 1, 0, "input", "MODIFY", "SUCCESS",
                        valid.startDate(), valid.endDate())),
                Arguments.of(copy(valid, 1, 101, "input", "MODIFY", "SUCCESS",
                        valid.startDate(), valid.endDate())),
                Arguments.of(copy(valid, 1, 20, "side", "MODIFY", "SUCCESS",
                        valid.startDate(), valid.endDate())),
                Arguments.of(copy(valid, 1, 20, "input", "RENAME", "SUCCESS",
                        valid.startDate(), valid.endDate())),
                Arguments.of(copy(valid, 1, 20, "input", "MODIFY", "PENDING",
                        valid.startDate(), valid.endDate())),
                Arguments.of(copy(valid, 1, 20, "input", "MODIFY", "SUCCESS",
                        LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 19))));
    }

    private static Stream<Arguments> invalidScanRunQueries() {
        return Stream.of(
                Arguments.of(new ScanRunQuery(0, 20, null, null, null, null)),
                Arguments.of(new ScanRunQuery(1, 0, null, null, null, null)),
                Arguments.of(new ScanRunQuery(1, 101, null, null, null, null)),
                Arguments.of(new ScanRunQuery(1, 20, null, "PENDING", null, null)),
                Arguments.of(new ScanRunQuery(1, 20, null, null,
                        LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 19))));
    }

    private static FieldChangeQuery validFieldQuery() {
        return new FieldChangeQuery(
                3, 25, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                42L, "payments", "src/T001", "TC001", "input", "Acct",
                "MODIFY", "张三", "SUCCESS");
    }

    private static FieldChangeQuery copy(FieldChangeQuery source, int current, int size,
                                         String ioType, String changeType, String status,
                                         LocalDate start, LocalDate end) {
        return new FieldChangeQuery(current, size, start, end, source.projectId(), source.projectName(),
                source.filePath(), source.flowId(), ioType, source.fieldId(), changeType,
                source.commitAuthor(), status);
    }

    private static FieldChangeRowData rowData(Long logId, Long detailId, String ioType,
                                              String fieldId, String detailChangeType,
                                              String changedAttributes, String fileChangeType) {
        return new FieldChangeRowData(
                logId, detailId, LocalDate.of(2026, 8, 19), 42L, "payments", "group/payments",
                "src/T001.flowtrans.xml", "TC001", "transfer", fileChangeType, "SUCCESS", null,
                ioType, detailId == null ? null : "/", fieldId, detailChangeType, changedAttributes,
                "abc123", "adjust fields", "张三", "zhangsan@example.com",
                LocalDateTime.of(2026, 8, 19, 18, 30));
    }

    private static FlowFieldChangeMeta meta(String path, String commitSha) {
        return new FlowFieldChangeMeta(
                9001L, LocalDate.of(2026, 8, 19), 42L, "payments", "group/payments",
                "master", path, "parent-" + commitSha, commitSha, "adjust fields", "张三",
                "zhangsan@example.com", LocalDateTime.of(2026, 8, 19, 18, 30));
    }

    private static FlowFieldChangeSet oneAddedField() {
        FieldChange added = new FieldChange(
                FieldChangeType.ADD, new FieldIdentity("input", "/", "AcctNo"), null,
                sortedStrings("id", "AcctNo", "ref", "account.number", "required", "true", "type", "T2"),
                new TreeMap<>());
        return new FlowFieldChangeSet(
                FileChangeType.ADD, "TC001", "transfer", List.of(added), 1, 0, 0, 1, 0);
    }

    private static FlowFieldChangeSet modifiedFieldWithAllAttributes() {
        TreeMap<String, ValueChange> changed = new TreeMap<>();
        changed.put("ref", new ValueChange(null, "account.number"));
        changed.put("required", new ValueChange("false", "true"));
        changed.put("type", new ValueChange("T1", "T2"));
        FieldChange modified = new FieldChange(
                FieldChangeType.MODIFY, new FieldIdentity("input", "/", "AcctNo"),
                sortedStrings("id", "AcctNo", "required", "false", "type", "T1"),
                sortedStrings("id", "AcctNo", "ref", "account.number", "required", "true", "type", "T2"),
                changed);
        return new FlowFieldChangeSet(
                FileChangeType.MODIFY, "TC001", "transfer", List.of(modified), 0, 1, 0, 1, 0);
    }

    private static FlowFieldChangeSet zeroField(FileChangeType type) {
        return new FlowFieldChangeSet(type, "EMPTY", "empty", List.of(), 0, 0, 0, 0, 0);
    }

    private static TreeMap<String, String> sortedStrings(String... values) {
        TreeMap<String, String> result = new TreeMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class HistoryStore {
        private final Map<Long, FlowFieldChangeLog> logs = new LinkedHashMap<>();
        private final Map<Long, FlowFieldChangeDetail> details = new LinkedHashMap<>();
        private long nextLogId = 1;
        private long nextDetailId = 1;
        private boolean failNextDetailInsert;

        FlowFieldChangeLogMapper logMapper() {
            return proxy(FlowFieldChangeLogMapper.class, this::invokeLog);
        }

        FlowFieldChangeDetailMapper detailMapper() {
            return proxy(FlowFieldChangeDetailMapper.class, this::invokeDetail);
        }

        FlowFieldChangeLog byDedup(String dedupKey) {
            return logs.values().stream()
                    .filter(row -> dedupKey.equals(row.getDedupKey()))
                    .findFirst().map(HistoryStore::copy).orElse(null);
        }

        FlowFieldChangeLog log(long id) {
            return copy(logs.get(id));
        }

        List<FlowFieldChangeDetail> details(long logId) {
            return details.values().stream()
                    .filter(row -> row.getLogId().equals(logId))
                    .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
                    .map(HistoryStore::copy).toList();
        }

        int logCount() {
            return logs.size();
        }

        void addStaleDetail(long logId) {
            FlowFieldChangeDetail row = new FlowFieldChangeDetail();
            row.setId(nextDetailId++);
            row.setLogId(logId);
            row.setIoType("input");
            row.setFieldPath("/");
            row.setFieldId("stale");
            row.setChangeType("ADD");
            details.put(row.getId(), copy(row));
        }

        void failNextDetailInsert() {
            failNextDetailInsert = true;
        }

        private Object invokeLog(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "insert" -> insertLog((FlowFieldChangeLog) args[0]);
                case "selectByDedupKey" -> byDedup((String) args[0]);
                case "updateDaily" -> updateLog((FlowFieldChangeLog) args[0]);
                case "selectById" -> log(((Number) args[0]).longValue());
                case "toString" -> "HistoryLogMapperFake";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private Object invokeDetail(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "insert" -> insertDetail((FlowFieldChangeDetail) args[0]);
                case "deleteByLogId" -> deleteDetails(((Number) args[0]).longValue());
                case "selectByLogId" -> details(((Number) args[0]).longValue());
                case "toString" -> "HistoryDetailMapperFake";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private int insertLog(FlowFieldChangeLog row) {
            if (byDedup(row.getDedupKey()) != null) {
                throw new IllegalStateException("duplicate dedup key");
            }
            row.setId(nextLogId++);
            logs.put(row.getId(), copy(row));
            return 1;
        }

        private int updateLog(FlowFieldChangeLog row) {
            if (!logs.containsKey(row.getId())) {
                return 0;
            }
            logs.put(row.getId(), copy(row));
            return 1;
        }

        private int insertDetail(FlowFieldChangeDetail row) {
            if (failNextDetailInsert) {
                failNextDetailInsert = false;
                throw new IllegalStateException("detail insert failed");
            }
            row.setId(nextDetailId++);
            details.put(row.getId(), copy(row));
            return 1;
        }

        private int deleteDetails(long logId) {
            int before = details.size();
            details.entrySet().removeIf(entry -> entry.getValue().getLogId().equals(logId));
            return before - details.size();
        }

        Snapshot snapshot() {
            Map<Long, FlowFieldChangeLog> logRows = new LinkedHashMap<>();
            logs.forEach((id, row) -> logRows.put(id, copy(row)));
            Map<Long, FlowFieldChangeDetail> detailRows = new LinkedHashMap<>();
            details.forEach((id, row) -> detailRows.put(id, copy(row)));
            return new Snapshot(logRows, detailRows, nextLogId, nextDetailId);
        }

        void restore(Snapshot snapshot) {
            logs.clear();
            snapshot.logs().forEach((id, row) -> logs.put(id, copy(row)));
            details.clear();
            snapshot.details().forEach((id, row) -> details.put(id, copy(row)));
            nextLogId = snapshot.nextLogId();
            nextDetailId = snapshot.nextDetailId();
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
        }

        private static FlowFieldChangeLog copy(FlowFieldChangeLog source) {
            if (source == null) {
                return null;
            }
            FlowFieldChangeLog target = new FlowFieldChangeLog();
            target.setId(source.getId());
            target.setDedupKey(source.getDedupKey());
            target.setScanRunId(source.getScanRunId());
            target.setChangeDate(source.getChangeDate());
            target.setProjectId(source.getProjectId());
            target.setProjectName(source.getProjectName());
            target.setProjectPath(source.getProjectPath());
            target.setBranch(source.getBranch());
            target.setFilePath(source.getFilePath());
            target.setFlowId(source.getFlowId());
            target.setFlowLongname(source.getFlowLongname());
            target.setFileChangeType(source.getFileChangeType());
            target.setCaptureStatus(source.getCaptureStatus());
            target.setErrorMessage(source.getErrorMessage());
            target.setParentSha(source.getParentSha());
            target.setCommitSha(source.getCommitSha());
            target.setCommitMessage(source.getCommitMessage());
            target.setCommitAuthor(source.getCommitAuthor());
            target.setCommitEmail(source.getCommitEmail());
            target.setCommitTime(source.getCommitTime());
            target.setAddCount(source.getAddCount());
            target.setModifyCount(source.getModifyCount());
            target.setRemoveCount(source.getRemoveCount());
            target.setInputChangeCount(source.getInputChangeCount());
            target.setOutputChangeCount(source.getOutputChangeCount());
            target.setCreateTime(source.getCreateTime());
            target.setUpdateTime(source.getUpdateTime());
            return target;
        }

        private static FlowFieldChangeDetail copy(FlowFieldChangeDetail source) {
            FlowFieldChangeDetail target = new FlowFieldChangeDetail();
            target.setId(source.getId());
            target.setLogId(source.getLogId());
            target.setIoType(source.getIoType());
            target.setFieldPath(source.getFieldPath());
            target.setFieldId(source.getFieldId());
            target.setChangeType(source.getChangeType());
            target.setOldSnapshot(source.getOldSnapshot());
            target.setNewSnapshot(source.getNewSnapshot());
            target.setChangedAttributes(source.getChangedAttributes());
            return target;
        }

        private record Snapshot(Map<Long, FlowFieldChangeLog> logs,
                                Map<Long, FlowFieldChangeDetail> details,
                                long nextLogId, long nextDetailId) {
        }
    }

    private static final class SnapshotTransactionManager extends AbstractPlatformTransactionManager {
        private final HistoryStore store;

        private SnapshotTransactionManager(HistoryStore store) {
            this.store = store;
        }

        @Override
        protected Object doGetTransaction() {
            return new TransactionSnapshot();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            ((TransactionSnapshot) transaction).snapshot = store.snapshot();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            store.restore(((TransactionSnapshot) status.getTransaction()).snapshot);
        }

        private static final class TransactionSnapshot {
            private HistoryStore.Snapshot snapshot;
        }
    }
}
