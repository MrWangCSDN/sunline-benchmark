package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.entity.FlowFieldDetail;
import com.sunline.dict.entity.Flowtran;
import com.sunline.dict.mapper.FlowtranMapper;
import com.sunline.dict.service.FlowFieldDetailService;
import com.sunline.dict.service.FlowFieldRescanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易字段明细控制器
 */
@RestController
@RequestMapping("/api/flow-field")
public class FlowFieldController {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldController.class);

    @Autowired
    private FlowFieldDetailService flowFieldDetailService;

    @Autowired
    private FlowFieldRescanService flowFieldRescanService;

    @Autowired
    private FlowtranMapper flowtranMapper;

    /**
     * 缺省源码目录配置（与 XmlScan 共用一份配置）。
     * 若 application.yml 未配置，缺省为空字符串，请求需显式传 sourcePath。
     */
    @Value("${xml-scan.default-source-path:}")
    private String defaultSourcePath;

    /** 按 flow_id 查字段清单（同时返回 input + output + flow 元信息） */
    @GetMapping("/by-flow/{flowId}")
    public Result<Map<String, Object>> byFlow(@PathVariable String flowId) {
        Map<String, Object> ret = new HashMap<>();
        ret.put("flowId", flowId);
        Flowtran flow = flowtranMapper.selectById(flowId);
        ret.put("longname", flow == null ? null : flow.getLongname());
        ret.put("input",  flowFieldDetailService.getByFlowIdAndIoType(flowId, "input"));
        ret.put("output", flowFieldDetailService.getByFlowIdAndIoType(flowId, "output"));
        return Result.success(ret);
    }

    /** 全量重扫（异步） */
    @PostMapping("/rescan-all")
    public Result<Map<String, Object>> rescanAll(@RequestBody(required = false) Map<String, String> body) {
        try {
            String sourcePath = body == null ? null : body.get("sourcePath");
            if (sourcePath == null || sourcePath.isEmpty()) {
                sourcePath = defaultSourcePath;
            }
            if (sourcePath == null || sourcePath.isEmpty()) {
                return Result.error("sourcePath 未传且 xml-scan.default-source-path 未配置");
            }
            log.info("触发字段明细全量重扫 sourcePath={}", sourcePath);
            return Result.success(flowFieldRescanService.startRescan(sourcePath));
        } catch (Exception e) {
            log.error("rescan-all 失败", e);
            return Result.error("启动失败：" + e.getMessage());
        }
    }

    /** 重扫单个 flow（同步） */
    @PostMapping("/rescan/{flowId}")
    public Result<Map<String, Integer>> rescanOne(@PathVariable String flowId,
                                                    @RequestBody(required = false) Map<String, String> body) {
        try {
            String sourcePath = body == null ? null : body.get("sourcePath");
            if (sourcePath == null || sourcePath.isEmpty()) {
                sourcePath = defaultSourcePath;
            }
            if (sourcePath == null || sourcePath.isEmpty()) {
                return Result.error("sourcePath 未传且 xml-scan.default-source-path 未配置");
            }
            return Result.success(flowFieldRescanService.rescanOne(flowId, sourcePath));
        } catch (Exception e) {
            log.error("rescan {} 失败", flowId, e);
            return Result.error("重扫失败：" + e.getMessage());
        }
    }

    /** 查询进度 */
    @GetMapping("/rescan/progress")
    public Result<Map<String, Object>> progress(@RequestParam String operationId) {
        return Result.success(flowFieldRescanService.getProgress(operationId));
    }
}
