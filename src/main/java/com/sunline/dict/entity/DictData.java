package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典数据实体类
 */
@TableName("dict_data")
public class DictData implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 排序号（Excel中的行号，用于保持顺序）
     */
    private Integer sortOrder;
    
    /**
     * 版本ID
     */
    private Long versionId;
    
    /**
     * 数据MD5值
     */
    private String dataHash;
    
    /**
     * 变更类型：NEW/UPDATE/DELETE/UNCHANGED
     */
    private String changeType;
    
    /**
     * 是否已删除：0-否，1-是
     */
    private Integer isDeleted;
    
    /**
     * 删除时间
     */
    private LocalDateTime deletedAt;
    
    /**
     * 数据项编号
     */
    private String dataItemCode;
    
    /**
     * 英文简称
     */
    private String englishAbbr;
    
    /**
     * 英文名称
     */
    private String englishName;
    
    /**
     * 中文名称
     */
    private String chineseName;
    
    /**
     * 字典属性
     */
    private String dictAttr;
    
    /**
     * 域中文名称
     */
    private String domainChineseName;
    
    /**
     * 数据类型
     */
    private String dataType;
    
    /**
     * 数据格式
     */
    private String dataFormat;
    
    /**
     * 取值范围
     */
    private String valueRange;
    
    /**
     * JAVA/ESF规范命名
     */
    private String javaEsfName;
    
    /**
     * ESF数据格式
     */
    private String esfDataFormat;
    
    /**
     * GaussDB数据格式
     */
    private String gaussdbDataFormat;
    
    /**
     * GoldenDB数据格式
     */
    private String goldendbDataFormat;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    // Getter and Setter methods
    
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
    
    @Override
    public String toString() {
        return "DictData{" +
                "id=" + id +
                ", sortOrder=" + sortOrder +
                ", dataItemCode='" + dataItemCode + '\'' +
                ", englishAbbr='" + englishAbbr + '\'' +
                ", englishName='" + englishName + '\'' +
                ", chineseName='" + chineseName + '\'' +
                ", dictAttr='" + dictAttr + '\'' +
                ", domainChineseName='" + domainChineseName + '\'' +
                ", dataType='" + dataType + '\'' +
                ", dataFormat='" + dataFormat + '\'' +
                ", valueRange='" + valueRange + '\'' +
                ", javaEsfName='" + javaEsfName + '\'' +
                ", esfDataFormat='" + esfDataFormat + '\'' +
                ", gaussdbDataFormat='" + gaussdbDataFormat + '\'' +
                ", goldendbDataFormat='" + goldendbDataFormat + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}

