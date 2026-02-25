package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 代码扩展清单变更日志实体类
 */
@TableName("code_extension_change_log")
public class CodeExtensionChangeLog implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long versionId;
    private String codeDomainChineseName;
    private String codeValue;
    private String changeType;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private LocalDateTime changeTime;
    
    // Getter and Setter methods
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Long getVersionId() {
        return versionId;
    }
    
    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }
    
    public String getCodeDomainChineseName() {
        return codeDomainChineseName;
    }
    
    public void setCodeDomainChineseName(String codeDomainChineseName) {
        this.codeDomainChineseName = codeDomainChineseName;
    }
    
    public String getCodeValue() {
        return codeValue;
    }
    
    public void setCodeValue(String codeValue) {
        this.codeValue = codeValue;
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public String getOldValue() {
        return oldValue;
    }
    
    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }
    
    public String getNewValue() {
        return newValue;
    }
    
    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }
    
    public LocalDateTime getChangeTime() {
        return changeTime;
    }
    
    public void setChangeTime(LocalDateTime changeTime) {
        this.changeTime = changeTime;
    }
}

