package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 枚举映射导入历史记录实体
 */
@TableName("enum_import_history")
public class EnumImportHistory {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("version")
    private String version;              // 版本号（V1, V2, V3...）
    
    @TableField("modifier")
    private String modifier;             // 修改人
    
    @TableField("modify_content")
    private String modifyContent;         // 修改内容
    
    @TableField("import_time")
    private LocalDateTime importTime;     // 导入时间
    
    @TableField("record_count")
    private Integer recordCount;         // 导入记录数
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getModifier() {
        return modifier;
    }
    
    public void setModifier(String modifier) {
        this.modifier = modifier;
    }
    
    public String getModifyContent() {
        return modifyContent;
    }
    
    public void setModifyContent(String modifyContent) {
        this.modifyContent = modifyContent;
    }
    
    public LocalDateTime getImportTime() {
        return importTime;
    }
    
    public void setImportTime(LocalDateTime importTime) {
        this.importTime = importTime;
    }
    
    public Integer getRecordCount() {
        return recordCount;
    }
    
    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }
}

