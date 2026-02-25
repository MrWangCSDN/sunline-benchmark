package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.config.DatabaseExportConfig;
import com.sunline.dict.service.DatabaseExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库导出控制器
 */
@RestController
@RequestMapping("/api/db-export")
public class DatabaseExportController {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseExportController.class);
    
    @Autowired
    private DatabaseExportService databaseExportService;
    
    @Autowired
    private DatabaseExportConfig databaseExportConfig;
    
    /**
     * 获取所有环境列表
     */
    @GetMapping("/environments")
    public Result<Map<String, Object>> getEnvironments() {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("environments", databaseExportConfig.getEnvironments().keySet());
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取环境列表失败", e);
            return Result.error("获取环境列表失败：" + e.getMessage());
        }
    }
    
    /**
     * 按分组导出数据库表到Excel
     */
    @PostMapping("/export-by-groups")
    public Result<Map<String, Object>> exportTablesByGroups(@RequestBody Map<String, Object> request) {
        try {
            String environment = (String) request.get("environment");
            @SuppressWarnings("unchecked")
            List<String> groups = (List<String>) request.get("groups");
            
            log.info("按分组导出数据库表，环境: {}, 分组: {}", environment, groups);
            
            if (environment == null || environment.trim().isEmpty()) {
                return Result.error("请选择环境");
            }
            
            if (groups == null || groups.isEmpty()) {
                return Result.error("请至少选择一个分组");
            }
            
            // 执行按组导出
            Map<String, Object> result = databaseExportService.exportTablesByGroups(environment, groups);
            
            log.info("导出完成，共 {} 个文件", ((List<?>) result.get("files")).size());
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("导出数据库表失败", e);
            return Result.error("导出失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取分组配置
     */
    @GetMapping("/group-config/{group}")
    public Result<Map<String, Object>> getGroupConfig(@PathVariable String group) {
        try {
            String tables = databaseExportService.getGroupConfig(group);
            Map<String, Object> result = new HashMap<>();
            result.put("tables", tables);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取分组配置失败", e);
            return Result.error("获取配置失败：" + e.getMessage());
        }
    }
    
    /**
     * 保存分组配置
     */
    @PostMapping("/group-config/save")
    public Result<Void> saveGroupConfig(@RequestBody Map<String, String> request) {
        try {
            String group = request.get("group");
            String tables = request.get("tables");
            
            log.info("保存分组配置，分组: {}", group);
            databaseExportService.saveGroupConfig(group, tables);
            
            return Result.success("配置保存成功", null);
        } catch (Exception e) {
            log.error("保存分组配置失败", e);
            return Result.error("保存失败：" + e.getMessage());
        }
    }
    
    /**
     * 下载导出结果
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadResult(@PathVariable String fileName) {
        try {
            log.info("下载导出文件: {}", fileName);
            
            File file = databaseExportService.getResultFile(fileName);
            if (!file.exists()) {
                log.error("文件不存在: {}", fileName);
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", 
                    URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("下载文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

