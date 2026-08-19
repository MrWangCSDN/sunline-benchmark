package com.sunline.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowView;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunView;
import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.service.FlowFieldChangeLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlowFieldChangeControllerTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T14:00:00Z"), SHANGHAI);

    @Test
    void valid_field_day_filters_reach_the_real_controller_contract() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        Page<FieldChangeRowView> page = new Page<>(1, 20, 1);
        when(service.pageFieldChanges(any())).thenReturn(page);
        FlowFieldChangeController controller = controller(service);

        Result<Page<FieldChangeRowView>> result = controller.list(
                1, 20, LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 19),
                42L, null, null, "TC001", "input", "Acct", "MODIFY", null, "SUCCESS");

        assertEquals(200, result.getCode());
        assertEquals(page, result.getData());
        ArgumentCaptor<FieldChangeQuery> captor = ArgumentCaptor.forClass(FieldChangeQuery.class);
        verify(service).pageFieldChanges(captor.capture());
        assertEquals(42L, captor.getValue().projectId());
        assertEquals("TC001", captor.getValue().flowId());
        assertEquals("input", captor.getValue().ioType());
        assertEquals("Acct", captor.getValue().fieldId());
        assertEquals("MODIFY", captor.getValue().changeType());
        assertEquals("SUCCESS", captor.getValue().captureStatus());
    }

    @Test
    void omitted_field_dates_default_to_the_current_shanghai_date() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        when(service.pageFieldChanges(any())).thenReturn(new Page<>(1, 20));
        FlowFieldChangeController controller = controller(service);

        controller.list(1, 20, null, null, null, null, null, null,
                null, null, null, null, null);

        ArgumentCaptor<FieldChangeQuery> captor = ArgumentCaptor.forClass(FieldChangeQuery.class);
        verify(service).pageFieldChanges(captor.capture());
        assertEquals(LocalDate.of(2026, 8, 19), captor.getValue().startDate());
        assertEquals(LocalDate.of(2026, 8, 19), captor.getValue().endDate());
    }

    @Test
    void scan_run_filters_and_pagination_reach_the_service() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        Page<ScanRunView> page = new Page<>(2, 30, 4);
        when(service.pageScanRuns(any())).thenReturn(page);
        FlowFieldChangeController controller = controller(service);

        Result<Page<ScanRunView>> result = controller.scanRuns(
                2, 30, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                42L, "COMPLETED_WITH_ERRORS");

        assertEquals(200, result.getCode());
        assertEquals(page, result.getData());
        ArgumentCaptor<ScanRunQuery> captor = ArgumentCaptor.forClass(ScanRunQuery.class);
        verify(service).pageScanRuns(captor.capture());
        assertEquals(42L, captor.getValue().projectId());
        assertEquals("COMPLETED_WITH_ERRORS", captor.getValue().status());
        assertEquals(LocalDate.of(2026, 8, 1), captor.getValue().startDate());
        assertEquals(LocalDate.of(2026, 8, 31), captor.getValue().endDate());
    }

    @Test
    void omitted_scan_run_dates_default_to_the_current_shanghai_date() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        when(service.pageScanRuns(any())).thenReturn(new Page<>(1, 20));
        FlowFieldChangeController controller = controller(service);

        controller.scanRuns(1, 20, null, null, null, null);

        ArgumentCaptor<ScanRunQuery> captor = ArgumentCaptor.forClass(ScanRunQuery.class);
        verify(service).pageScanRuns(captor.capture());
        assertEquals(LocalDate.of(2026, 8, 19), captor.getValue().startDate());
        assertEquals(LocalDate.of(2026, 8, 19), captor.getValue().endDate());
    }

    @Test
    void missing_detail_maps_to_body_code_404() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        when(service.getDetail(404L)).thenThrow(new NoSuchElementException("交易接口变动历史不存在"));

        Result<FlowFieldChangeHistoryDetail> result = controller(service).detail(404L);

        assertEquals(404, result.getCode());
        assertEquals("交易接口变动历史不存在", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void existing_detail_returns_body_code_200() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        FlowFieldChangeLog log = new FlowFieldChangeLog();
        log.setId(101L);
        FlowFieldChangeHistoryDetail detail = new FlowFieldChangeHistoryDetail(log, List.of());
        when(service.getDetail(101L)).thenReturn(detail);

        Result<FlowFieldChangeHistoryDetail> result = controller(service).detail(101L);

        assertEquals(200, result.getCode());
        assertEquals(detail, result.getData());
    }

    @Test
    void invalid_page_enum_and_date_values_return_safe_400_before_service_calls() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        FlowFieldChangeController controller = controller(service);

        assertEquals(400, controller.list(0, 20, null, null, null, null, null,
                null, null, null, null, null, null).getCode());
        assertEquals(400, controller.list(1, 101, null, null, null, null, null,
                null, null, null, null, null, null).getCode());
        assertEquals(400, controller.list(1, 20, null, null, null, null, null,
                null, "side", null, null, null, null).getCode());
        assertEquals(400, controller.list(1, 20, null, null, null, null, null,
                null, null, null, "RENAME", null, null).getCode());
        assertEquals(400, controller.list(1, 20, LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 19), null, null, null, null, null, null,
                null, null, null).getCode());
        assertEquals(400, controller.scanRuns(1, 20, null, null, null, "PENDING").getCode());
        assertEquals(400, controller.scanRuns(1, 20, LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 8, 19), null, null).getCode());
        verify(service, never()).pageFieldChanges(any());
        verify(service, never()).pageScanRuns(any());
    }

    @Test
    void database_failures_return_generic_500_without_sql_or_exception_details() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        when(service.pageFieldChanges(any())).thenThrow(new RuntimeException(
                "SELECT * FROM flow_field_change_log; SQLException at Mapper.java:42"));
        when(service.pageScanRuns(any())).thenThrow(new RuntimeException(
                "jdbc:mysql://db.internal/flow password=secret"));
        FlowFieldChangeController controller = controller(service);

        Result<Page<FieldChangeRowView>> list = controller.list(
                1, 20, null, null, null, null, null, null, null, null, null, null, null);
        Result<Page<ScanRunView>> runs = controller.scanRuns(1, 20, null, null, null, null);

        assertEquals(500, list.getCode());
        assertEquals("查询交易接口变动历史失败", list.getMessage());
        assertFalse(list.getMessage().contains("SELECT"));
        assertFalse(list.getMessage().contains("SQLException"));
        assertEquals(500, runs.getCode());
        assertEquals("查询扫描运行状态失败", runs.getMessage());
        assertFalse(runs.getMessage().contains("password"));
    }

    @Test
    void controller_exposes_exactly_three_get_only_read_endpoints() {
        List<Method> requestMethods = List.of(FlowFieldChangeController.class.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class))
                .toList();

        assertEquals(3, requestMethods.size());
        assertTrue(requestMethods.stream().allMatch(method -> method.isAnnotationPresent(GetMapping.class)));
        assertNotNull(FlowFieldChangeController.class.getAnnotation(
                org.springframework.web.bind.annotation.RestController.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/flow-field-change/list?current=abc",
            "/api/flow-field-change/list?startDate=not-an-iso-date",
            "/api/flow-field-change/detail/not-a-number",
            "/api/flow-field-change/scan-runs?endDate=2026-99-99"
    })
    void request_binding_failures_return_result_body_code_400(String path) throws Exception {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(service)).build();

        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数不合法"));

        verify(service, never()).pageFieldChanges(any());
        verify(service, never()).pageScanRuns(any());
    }

    private static FlowFieldChangeController controller(FlowFieldChangeLogService service) {
        return new FlowFieldChangeController(service, CLOCK);
    }
}
