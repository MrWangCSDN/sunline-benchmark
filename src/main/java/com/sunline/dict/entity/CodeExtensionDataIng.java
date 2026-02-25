package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 代码扩展清单数据实体（在途）
 */
@TableName("code_extension_data_ing")
public class CodeExtensionDataIng {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String codeDomainNumber;
    private String codeDomainChineseName;
    private String codeValue;
    private String valueChineseName;
    private String codeEnglishName;
    private String codeEnglishAbbr;
    private String codeDescription;
    private String domainRule;
    private String codeDomainSource;
    private String sourceNumber;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getCodeDomainNumber() {
        return codeDomainNumber;
    }
    
    public void setCodeDomainNumber(String codeDomainNumber) {
        this.codeDomainNumber = codeDomainNumber;
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
    
    public String getValueChineseName() {
        return valueChineseName;
    }
    
    public void setValueChineseName(String valueChineseName) {
        this.valueChineseName = valueChineseName;
    }
    
    public String getCodeEnglishName() {
        return codeEnglishName;
    }
    
    public void setCodeEnglishName(String codeEnglishName) {
        this.codeEnglishName = codeEnglishName;
    }
    
    public String getCodeEnglishAbbr() {
        return codeEnglishAbbr;
    }
    
    public void setCodeEnglishAbbr(String codeEnglishAbbr) {
        this.codeEnglishAbbr = codeEnglishAbbr;
    }
    
    public String getCodeDescription() {
        return codeDescription;
    }
    
    public void setCodeDescription(String codeDescription) {
        this.codeDescription = codeDescription;
    }
    
    public String getDomainRule() {
        return domainRule;
    }
    
    public void setDomainRule(String domainRule) {
        this.domainRule = domainRule;
    }
    
    public String getCodeDomainSource() {
        return codeDomainSource;
    }
    
    public void setCodeDomainSource(String codeDomainSource) {
        this.codeDomainSource = codeDomainSource;
    }
    
    public String getSourceNumber() {
        return sourceNumber;
    }
    
    public void setSourceNumber(String sourceNumber) {
        this.sourceNumber = sourceNumber;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
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
}

