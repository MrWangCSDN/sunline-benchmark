package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.PublicCoreFileBaselineCompareService;
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
 * 对公核心文件基线比对控制器
 */
@RestController
@RequestMapping("/api/public-core-file-baseline")
public class PublicCoreFileBaselineCompareController {

    private static final Logger log = LoggerFactory.getLogger(PublicCoreFileBaselineCompareController.class);

    @Autowired
    private PublicCoreFileBaselineCompareService service;

    /**
     * 比对两个对公核心文件基线 Excel
     */
    @PostMapping("/compare")
    public Result<Map<String, Object>> compare(
            @RequestParam("baselineFile") MultipartFile baselineFile,
            @RequestParam("compareFile") MultipartFile compareFile) {

        try {
            log.info("收到对公核心文件基线比对请求 baseline={} compare={}",
                    baselineFile.getOriginalFilename(), compareFile.getOriginalFilename());

            if (baselineFile.isEmpty() || compareFile.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String baselineName = baselineFile.getOriginalFilename();
            String compareName = compareFile.getOriginalFilename();
            if (baselineName == null || compareName == null
                    || (!baselineName.endsWith(".xlsx") && !baselineName.endsWith(".xls"))
                    || (!compareName.endsWith(".xlsx") && !compareName.endsWith(".xls"))) {
                return Result.error("文件格式不正确，只支持.xlsx和.xls格式");
            }

            Map<String, Object> result = service.compareFiles(baselineFile, compareFile);
            log.info("对公核心文件基线比对完成，结果文件: {}", result.get("fileName"));
            return Result.success(result);

        } catch (Exception e) {
            log.error("对公核心文件基线比对失败", e);
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
