package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.EsfInterfaceCompareService;
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
 * ESF接口文档比对控制器
 */
@RestController
@RequestMapping("/api/esf-interface")
public class EsfInterfaceCompareController {
    
    private static final Logger log = LoggerFactory.getLogger(EsfInterfaceCompareController.class);
    
    @Autowired
    private EsfInterfaceCompareService esfInterfaceCompareService;
    
    /**
     * 比较两个ESF接口文档
     */
    @PostMapping("/compare")
    public Result<Map<String, Object>> compareInterfaces(
            @RequestParam("baseFile") MultipartFile baseFile,
            @RequestParam("compareFile") MultipartFile compareFile) {
        
        try {
            log.info("开始比较ESF接口文档，基础版: {}, 比较版: {}", 
                    baseFile.getOriginalFilename(), compareFile.getOriginalFilename());
            
            if (baseFile.isEmpty() || compareFile.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            String baseName = baseFile.getOriginalFilename();
            String compareName = compareFile.getOriginalFilename();
            if (baseName == null || compareName == null || 
                (!baseName.endsWith(".xlsx") && !baseName.endsWith(".xls")) ||
                (!compareName.endsWith(".xlsx") && !compareName.endsWith(".xls"))) {
                return Result.error("文件格式不正确，只支持.xlsx和.xls格式");
            }
            
            Map<String, Object> result = esfInterfaceCompareService.compareFiles(baseFile, compareFile);
            
            log.info("ESF接口比较完成，结果文件: {}", result.get("fileName"));
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("比较ESF接口文档失败", e);
            return Result.error("比较失败：" + e.getMessage());
        }
    }
    
    /**
     * 下载比较结果
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadResult(@PathVariable String fileName) {
        try {
            log.info("下载ESF比较结果文件: {}", fileName);
            
            File file = esfInterfaceCompareService.getResultFile(fileName);
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
