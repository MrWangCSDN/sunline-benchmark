package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.XmlScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * XML 全量扫描 Controller
 */
@RestController
@RequestMapping("/api/xml-scan")
@CrossOrigin
public class XmlScanController {

    private static final Logger log = LoggerFactory.getLogger(XmlScanController.class);

    @Autowired
    private XmlScanService xmlScanService;

    /**
     * 执行全量扫描
     */
    @PostMapping("/scan")
    public Result<Map<String, Object>> scan(@RequestBody Map<String, Object> request) {
        try {
            String folderPath = (String) request.get("folderPath");

            if (folderPath == null || folderPath.trim().isEmpty()) {
                return Result.error("文件夹路径不能为空");
            }

            folderPath = folderPath.trim();

            // 校验绝对路径
            if (!folderPath.startsWith("/") && !folderPath.matches("^[A-Za-z]:[/\\\\].*")) {
                return Result.error("请输入完整的绝对路径，例如：/home/user/project 或 C:\\Users\\xx\\project");
            }

            Map<String, Object> result = xmlScanService.scanAndSave(folderPath);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            log.warn("XML扫描参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("XML全量扫描失败", e);
            return Result.error("扫描失败: " + e.getMessage());
        }
    }
}
