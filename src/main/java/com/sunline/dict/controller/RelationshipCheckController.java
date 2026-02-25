package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.RelationshipCheckService;
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

/**
 * 关联性检查控制器
 */
@RestController
@RequestMapping("/api/relationship")
public class RelationshipCheckController {
    
    private static final Logger log = LoggerFactory.getLogger(RelationshipCheckController.class);
    
    @Autowired
    private RelationshipCheckService relationshipCheckService;
    
    /**
     * 执行关联性检查
     * 如果检查通过，返回JSON
     * 如果检查失败，返回Excel文件
     */
    @GetMapping("/check")
    public ResponseEntity<?> checkRelationship() {
        try {
            log.info("开始执行关联性检查");
            
            byte[] excelBytes = relationshipCheckService.performCheck();
            
            if (excelBytes == null || excelBytes.length == 0) {
                // 检查通过，返回JSON
                log.info("关联性检查通过");
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Result.success("关联性检查通过，所有数据关联正常！"));
            } else {
                // 检查失败，返回Excel文件
                log.info("关联性检查发现问题，生成Excel报告，大小: {} bytes", excelBytes.length);
                
                ByteArrayResource resource = new ByteArrayResource(excelBytes);
                
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"关联性检查.xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(excelBytes.length)
                    .body(resource);
            }
        } catch (Exception e) {
            log.error("关联性检查失败", e);
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.error("关联性检查失败: " + e.getMessage()));
        }
    }
}

