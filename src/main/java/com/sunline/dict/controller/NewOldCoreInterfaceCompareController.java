package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.NewOldCoreInterfaceCompareService;
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
 * 新老核心接口文档比对控制器
 */
@RestController
@RequestMapping("/api/new-old-core")
public class NewOldCoreInterfaceCompareController {

    private static final Logger log = LoggerFactory.getLogger(NewOldCoreInterfaceCompareController.class);

    @Autowired
    private NewOldCoreInterfaceCompareService service;

    /**
     * 比对新老核心接口 Excel 文档
     */
    @PostMapping("/compare")
    public Result<Map<String, Object>> compare(
            @RequestParam("oldFile") MultipartFile oldFile,
            @RequestParam("newFile") MultipartFile newFile,
            @RequestParam(value = "excludeSheets", required = false, defaultValue = "") String excludeSheets) {

        try {
            log.info("收到新老核心接口比对请求 旧={} 新={} 排除={}",
                    oldFile.getOriginalFilename(), newFile.getOriginalFilename(), excludeSheets);

            if (oldFile.isEmpty() || newFile.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String oldName = oldFile.getOriginalFilename();
            String newName = newFile.getOriginalFilename();
            if (oldName == null || newName == null
                    || (!oldName.endsWith(".xlsx") && !oldName.endsWith(".xls"))
                    || (!newName.endsWith(".xlsx") && !newName.endsWith(".xls"))) {
                return Result.error("文件格式不正确，只支持.xlsx和.xls格式");
            }

            Map<String, Object> result = service.compareFiles(oldFile, newFile, excludeSheets);
            log.info("新老核心接口比对完成，结果文件: {}", result.get("fileName"));
            return Result.success(result);

        } catch (Exception e) {
            log.error("新老核心接口比对失败", e);
            return Result.error("比较失败：" + e.getMessage());
        }
    }

    /**
     * 下载比较结果
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadResult(@PathVariable String fileName) {
        try {
            File file = service.getResultFile(fileName);
            if (!file.exists()) {
                log.error("结果文件不存在: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    URLEncoder.encode(fileName, StandardCharsets.UTF_8));

            return ResponseEntity.ok().headers(headers).body(resource);

        } catch (Exception e) {
            log.error("下载结果文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
