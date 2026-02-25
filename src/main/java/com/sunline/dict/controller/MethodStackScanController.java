package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.MethodStackScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 方法栈扫描控制器
 */
@RestController
@RequestMapping("/api/method-stack-scan")
@CrossOrigin
public class MethodStackScanController {
    
    private static final Logger log = LoggerFactory.getLogger(MethodStackScanController.class);
    
    @Autowired
    private MethodStackScanService methodStackScanService;
    
    /**
     * 开始扫描方法栈（文件上传方式）
     */
    @PostMapping("/scan")
    public Result<Map<String, Object>> scanMethodStack(
            @RequestParam("jarFile") MultipartFile jarFile,
            @RequestParam("jarNamePrefixes") String jarNamePrefixesJson) {
        try {
            // 验证文件
            if (jarFile == null || jarFile.isEmpty()) {
                return Result.error("请上传Jar包文件");
            }
            
            String originalFilename = jarFile.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".jar")) {
                return Result.error("请上传 .jar 格式的文件");
            }
            
            // 解析jarNamePrefixes
            List<String> jarNamePrefixes;
            try {
                @SuppressWarnings("unchecked")
                List<String> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(jarNamePrefixesJson, List.class);
                jarNamePrefixes = parsed;
            } catch (Exception e) {
                log.error("解析jarNamePrefixes失败", e);
                return Result.error("jarNamePrefixes参数格式错误");
            }
            
            if (jarNamePrefixes == null || jarNamePrefixes.isEmpty()) {
                return Result.error("请至少选择一个jar包前缀");
            }
            
            log.info("开始扫描方法栈，文件：{}，jar包前缀：{}", originalFilename, jarNamePrefixes);
            
            // 在Controller层（同步）先保存文件，避免异步时临时文件被清理
            File savedFile = saveUploadedFile(jarFile, "method-stack-upload");
            
            // 异步执行扫描（传递文件路径）
            methodStackScanService.scanMethodStack(savedFile.getAbsolutePath(), jarNamePrefixes);
            
            return Result.success(Map.of("message", "扫描任务已启动，文件：" + originalFilename));
        } catch (Exception e) {
            log.error("启动扫描失败", e);
            return Result.error("启动扫描失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取扫描进度
     */
    @GetMapping("/progress")
    public Result<Map<String, Object>> getScanProgress() {
        try {
            Map<String, Object> progress = methodStackScanService.getScanProgress();
            return Result.success(progress);
        } catch (Exception e) {
            log.error("获取扫描进度失败", e);
            return Result.error("获取扫描进度失败：" + e.getMessage());
        }
    }
    
    /**
     * 取消扫描
     */
    @PostMapping("/cancel")
    public Result<String> cancelScan() {
        try {
            methodStackScanService.cancelScan();
            return Result.success("扫描已取消");
        } catch (Exception e) {
            log.error("取消扫描失败", e);
            return Result.error("取消扫描失败：" + e.getMessage());
        }
    }
    
    /**
     * 保存上传的文件到临时目录（同步执行，避免异步时临时文件被清理）
     */
    private File saveUploadedFile(MultipartFile file, String prefix) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "uploaded.jar";
        }
        
        // 获取临时目录
        File baseTempDir = getTempDirectory();
        File tempDir = new File(baseTempDir, prefix + "-" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new RuntimeException("创建临时目录失败：" + tempDir.getAbsolutePath());
        }
        
        File tempFile = new File(tempDir, originalFilename);
        log.info("保存上传的文件到临时目录：{}", tempFile.getAbsolutePath());
        
        // 同步保存文件
        file.transferTo(tempFile);
        log.info("文件保存成功，大小：{} bytes", tempFile.length());
        
        return tempFile;
    }
    
    /**
     * 获取临时目录路径
     * Windows环境：使用系统临时目录
     * Linux环境：使用 /home/cbs/benchmark/tmp
     */
    private File getTempDirectory() {
        String osName = System.getProperty("os.name").toLowerCase();
        File tempDir;
        
        if (osName.contains("linux")) {
            // Linux环境：使用指定目录
            tempDir = new File("/home/cbs/benchmark/tmp");
            // 如果目录不存在，创建它
            if (!tempDir.exists()) {
                if (!tempDir.mkdirs()) {
                    log.warn("无法创建Linux临时目录：{}，将使用系统临时目录", tempDir.getAbsolutePath());
                    tempDir = new File(System.getProperty("java.io.tmpdir"));
                } else {
                    log.info("已创建Linux临时目录：{}", tempDir.getAbsolutePath());
                }
            }
        } else {
            // Windows或其他环境：使用系统临时目录
            tempDir = new File(System.getProperty("java.io.tmpdir"));
        }
        
        return tempDir;
    }
}

