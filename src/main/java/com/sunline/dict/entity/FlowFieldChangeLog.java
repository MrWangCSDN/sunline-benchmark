package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("flow_field_change_log")
public class FlowFieldChangeLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String dedupKey;
    private String webhookUuid;
    private Long projectId;
    private String projectName;
    private String branch;
    private String filePath;
    private String flowId;
    private String flowLongname;
    private String fileChangeType;
    private String captureStatus;
    private String errorMessage;
    private String beforeSha;
    private String afterSha;
    private String commitSha;
    private String commitMessage;
    private String commitAuthor;
    private String commitEmail;
    private LocalDateTime commitTime;
    private Integer addCount;
    private Integer modifyCount;
    private Integer removeCount;
    private Integer inputChangeCount;
    private Integer outputChangeCount;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    public String getWebhookUuid() { return webhookUuid; }
    public void setWebhookUuid(String webhookUuid) { this.webhookUuid = webhookUuid; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }
    public String getFlowLongname() { return flowLongname; }
    public void setFlowLongname(String flowLongname) { this.flowLongname = flowLongname; }
    public String getFileChangeType() { return fileChangeType; }
    public void setFileChangeType(String fileChangeType) { this.fileChangeType = fileChangeType; }
    public String getCaptureStatus() { return captureStatus; }
    public void setCaptureStatus(String captureStatus) { this.captureStatus = captureStatus; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getBeforeSha() { return beforeSha; }
    public void setBeforeSha(String beforeSha) { this.beforeSha = beforeSha; }
    public String getAfterSha() { return afterSha; }
    public void setAfterSha(String afterSha) { this.afterSha = afterSha; }
    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
    public String getCommitMessage() { return commitMessage; }
    public void setCommitMessage(String commitMessage) { this.commitMessage = commitMessage; }
    public String getCommitAuthor() { return commitAuthor; }
    public void setCommitAuthor(String commitAuthor) { this.commitAuthor = commitAuthor; }
    public String getCommitEmail() { return commitEmail; }
    public void setCommitEmail(String commitEmail) { this.commitEmail = commitEmail; }
    public LocalDateTime getCommitTime() { return commitTime; }
    public void setCommitTime(LocalDateTime commitTime) { this.commitTime = commitTime; }
    public Integer getAddCount() { return addCount; }
    public void setAddCount(Integer addCount) { this.addCount = addCount; }
    public Integer getModifyCount() { return modifyCount; }
    public void setModifyCount(Integer modifyCount) { this.modifyCount = modifyCount; }
    public Integer getRemoveCount() { return removeCount; }
    public void setRemoveCount(Integer removeCount) { this.removeCount = removeCount; }
    public Integer getInputChangeCount() { return inputChangeCount; }
    public void setInputChangeCount(Integer inputChangeCount) { this.inputChangeCount = inputChangeCount; }
    public Integer getOutputChangeCount() { return outputChangeCount; }
    public void setOutputChangeCount(Integer outputChangeCount) { this.outputChangeCount = outputChangeCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
