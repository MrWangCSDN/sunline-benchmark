package com.sunline.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.DetailView;
import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.service.FlowFieldChangeLogService;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet.ValueChange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowFieldChangeControllerTest {

    @Test
    void rejects_end_time_before_start_time() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        FlowFieldChangeController controller = new FlowFieldChangeController(service);

        Result<Page<FlowFieldChangeLog>> result = controller.list(
                1, 20, null, null, null, null, null,
                LocalDateTime.parse("2026-08-20T00:00:00"),
                LocalDateTime.parse("2026-08-19T00:00:00"));

        assertEquals(400, result.getCode());
        assertEquals("endTime 不能早于 startTime", result.getMessage());
        verify(service, never()).pageLogs(any());
    }

    @Test
    void valid_filters_reach_the_service() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        Page<FlowFieldChangeLog> page = new Page<>(2, 30);
        when(service.pageLogs(any())).thenReturn(page);
        FlowFieldChangeController controller = new FlowFieldChangeController(service);

        Result<Page<FlowFieldChangeLog>> result = controller.list(
                2, 30, "TC045", "src/TC045", "张三", "MODIFY", "SUCCESS",
                LocalDateTime.parse("2026-08-01T00:00:00"),
                LocalDateTime.parse("2026-08-31T23:59:59"));

        assertEquals(200, result.getCode());
        assertEquals(page, result.getData());
        ArgumentCaptor<FlowFieldChangeQuery> captor = ArgumentCaptor.forClass(FlowFieldChangeQuery.class);
        verify(service).pageLogs(captor.capture());
        assertEquals("TC045", captor.getValue().flowId());
        assertEquals("src/TC045", captor.getValue().filePath());
        assertEquals("张三", captor.getValue().commitAuthor());
        assertEquals("MODIFY", captor.getValue().fileChangeType());
        assertEquals("SUCCESS", captor.getValue().captureStatus());
    }

    @Test
    void detail_returns_decoded_json_objects_from_the_service() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        FlowFieldChangeLog log = new FlowFieldChangeLog();
        log.setId(101L);
        DetailView view = new DetailView(
                1001L, "input", "/", "amount", "MODIFY",
                Map.of("required", "false"), Map.of("required", "true"),
                Map.of("required", new ValueChange("false", "true")));
        FlowFieldChangeHistoryDetail detail = new FlowFieldChangeHistoryDetail(log, List.of(view));
        when(service.getDetail(101L)).thenReturn(detail);
        FlowFieldChangeController controller = new FlowFieldChangeController(service);

        Result<FlowFieldChangeHistoryDetail> result = controller.detail(101L);

        assertEquals(200, result.getCode());
        assertTrue(result.getData().details().get(0).oldSnapshot() instanceof Map);
        assertEquals("true", result.getData().details().get(0).newSnapshot().get("required"));
    }

    @Test
    void missing_id_maps_to_body_code_404() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        when(service.getDetail(404L)).thenThrow(new NoSuchElementException("交易接口变动历史不存在"));
        FlowFieldChangeController controller = new FlowFieldChangeController(service);

        Result<FlowFieldChangeHistoryDetail> result = controller.detail(404L);

        assertEquals(404, result.getCode());
        assertEquals("交易接口变动历史不存在", result.getMessage());
    }

    @Test
    void database_failures_return_safe_messages_without_sql_or_stack_details() {
        FlowFieldChangeLogService service = mock(FlowFieldChangeLogService.class);
        when(service.pageLogs(any())).thenThrow(new RuntimeException(
                "SELECT * FROM flow_field_change_log; SQLException at Mapper.java:42"));
        FlowFieldChangeController controller = new FlowFieldChangeController(service);

        Result<Page<FlowFieldChangeLog>> result = controller.list(
                1, 20, null, null, null, null, null, null, null);

        assertEquals(500, result.getCode());
        assertEquals("查询交易接口变动历史失败", result.getMessage());
        assertFalse(result.getMessage().contains("SELECT"));
        assertFalse(result.getMessage().contains("SQLException"));
        assertEquals(null, result.getData());
    }

    @Test
    void controller_exposes_only_get_read_endpoints() {
        List<Method> requestMethods = List.of(FlowFieldChangeController.class.getDeclaredMethods()).stream()
                .filter(method -> method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class))
                .toList();

        assertEquals(2, requestMethods.size());
        assertTrue(requestMethods.stream().allMatch(method -> method.isAnnotationPresent(GetMapping.class)));
        assertNotNull(FlowFieldChangeController.class.getAnnotation(org.springframework.web.bind.annotation.RestController.class));
    }
}
