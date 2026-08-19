package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeQuery;
import com.sunline.dict.entity.FlowFieldChangeDetail;
import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.mapper.FlowFieldChangeDetailMapper;
import com.sunline.dict.mapper.FlowFieldChangeLogMapper;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta;
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
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowFieldChangeLogServiceImplTest {

    private FlowFieldChangeLogMapper logMapper;
    private FlowFieldChangeDetailMapper detailMapper;
    private FlowFieldChangeLogServiceImpl service;

    @BeforeEach
    void setUp() {
        logMapper = mock(FlowFieldChangeLogMapper.class);
        detailMapper = mock(FlowFieldChangeDetailMapper.class);
        service = new FlowFieldChangeLogServiceImpl(logMapper, detailMapper, new ObjectMapper());
    }

    @Test
    void record_success_inserts_one_header_and_one_row_per_changed_field() {
        when(logMapper.insert(any())).thenAnswer(invocation -> {
            FlowFieldChangeLog row = invocation.getArgument(0);
            row.setId(88L);
            return 1;
        });

        FlowFieldChangeLog saved = service.recordSuccess(meta(), changeSetWithTwoDetails());

        assertEquals(88L, saved.getId());
        assertEquals("TC045", saved.getFlowId());
        assertEquals("MODIFY", saved.getFileChangeType());
        assertEquals("SUCCESS", saved.getCaptureStatus());
        assertEquals(1, saved.getAddCount());
        assertEquals(1, saved.getModifyCount());
        ArgumentCaptor<FlowFieldChangeDetail> captor = ArgumentCaptor.forClass(FlowFieldChangeDetail.class);
        verify(detailMapper, times(2)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(row -> row.getLogId().equals(88L)));
        assertEquals("{\"id\":\"amount\",\"type\":\"T1\"}", captor.getAllValues().get(0).getOldSnapshot());
        assertEquals("{\"required\":{\"new\":\"true\",\"old\":\"false\"}}",
                captor.getAllValues().get(0).getChangedAttributes());
    }

    @Test
    void record_success_requires_generated_header_id_before_detail_inserts() {
        when(logMapper.insert(any())).thenReturn(1);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.recordSuccess(meta(), changeSetWithTwoDetails()));

        assertEquals("未生成变动历史主键", error.getMessage());
        verify(detailMapper, never()).insert(any());
    }

    @Test
    void detail_decodes_snapshot_and_value_change_json_to_maps() {
        FlowFieldChangeLog log = new FlowFieldChangeLog();
        log.setId(77L);
        FlowFieldChangeDetail row = new FlowFieldChangeDetail();
        row.setId(5L);
        row.setLogId(77L);
        row.setIoType("input");
        row.setFieldPath("/");
        row.setFieldId("amount");
        row.setChangeType("MODIFY");
        row.setOldSnapshot("{\"id\":\"amount\",\"required\":\"false\"}");
        row.setNewSnapshot("{\"id\":\"amount\",\"required\":\"true\"}");
        row.setChangedAttributes("{\"required\":{\"old\":\"false\",\"new\":\"true\"}}");
        when(logMapper.selectById(77L)).thenReturn(log);
        when(detailMapper.selectList(any())).thenReturn(List.of(row));

        FlowFieldChangeHistoryDetail result = service.getDetail(77L);

        assertEquals("false", result.details().get(0).oldSnapshot().get("required"));
        assertEquals("true", result.details().get(0).newSnapshot().get("required"));
        assertEquals(new ValueChange("false", "true"),
                result.details().get(0).changedAttributes().get("required"));
    }

    @Test
    void record_failure_writes_unknown_failed_zero_counts_and_sanitized_truncated_error() {
        when(logMapper.insert(any())).thenAnswer(invocation -> {
            FlowFieldChangeLog row = invocation.getArgument(0);
            row.setId(99L);
            return 1;
        });
        String unsafe = "Authorization: Bearer secret-token\n" + "x".repeat(3000);

        FlowFieldChangeLog saved = service.recordFailure(meta(), unsafe);

        assertEquals("UNKNOWN", saved.getFileChangeType());
        assertEquals("FAILED", saved.getCaptureStatus());
        assertEquals(0, saved.getAddCount());
        assertEquals(0, saved.getModifyCount());
        assertEquals(0, saved.getRemoveCount());
        assertEquals(0, saved.getInputChangeCount());
        assertEquals(0, saved.getOutputChangeCount());
        assertNotNull(saved.getErrorMessage());
        assertTrue(saved.getErrorMessage().length() <= 2000);
        assertFalse(saved.getErrorMessage().contains("secret-token"));
        assertFalse(saved.getErrorMessage().contains("Authorization"));
        verify(detailMapper, never()).insert(any());
    }

    @Test
    void dedup_lookup_uses_the_unique_dedup_key() {
        when(logMapper.selectCount(any())).thenReturn(1L);

        assertTrue(service.existsByDedupKey("dedup-123"));

        ArgumentCaptor<QueryWrapper<FlowFieldChangeLog>> captor = wrapperCaptor();
        verify(logMapper).selectCount(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("dedup_key"));
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("dedup-123"));
    }

    @Test
    void detail_not_found_raises_a_domain_safe_exception() {
        when(logMapper.selectById(404L)).thenReturn(null);

        NoSuchElementException error = assertThrows(NoSuchElementException.class,
                () -> service.getDetail(404L));

        assertEquals("交易接口变动历史不存在", error.getMessage());
        verify(detailMapper, never()).selectList(any());
    }

    @Test
    void list_applies_filters_and_default_commit_or_create_time_order() {
        when(logMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> invocation.getArgument(0));
        FlowFieldChangeQuery query = new FlowFieldChangeQuery(
                3, 25, "TC045", "src/TC045", "张三", "MODIFY", "SUCCESS",
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-08-31T23:59:59"));

        Page<FlowFieldChangeLog> result = service.pageLogs(query);

        assertEquals(3, result.getCurrent());
        assertEquals(25, result.getSize());
        ArgumentCaptor<QueryWrapper<FlowFieldChangeLog>> captor = wrapperCaptor();
        verify(logMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("flow_id"));
        assertTrue(sql.contains("file_path"));
        assertTrue(sql.contains("commit_author"));
        assertTrue(sql.contains("file_change_type"));
        assertTrue(sql.contains("capture_status"));
        assertTrue(sql.contains("commit_time"));
        assertTrue(sql.contains("COALESCE(commit_time, create_time) DESC"));
        assertTrue(sql.contains("id DESC"));
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void list_rejects_invalid_page_enum_and_time_ranges(FlowFieldChangeQuery query) {
        assertThrows(IllegalArgumentException.class, () -> service.pageLogs(query));
        verify(logMapper, never()).selectPage(any(Page.class), any());
    }

    private static Stream<Arguments> invalidQueries() {
        LocalDateTime start = LocalDateTime.parse("2026-08-20T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-08-19T00:00:00");
        return Stream.of(
                Arguments.of(query(0, 20, null, null, null, null)),
                Arguments.of(query(1, 0, null, null, null, null)),
                Arguments.of(query(1, 101, null, null, null, null)),
                Arguments.of(query(1, 20, "RENAME", null, null, null)),
                Arguments.of(query(1, 20, null, "PENDING", null, null)),
                Arguments.of(query(1, 20, null, null, start, end)));
    }

    private static FlowFieldChangeQuery query(int current, int size, String fileType, String status,
                                              LocalDateTime start, LocalDateTime end) {
        return new FlowFieldChangeQuery(current, size, null, null, null, fileType, status, start, end);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ArgumentCaptor<QueryWrapper<FlowFieldChangeLog>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(QueryWrapper.class);
    }

    private FlowFieldChangeCaptureMeta meta() {
        return new FlowFieldChangeCaptureMeta(
                "dedup-123", "event-1", 123L, "ccbs", "master",
                "src/TC045.flowtrans.xml", "before", "after", "commit",
                "adjust fields", "张三", "zhangsan@example.com",
                LocalDateTime.parse("2026-08-19T10:30:00"));
    }

    private FlowFieldChangeSet changeSetWithTwoDetails() {
        FieldChange modified = new FieldChange(
                FieldChangeType.MODIFY,
                new FieldIdentity("input", "/", "amount"),
                sortedStrings("id", "amount", "type", "T1"),
                sortedStrings("id", "amount", "type", "T2", "required", "true"),
                new TreeMap<>(Map.of("required", new ValueChange("false", "true"))));
        FieldChange added = new FieldChange(
                FieldChangeType.ADD,
                new FieldIdentity("output", "/fields[result]", "status"),
                null,
                sortedStrings("id", "status", "type", "String"),
                new TreeMap<>());
        return new FlowFieldChangeSet(
                FileChangeType.MODIFY, "TC045", "manual transfer", List.of(modified, added),
                1, 1, 0, 1, 1);
    }

    private static TreeMap<String, String> sortedStrings(String... values) {
        TreeMap<String, String> result = new TreeMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(values[index], values[index + 1]);
        }
        return result;
    }
}
