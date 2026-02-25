package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.ExcelCleanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Excel数据清洗控制器
 */
@RestController
@RequestMapping("/api/excel")
public class ExcelCleanController {
    
    private static final Logger log = LoggerFactory.getLogger(ExcelCleanController.class);
    
    @Autowired
    private ExcelCleanService excelCleanService;
    
    /**
     * 清洗Excel文件
     */
    @PostMapping("/clean")
    public Result<Map<String, Object>> cleanExcel(
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "sheetNames", required = false, defaultValue = "") String sheetNames,
            @RequestParam(value = "renameRules", required = false, defaultValue = "") String renameRules) {
        
        try {
            log.info("开始清洗Excel文件: {}", excelFile.getOriginalFilename());
            log.info("保留的Sheet页: {}", sheetNames);
            log.info("重命名规则: {}", renameRules);
            
            // 验证文件
            if (excelFile.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            // 验证至少配置了一个功能
            if ((sheetNames == null || sheetNames.trim().isEmpty()) && 
                (renameRules == null || renameRules.trim().isEmpty())) {
                return Result.error("请至少配置一个功能（保留Sheet页或替换Sheet页名称）");
            }
            
            // 验证文件格式
            String fileName = excelFile.getOriginalFilename();
            if (fileName == null || 
                (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
                return Result.error("文件格式不正确，只支持.xlsx和.xls格式");
            }
            
            // 执行清洗
            Map<String, Object> result = excelCleanService.cleanExcelFile(excelFile, sheetNames, renameRules);
            
            log.info("Excel清洗完成，结果文件: {}", result.get("fileName"));
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("清洗Excel文件失败", e);
            return Result.error("清洗失败：" + e.getMessage());
        }
    }
    
    /**
     * 下载清洗结果
     */
    @GetMapping("/clean/download/{fileName}")
    public ResponseEntity<Resource> downloadResult(@PathVariable String fileName) {
        try {
            log.info("下载清洗结果文件: {}", fileName);
            
            File file = excelCleanService.getResultFile(fileName);
            if (!file.exists()) {
                log.error("文件不存在: {}", fileName);
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            // 设置响应头
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
