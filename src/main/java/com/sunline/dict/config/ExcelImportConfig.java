package com.sunline.dict.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Excel导入配置
 */
@Configuration
@ConfigurationProperties(prefix = "excel.import")
public class ExcelImportConfig {
    
    /**
     * Excel导入口令（6位数字）
     */
    private String password = "123456";
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    /**
     * 验证口令是否正确
     * @param inputPassword 用户输入的口令
     * @return 是否正确
     */
    public boolean validatePassword(String inputPassword) {
        if (inputPassword == null || inputPassword.trim().isEmpty()) {
            return false;
        }
        return password.equals(inputPassword.trim());
    }
}

