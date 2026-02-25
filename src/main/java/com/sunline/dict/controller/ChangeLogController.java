package com.sunline.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.DictChangeLog;
import com.sunline.dict.entity.ImportHistory;
import com.sunline.dict.entity.CodeExtensionChangeLog;
import com.sunline.dict.entity.DomainChangeLog;
import com.sunline.dict.service.CodeExtensionChangeLogService;
import com.sunline.dict.service.DictChangeLogService;
import com.sunline.dict.service.DomainChangeLogService;
import com.sunline.dict.service.ImportHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 变更日志查询Controller
 */
@RestController
@RequestMapping("/api/dict/change-log")
@CrossOrigin
public class ChangeLogController {
    
    private static final Logger log = LoggerFactory.getLogger(ChangeLogController.class);
    
    @Autowired
    private DictChangeLogService changeLogService;
    
    @Autowired
    private ImportHistoryService importHistoryService;
    
    @Autowired
    private DomainChangeLogService domainChangeLogService;
    
    @Autowired
    private CodeExtensionChangeLogService codeExtensionChangeLogService;
    
    /**
     * 分页查询导入历史
     */
    @GetMapping("/page")
    public Result<Page<ImportHistory>> getImportHistoryPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String changeDescription) {
        try {
            Page<ImportHistory> page = new Page<>(current, size);
            QueryWrapper<ImportHistory> queryWrapper = new QueryWrapper<>();
            
            if (changeDescription != null && !changeDescription.isEmpty()) {
                queryWrapper.like("change_description", changeDescription);
            }
            
            queryWrapper.orderByDesc("import_time");
            
            Page<ImportHistory> result = importHistoryService.page(page, queryWrapper);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询导入历史失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取导入统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getImportStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // 总导入次数
            long total = importHistoryService.count();
            stats.put("total", total);
            
            // 成功次数
            QueryWrapper<ImportHistory> successWrapper = new QueryWrapper<>();
            successWrapper.eq("status", "SUCCESS");
            long successCount = importHistoryService.count(successWrapper);
            stats.put("newCount", successCount);  // 为了兼容前端，使用newCount字段
            
            // 失败次数
            QueryWrapper<ImportHistory> failedWrapper = new QueryWrapper<>();
            failedWrapper.eq("status", "FAILED");
            long failedCount = importHistoryService.count(failedWrapper);
            stats.put("updateCount", failedCount);  // 为了兼容前端，使用updateCount字段
            
            // 总记录数（所有导入的记录总和）
            stats.put("deleteCount", 0);  // 暂时设为0
            
            return Result.success("查询成功", stats);
        } catch (Exception e) {
            log.error("查询导入统计失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据导入历史ID查询变更详情
     */
    @GetMapping("/detail/{importId}")
    public Result<java.util.List<DictChangeLog>> getImportDetail(@PathVariable Long importId) {
        try {
            QueryWrapper<DictChangeLog> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("version_id", importId);
            queryWrapper.orderByAsc("id");
            java.util.List<DictChangeLog> logs = changeLogService.list(queryWrapper);
            return Result.success("查询成功", logs);
        } catch (Exception e) {
            log.error("查询导入详情失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据导入历史ID查询域清单变更详情
     */
    @GetMapping("/domain-detail/{importId}")
    public Result<java.util.List<DomainChangeLog>> getDomainDetail(@PathVariable Long importId) {
        try {
            java.util.List<DomainChangeLog> logs = domainChangeLogService.getByVersionId(importId);
            return Result.success("查询成功", logs);
        } catch (Exception e) {
            log.error("查询域清单变更详情失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据导入历史ID查询代码扩展清单变更详情
     */
    @GetMapping("/code-detail/{importId}")
    public Result<java.util.List<CodeExtensionChangeLog>> getCodeDetail(@PathVariable Long importId) {
        try {
            java.util.List<CodeExtensionChangeLog> logs = codeExtensionChangeLogService.getByVersionId(importId);
            return Result.success("查询成功", logs);
        } catch (Exception e) {
            log.error("查询代码扩展清单变更详情失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据数据项编号查询变更历史
     */
    @GetMapping("/history/{dataItemCode}")
    public Result<java.util.List<DictChangeLog>> getChangeHistory(@PathVariable String dataItemCode) {
        try {
            java.util.List<DictChangeLog> logs = changeLogService.getByDataItemCode(dataItemCode);
            return Result.success("查询成功", logs);
        } catch (Exception e) {
            log.error("查询变更历史失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
}

