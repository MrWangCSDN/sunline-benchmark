package com.sunline.dict.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工程编译配置
 */
@Component
@ConfigurationProperties(prefix = "build")
public class BuildConfig {

    private String basePath = "/home/cbs/code";
    private String logPath = "/home/cbs/logs/build";
    private String mvnHome = "";
    private String settingsFile = "";
    private int buildTimeoutMinutes = 30;
    private SuccessCallbackConfig successCallback = new SuccessCallbackConfig();
    private List<BatchConfig> batches = new ArrayList<>();

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public String getLogPath() { return logPath; }
    public void setLogPath(String logPath) { this.logPath = logPath; }

    public String getMvnHome() { return mvnHome; }
    public void setMvnHome(String mvnHome) { this.mvnHome = mvnHome; }

    public String getSettingsFile() { return settingsFile; }
    public void setSettingsFile(String settingsFile) { this.settingsFile = settingsFile; }

    public int getBuildTimeoutMinutes() { return buildTimeoutMinutes; }
    public void setBuildTimeoutMinutes(int buildTimeoutMinutes) { this.buildTimeoutMinutes = buildTimeoutMinutes; }

    public SuccessCallbackConfig getSuccessCallback() { return successCallback; }
    public void setSuccessCallback(SuccessCallbackConfig successCallback) { this.successCallback = successCallback; }

    public List<BatchConfig> getBatches() { return batches; }
    public void setBatches(List<BatchConfig> batches) { this.batches = batches; }

    /**
     * 全量任务成功后的回调配置
     */
    public static class SuccessCallbackConfig {
        private boolean enabled = true;
        private String url = "http://127.0.0.1:8123/api/neo4j/build/async";
        private int timeoutSeconds = 10;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    /**
     * 单批次配置
     */
    public static class BatchConfig {
        private String name;
        private boolean parallel = false;
        private List<String> projects = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public boolean isParallel() { return parallel; }
        public void setParallel(boolean parallel) { this.parallel = parallel; }

        public List<String> getProjects() { return projects; }
        public void setProjects(List<String> projects) { this.projects = projects; }
    }
}
