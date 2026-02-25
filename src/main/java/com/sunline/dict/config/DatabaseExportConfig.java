package com.sunline.dict.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库导出配置
 */
@Component
@ConfigurationProperties(prefix = "database.export")
public class DatabaseExportConfig {
    
    private Map<String, EnvironmentConfig> environments = new HashMap<>();
    
    public Map<String, EnvironmentConfig> getEnvironments() {
        return environments;
    }
    
    public void setEnvironments(Map<String, EnvironmentConfig> environments) {
        this.environments = environments;
    }
    
    public EnvironmentConfig getEnvironment(String envName) {
        return environments.get(envName);
    }
    
    /**
     * 环境配置
     */
    public static class EnvironmentConfig {
        private String url;
        private String username;
        private String password;
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
    }
}

