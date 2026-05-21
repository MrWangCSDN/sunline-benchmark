package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.dto.ValidationResult;
import com.sunline.dict.service.ExportService;
import com.sunline.dict.service.FileTemplateExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 数据导出控制器
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {
    
    private static final Logger log = LoggerFactory.getLogger(ExportController.class);
    
    @Autowired
    private ExportService exportService;

    @Autowired
    private FileTemplateExportService fileTemplateExportService;
    
    /**
     * 导出所有数据到Excel
     * @param exportType 导出类型: "standard"(贯标数据), "ing"(在途数据), "all"(全量数据)
     */
    @GetMapping("/all")
    public ResponseEntity<ByteArrayResource> exportAllData(
            @org.springframework.web.bind.annotation.RequestParam(value = "type", defaultValue = "all") String exportType) {
        try {
            log.info("开始导出数据，类型: {}", exportType);
            
            byte[] excelBytes = exportService.exportAllData(exportType);
            
            log.info("数据导出成功，文件大小: {} bytes", excelBytes.length);
            
            ByteArrayResource resource = new ByteArrayResource(excelBytes);
            
            // 根据导出类型设置文件名
            String fileName;
            if ("standard".equals(exportType)) {
                fileName = "已贯标全量清单.xlsx";
            } else if ("ing".equals(exportType)) {
                fileName = "在途数标全量清单.xlsx";
            } else {
                fileName = "全量数标清单.xlsx";
            }
            
            // 对文件名进行URL编码以支持中文
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(excelBytes.length)
                .body(resource);
        } catch (Exception e) {
            log.error("数据导出失败", e);
            throw new RuntimeException("数据导出失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 校验全量数据导出前的数据完整性
     */
    @GetMapping("/validate-all")
    public Result<ValidationResult> validateAllData() {
        try {
            log.info("开始校验全量数据导出前的数据完整性");
            ValidationResult result = exportService.validateAllDataForExport();
            return Result.success("校验完成", result);
        } catch (Exception e) {
            log.error("校验全量数据失败", e);
            return Result.error("校验失败: " + e.getMessage());
        }
    }

    /**
     * 导出文件模版骨架工作簿
     *
     * @param scope 导出范围：all/deposit/loan/public/settlement
     */
    @GetMapping("/file-template")
    public ResponseEntity<ByteArrayResource> exportFileTemplate(
            @org.springframework.web.bind.annotation.RequestParam(value = "scope", defaultValue = "all") String scope) {
        try {
            log.info("开始导出文件模版，范围: {}", scope);

            byte[] excelBytes = fileTemplateExportService.exportTemplateWorkbook(scope);
            ByteArrayResource resource = new ByteArrayResource(excelBytes);

            String fileName = buildFileTemplateFileName(scope);
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(excelBytes.length)
                    .body(resource);
        } catch (Exception e) {
            log.error("文件模版导出失败，范围: {}", scope, e);
            throw new RuntimeException("文件模版导出失败: " + e.getMessage(), e);
        }
    }

    private String buildFileTemplateFileName(String scope) {
        if (scope == null) {
            return "文件模版_全领域.xlsx";
        }
        switch (scope.toLowerCase()) {
            case "deposit":
                return "文件模版_存款领域.xlsx";
            case "loan":
                return "文件模版_贷款领域.xlsx";
            case "public":
                return "文件模版_公共领域.xlsx";
            case "settlement":
                return "文件模版_结算领域.xlsx";
            default:
                return "文件模版_全领域.xlsx";
        }
    }
}

