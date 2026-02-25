package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 域清单数据实体（在途）
 */
@TableName("domain_data_ing")
public class DomainDataIng {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Integer domainNumber;
    private String domainType;
    private String domainGroup;
    private String chineseName;
    private String englishName;
    private String englishAbbr;
    private String domainDefinition;
    private String dataFormat;
    private String domainRule;
    private String valueRange;
    private String domainSource;
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
    
    public Integer getDomainNumber() {
        return domainNumber;
    }
    
    public void setDomainNumber(Integer domainNumber) {
        this.domainNumber = domainNumber;
    }
    
    public String getDomainType() {
        return domainType;
    }
    
    public void setDomainType(String domainType) {
        this.domainType = domainType;
    }
    
    public String getDomainGroup() {
        return domainGroup;
    }
    
    public void setDomainGroup(String domainGroup) {
        this.domainGroup = domainGroup;
    }
    
    public String getChineseName() {
        return chineseName;
    }
    
    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }
    
    public String getEnglishName() {
        return englishName;
    }
    
    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }
    
    public String getEnglishAbbr() {
        return englishAbbr;
    }
    
    public void setEnglishAbbr(String englishAbbr) {
        this.englishAbbr = englishAbbr;
    }
    
    public String getDomainDefinition() {
        return domainDefinition;
    }
    
    public void setDomainDefinition(String domainDefinition) {
        this.domainDefinition = domainDefinition;
    }
    
    public String getDataFormat() {
        return dataFormat;
    }
    
    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }
    
    public String getDomainRule() {
        return domainRule;
    }
    
    public void setDomainRule(String domainRule) {
        this.domainRule = domainRule;
    }
    
    public String getValueRange() {
        return valueRange;
    }
    
    public void setValueRange(String valueRange) {
        this.valueRange = valueRange;
    }
    
    public String getDomainSource() {
        return domainSource;
    }
    
    public void setDomainSource(String domainSource) {
        this.domainSource = domainSource;
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

