package com.sunline.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeQuery;
import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.service.FlowFieldChangeLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/flow-field-change")
public class FlowFieldChangeController {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldChangeController.class);
    private final FlowFieldChangeLogService service;

    @Autowired
    public FlowFieldChangeController(FlowFieldChangeLogService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public Result<Page<FlowFieldChangeLog>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String flowId,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String commitAuthor,
            @RequestParam(required = false) String fileChangeType,
            @RequestParam(required = false) String captureStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            return Result.error(400, "endTime 不能早于 startTime");
        }
        try {
            FlowFieldChangeQuery query = new FlowFieldChangeQuery(
                    current, size, flowId, filePath, commitAuthor, fileChangeType, captureStatus,
                    startTime, endTime);
            return Result.success(service.pageLogs(query));
        } catch (IllegalArgumentException exception) {
            return Result.error(400, exception.getMessage());
        } catch (Exception exception) {
            log.error("查询交易接口变动历史失败: {}", exception.getClass().getSimpleName());
            return Result.error(500, "查询交易接口变动历史失败");
        }
    }

    @GetMapping("/detail/{logId}")
    public Result<FlowFieldChangeHistoryDetail> detail(@PathVariable Long logId) {
        try {
            return Result.success(service.getDetail(logId));
        } catch (NoSuchElementException exception) {
            return Result.error(404, "交易接口变动历史不存在");
        } catch (Exception exception) {
            log.error("查询交易接口变动历史详情失败, logId={}, type={}",
                    logId, exception.getClass().getSimpleName());
            return Result.error(500, "查询交易接口变动历史失败");
        }
    }
}
