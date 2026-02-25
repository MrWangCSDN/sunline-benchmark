package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.DictUsageAnalysisService;
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
 * 在途字典使用分析Controller
 */
@RestController
@RequestMapping("/api/dict-usage")
@CrossOrigin
public class DictUsageAnalysisController {
    
    private static final Logger log = LoggerFactory.getLogger(DictUsageAnalysisController.class);
    
    @Autowired
    private DictUsageAnalysisService dictUsageAnalysisService;
    
    /**
     * 分析字典使用情况
     */
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyzeUsage(
            @RequestParam("codeDirectory") String codeDirectory,
            @RequestParam("dictFile") MultipartFile dictFile) {
        try {
            // 验证参数
            if (codeDirectory == null || codeDirectory.trim().isEmpty()) {
                return Result.error("代码目录不能为空");
            }
            if (dictFile == null || dictFile.isEmpty()) {
                return Result.error("字典文件不能为空");
            }
            
            String originalFilename = dictFile.getOriginalFilename();
            if (originalFilename == null || 
                (!originalFilename.toLowerCase().endsWith(".xlsx") && 
                 !originalFilename.toLowerCase().endsWith(".xls"))) {
                return Result.error("请上传Excel文件（.xlsx 或 .xls）");
            }
            
            log.info("开始分析字典使用情况，代码目录：{}，字典文件：{}", codeDirectory, originalFilename);
            
            // 执行分析
            Map<String, Object> result = dictUsageAnalysisService.analyzeUsage(
                codeDirectory.trim(), 
                dictFile
            );
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("分析字典使用情况失败", e);
            return Result.error("分析失败：" + e.getMessage());
        }
    }
    
    /**
     * 下载分析结果文件
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            File file = dictUsageAnalysisService.getAnalysisResultFile(fileName);
            
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            // 对文件名进行URL编码
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                .replaceAll("\\+", "%20");
            
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                .body(resource);
        } catch (Exception e) {
            log.error("下载文件失败：{}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

