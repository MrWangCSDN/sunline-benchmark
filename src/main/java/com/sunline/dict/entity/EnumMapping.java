package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 枚举映射关系实体
 * 用于维护域英文简称和代码含义英文简称的映射关系
 */
@TableName("enum_mapping")
public class EnumMapping {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("domain_english_abbr")
    private String domainEnglishAbbr;           // 域英文简称
    
    @TableField("enum_field_id")
    private String enumFieldId;                 // 枚举字段ID
    
    @TableField("domain_chinese_name")
    private String domainChineseName;           // 域中文名称
    
    @TableField("code_value")
    private String codeValue;                   // 代码取值
    
    @TableField("value_chinese_name")
    private String valueChineseName;            // 取值含义中文名称
    
    @TableField("code_description")
    private String codeDescription;             // 代码描述
    
    @TableField("create_time")
    private LocalDateTime createTime;           // 创建时间
    
    @TableField("update_time")
    private LocalDateTime updateTime;           // 更新时间
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getDomainEnglishAbbr() {
        return domainEnglishAbbr;
    }
    
    public void setDomainEnglishAbbr(String domainEnglishAbbr) {
        this.domainEnglishAbbr = domainEnglishAbbr;
    }
    
    public String getEnumFieldId() {
        return enumFieldId;
    }
    
    public void setEnumFieldId(String enumFieldId) {
        this.enumFieldId = enumFieldId;
    }
    
    public String getDomainChineseName() {
        return domainChineseName;
    }
    
    public void setDomainChineseName(String domainChineseName) {
        this.domainChineseName = domainChineseName;
    }
    
    public String getCodeValue() {
        return codeValue;
    }
    
    public void setCodeValue(String codeValue) {
        this.codeValue = codeValue;
    }
    
    public String getValueChineseName() {
        return valueChineseName;
    }
    
    public void setValueChineseName(String valueChineseName) {
        this.valueChineseName = valueChineseName;
    }
    
    public String getCodeDescription() {
        return codeDescription;
    }
    
    public void setCodeDescription(String codeDescription) {
        this.codeDescription = codeDescription;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    @Override
    public String toString() {
        return "EnumMapping{" +
                "id=" + id +
                ", domainEnglishAbbr='" + domainEnglishAbbr + '\'' +
                ", enumFieldId='" + enumFieldId + '\'' +
                ", domainChineseName='" + domainChineseName + '\'' +
                ", codeValue='" + codeValue + '\'' +
                ", valueChineseName='" + valueChineseName + '\'' +
                ", codeDescription='" + codeDescription + '\'' +
                '}';
    }
}

