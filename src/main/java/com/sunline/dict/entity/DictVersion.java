package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典版本实体类
 */
@TableName("dict_version")
public class DictVersion implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String versionNumber;
    private LocalDateTime importTime;
    private String fileName;
    private Long fileSize;
    private String fileMd5;
    private Integer totalCount;
    private Integer newCount;
    private Integer updateCount;
    private Integer deleteCount;
    private Integer unchangedCount;
    private String operator;
    private String remark;
    private String status;
    private LocalDateTime createTime;
    
    // Getter and Setter methods
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getVersionNumber() {
        return versionNumber;
    }
    
    public void setVersionNumber(String versionNumber) {
        this.versionNumber = versionNumber;
    }
    
    public LocalDateTime getImportTime() {
        return importTime;
    }
    
    public void setImportTime(LocalDateTime importTime) {
        this.importTime = importTime;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getFileMd5() {
        return fileMd5;
    }
    
    public void setFileMd5(String fileMd5) {
        this.fileMd5 = fileMd5;
    }
    
    public Integer getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
    
    public Integer getNewCount() {
        return newCount;
    }
    
    public void setNewCount(Integer newCount) {
        this.newCount = newCount;
    }
    
    public Integer getUpdateCount() {
        return updateCount;
    }
    
    public void setUpdateCount(Integer updateCount) {
        this.updateCount = updateCount;
    }
    
    public Integer getDeleteCount() {
        return deleteCount;
    }
    
    public void setDeleteCount(Integer deleteCount) {
        this.deleteCount = deleteCount;
    }
    
    public Integer getUnchangedCount() {
        return unchangedCount;
    }
    
    public void setUnchangedCount(Integer unchangedCount) {
        this.unchangedCount = unchangedCount;
    }
    
    public String getOperator() {
        return operator;
    }
    
    public void setOperator(String operator) {
        this.operator = operator;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}

