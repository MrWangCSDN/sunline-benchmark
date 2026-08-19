package com.sunline.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowView;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunView;
import com.sunline.dict.service.FlowFieldChangeLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/flow-field-change")
public class FlowFieldChangeController {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldChangeController.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final FlowFieldChangeLogService service;
    private final Clock clock;

    @Autowired
    public FlowFieldChangeController(FlowFieldChangeLogService service,
                                     ObjectProvider<Clock> clockProvider) {
        this(service, clockProvider.getIfAvailable(() -> Clock.system(SHANGHAI)));
    }

    public FlowFieldChangeController(FlowFieldChangeLogService service, Clock clock) {
        this.service = service;
        this.clock = clock.withZone(SHANGHAI);
    }

    @GetMapping("/list")
    public Result<Page<FieldChangeRowView>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String flowId,
            @RequestParam(required = false) String ioType,
            @RequestParam(required = false) String fieldId,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String commitAuthor,
            @RequestParam(required = false) String captureStatus) {
        try {
            LocalDate today = LocalDate.now(clock);
            FieldChangeQuery query = new FieldChangeQuery(
                    required(current), required(size), defaultDate(startDate, today),
                    defaultDate(endDate, today), projectId, projectName, filePath, flowId,
                    ioType, fieldId, changeType, commitAuthor, captureStatus);
            query.validate();
            return Result.success(service.pageFieldChanges(query));
        } catch (IllegalArgumentException exception) {
            return Result.error(400, "请求参数不合法");
        } catch (Exception exception) {
            log.error("查询交易接口变动历史失败: {}", exception.getClass().getSimpleName());
            return Result.error(500, "查询交易接口变动历史失败");
        }
    }

    @GetMapping("/detail/{logId}")
    public Result<FlowFieldChangeHistoryDetail> detail(@PathVariable Long logId) {
        try {
            if (logId == null || logId <= 0) {
                return Result.error(400, "请求参数不合法");
            }
            return Result.success(service.getDetail(logId));
        } catch (NoSuchElementException exception) {
            return Result.error(404, "交易接口变动历史不存在");
        } catch (Exception exception) {
            log.error("查询交易接口变动历史详情失败, logId={}, type={}",
                    logId, exception.getClass().getSimpleName());
            return Result.error(500, "查询交易接口变动历史失败");
        }
    }

    @GetMapping("/scan-runs")
    public Result<Page<ScanRunView>> scanRuns(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status) {
        try {
            LocalDate today = LocalDate.now(clock);
            ScanRunQuery query = new ScanRunQuery(
                    required(current), required(size), projectId, status,
                    defaultDate(startDate, today), defaultDate(endDate, today));
            query.validate();
            return Result.success(service.pageScanRuns(query));
        } catch (IllegalArgumentException exception) {
            return Result.error(400, "请求参数不合法");
        } catch (Exception exception) {
            log.error("查询扫描运行状态失败: {}", exception.getClass().getSimpleName());
            return Result.error(500, "查询扫描运行状态失败");
        }
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleRequestBindingFailure(MethodArgumentTypeMismatchException exception) {
        return Result.error(400, "请求参数不合法");
    }

    private static int required(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("分页参数不能为空");
        }
        return value;
    }

    private static LocalDate defaultDate(LocalDate value, LocalDate defaultValue) {
        return value == null ? defaultValue : value;
    }
}
