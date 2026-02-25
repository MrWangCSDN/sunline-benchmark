package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.MigrationTableCompareService;
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
 * 迁移中间表比对控制器
 */
@RestController
@RequestMapping("/api/migration-table")
public class MigrationTableCompareController {
    
    private static final Logger log = LoggerFactory.getLogger(MigrationTableCompareController.class);
    
    @Autowired
    private MigrationTableCompareService migrationTableCompareService;
    
    /**
     * 比较XML表定义和迁移中间表
     */
    @PostMapping("/compare")
    public Result<Map<String, Object>> compareTables(
            @RequestParam("xmlFile") MultipartFile xmlFile,
            @RequestParam("migrationFile") MultipartFile migrationFile) {
        
        try {
            log.info("开始比较迁移中间表，XML表定义: {}, 迁移中间表: {}", 
                    xmlFile.getOriginalFilename(), migrationFile.getOriginalFilename());
            
            if (xmlFile.isEmpty() || migrationFile.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            String xmlName = xmlFile.getOriginalFilename();
            String migrationName = migrationFile.getOriginalFilename();
            if (xmlName == null || migrationName == null || 
                (!xmlName.endsWith(".xlsx") && !xmlName.endsWith(".xls")) ||
                (!migrationName.endsWith(".xlsx") && !migrationName.endsWith(".xls"))) {
                return Result.error("文件格式不正确，只支持.xlsx和.xls格式");
            }
            
            Map<String, Object> result = migrationTableCompareService.compareFiles(xmlFile, migrationFile);
            
            log.info("迁移中间表比较完成，结果文件: {}", result.get("fileName"));
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("比较迁移中间表失败", e);
            return Result.error("比较失败：" + e.getMessage());
        }
    }
    
    /**
     * 下载比较结果
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadResult(@PathVariable String fileName) {
        try {
            log.info("下载迁移中间表比较结果文件: {}", fileName);
            
            File file = migrationTableCompareService.getResultFile(fileName);
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
