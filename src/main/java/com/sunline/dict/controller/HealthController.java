package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);
    
    @Autowired
    private DataSource dataSource;
    
    /**
     * 健康检查
     */
    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("application", "字典管理系统");
        data.put("version", "1.0.0");
        
        return Result.success(data);
    }
    
    /**
     * 数据库连接检查
     */
    @GetMapping("/db")
    public Result<Map<String, Object>> checkDatabase() {
        Map<String, Object> data = new HashMap<>();
        
        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(3);
            data.put("database", "MySQL");
            data.put("connected", isValid);
            data.put("url", connection.getMetaData().getURL());
            data.put("username", connection.getMetaData().getUserName());
            
            if (isValid) {
                return Result.success("数据库连接正常", data);
            } else {
                return Result.error("数据库连接异常");
            }
        } catch (Exception e) {
            log.error("数据库连接检查失败", e);
            data.put("connected", false);
            data.put("error", e.getMessage());
            return Result.error("数据库连接失败: " + e.getMessage());
        }
    }
}

