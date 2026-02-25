package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典数据实体类（在途）
 */
@TableName("dict_data_ing")
public class DictDataIng implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Integer sortOrder;
    private Long versionId;
    private String dataHash;
    private String changeType;
    private Integer isDeleted;
    private LocalDateTime deletedAt;
    private String dataItemCode;
    private String englishAbbr;
    private String englishName;
    private String chineseName;
    private String dictAttr;
    private String domainChineseName;
    private String dataType;
    private String dataFormat;
    private String valueRange;
    private String javaEsfName;
    private String esfDataFormat;
    private String gaussdbDataFormat;
    private String goldendbDataFormat;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Integer getSortOrder() {
        return sortOrder;
    }
    
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
    
    public Long getVersionId() {
        return versionId;
    }
    
    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }
    
    public String getDataHash() {
        return dataHash;
    }
    
    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
    }
    
    public String getChangeType() {
        return changeType;
    }
    
    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }
    
    public Integer getIsDeleted() {
        return isDeleted;
    }
    
    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }
    
    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
    
    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
    
    public String getDataItemCode() {
        return dataItemCode;
    }
    
    public void setDataItemCode(String dataItemCode) {
        this.dataItemCode = dataItemCode;
    }
    
    public String getEnglishAbbr() {
        return englishAbbr;
    }
    
    public void setEnglishAbbr(String englishAbbr) {
        this.englishAbbr = englishAbbr;
    }
    
    public String getEnglishName() {
        return englishName;
    }
    
    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }
    
    public String getChineseName() {
        return chineseName;
    }
    
    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }
    
    public String getDictAttr() {
        return dictAttr;
    }
    
    public void setDictAttr(String dictAttr) {
        this.dictAttr = dictAttr;
    }
    
    public String getDomainChineseName() {
        return domainChineseName;
    }
    
    public void setDomainChineseName(String domainChineseName) {
        this.domainChineseName = domainChineseName;
    }
    
    public String getDataType() {
        return dataType;
    }
    
    public void setDataType(String dataType) {
        this.dataType = dataType;
    }
    
    public String getDataFormat() {
        return dataFormat;
    }
    
    public void setDataFormat(String dataFormat) {
        this.dataFormat = dataFormat;
    }
    
    public String getValueRange() {
        return valueRange;
    }
    
    public void setValueRange(String valueRange) {
        this.valueRange = valueRange;
    }
    
    public String getJavaEsfName() {
        return javaEsfName;
    }
    
    public void setJavaEsfName(String javaEsfName) {
        this.javaEsfName = javaEsfName;
    }
    
    public String getEsfDataFormat() {
        return esfDataFormat;
    }
    
    public void setEsfDataFormat(String esfDataFormat) {
        this.esfDataFormat = esfDataFormat;
    }
    
    public String getGaussdbDataFormat() {
        return gaussdbDataFormat;
    }
    
    public void setGaussdbDataFormat(String gaussdbDataFormat) {
        this.gaussdbDataFormat = gaussdbDataFormat;
    }
    
    public String getGoldendbDataFormat() {
        return goldendbDataFormat;
    }
    
    public void setGoldendbDataFormat(String goldendbDataFormat) {
        this.goldendbDataFormat = goldendbDataFormat;
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

