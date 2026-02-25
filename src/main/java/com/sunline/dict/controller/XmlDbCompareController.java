package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.XmlDbCompareService;
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
 * XML模型与数据库比对控制器
 */
@RestController
@RequestMapping("/api/xml-db-compare")
public class XmlDbCompareController {
    
    private static final Logger log = LoggerFactory.getLogger(XmlDbCompareController.class);
    
    @Autowired
    private XmlDbCompareService xmlDbCompareService;
    
    /**
     * 分析XML模型和数据库的差异
     */
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyzeModified(@RequestParam("xmlFile") MultipartFile xmlFile,
            @RequestParam("dbFile") MultipartFile dbFile) {
        
        try {
            log.info("开始分析XML模型与数据库差异，XML: {}, DB: {}", 
                    xmlFile.getOriginalFilename(), dbFile.getOriginalFilename());
            
            // 验证文件
            if (xmlFile.isEmpty() || dbFile.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            // 验证文件格式
            String xmlName = xmlFile.getOriginalFilename();
            String dbName = dbFile.getOriginalFilename();
            if (xmlName == null || dbName == null || 
                (!xmlName.endsWith(".xlsx") && !xmlName.endsWith(".xls")) ||
                (!dbName.endsWith(".xlsx") && !dbName.endsWith(".xls"))) {
                return Result.error("文件格式不正确，只支持.xlsx和.xls格式");
            }
            
            // 执行分析
            Map<String, Object> result = xmlDbCompareService.compareFiles(xmlFile, dbFile);
            
            log.info("差异分析完成，结果文件: {}", result.get("fileName"));
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("分析XML模型与数据库差异失败", e);
            return Result.error("分析失败：" + e.getMessage());
        }
    }
    
    /**
     * 下载分析结果
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadResult(@PathVariable String fileName) {
        try {
            log.info("下载差异分析结果文件: {}", fileName);
            
            File file = xmlDbCompareService.getResultFile(fileName);
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
