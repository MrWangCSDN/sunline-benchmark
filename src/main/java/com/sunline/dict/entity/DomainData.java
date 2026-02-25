package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 域清单数据实体
 * 严格13个字段：域编号、域类型、域组、域中文名称、域英文名称、域英文简称、域定义、数据格式、域规则、取值范围、域来源、来源编号、备注
 */
@TableName("domain_data")
public class DomainData {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Integer domainNumber;           // 1. 域编号
    private String domainType;              // 2. 域类型
    private String domainGroup;             // 3. 域组
    private String chineseName;             // 4. 域中文名称
    private String englishName;             // 5. 域英文名称
    private String englishAbbr;             // 6. 域英文简称
    private String domainDefinition;        // 7. 域定义
    private String dataFormat;              // 8. 数据格式
    private String domainRule;              // 9. 域规则
    private String valueRange;              // 10. 取值范围
    private String domainSource;            // 11. 域来源
    private String sourceNumber;            // 12. 来源编号
    private String remark;                  // 13. 备注
    private LocalDateTime createTime;       // 创建时间
    private LocalDateTime updateTime;       // 更新时间
    
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
    
    @Override
    public String toString() {
        return "DomainData{" +
                "id=" + id +
                ", domainNumber=" + domainNumber +
                ", domainType='" + domainType + '\'' +
                ", domainGroup='" + domainGroup + '\'' +
                ", chineseName='" + chineseName + '\'' +
                ", englishName='" + englishName + '\'' +
                ", englishAbbr='" + englishAbbr + '\'' +
                ", domainDefinition='" + domainDefinition + '\'' +
                ", dataFormat='" + dataFormat + '\'' +
                ", domainRule='" + domainRule + '\'' +
                ", valueRange='" + valueRange + '\'' +
                ", domainSource='" + domainSource + '\'' +
                ", sourceNumber='" + sourceNumber + '\'' +
                ", remark='" + remark + '\'' +
                '}';
    }
}
