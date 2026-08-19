package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("flow_field_scan_cursor")
public class FlowFieldScanCursor {

    private Long projectId;
    private String branch;
    private String projectName;
    private String projectPath;
    private LocalDateTime lastSuccessEnd;
    private Long lastRunId;
    private LocalDateTime updateTime;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getProjectPath() { return projectPath; }
    public void setProjectPath(String projectPath) { this.projectPath = projectPath; }
    public LocalDateTime getLastSuccessEnd() { return lastSuccessEnd; }
    public void setLastSuccessEnd(LocalDateTime lastSuccessEnd) { this.lastSuccessEnd = lastSuccessEnd; }
    public Long getLastRunId() { return lastRunId; }
    public void setLastRunId(Long lastRunId) { this.lastRunId = lastRunId; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
