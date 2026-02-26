package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自定义类型明细实体（对应 .u_schema.xml 中 restrictionType 标签）
 */
@TableName("uschema_detail")
public class UschemaDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 schema.id */
    private String uschemaId;

    /** restrictionType.id */
    private String restrictionTypeId;

    /** restrictionType.longname */
    private String restrictionTypeLongname;

    /** restrictionType.base（可选） */
    private String restrictionTypeBase;

    /** restrictionType.minLength（可选） */
    private String restrictionTypeMinLength;

    /** restrictionType.maxLength（可选） */
    private String restrictionTypeMaxLength;

    /** restrictionType.fractionDigits（可选） */
    private String restrictionTypeFractionDigits;

    /** restrictionType.dbLength（可选） */
    private String restrictionTypeDbLength;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUschemaId() { return uschemaId; }
    public void setUschemaId(String uschemaId) { this.uschemaId = uschemaId; }

    public String getRestrictionTypeId() { return restrictionTypeId; }
    public void setRestrictionTypeId(String restrictionTypeId) { this.restrictionTypeId = restrictionTypeId; }

    public String getRestrictionTypeLongname() { return restrictionTypeLongname; }
    public void setRestrictionTypeLongname(String restrictionTypeLongname) { this.restrictionTypeLongname = restrictionTypeLongname; }

    public String getRestrictionTypeBase() { return restrictionTypeBase; }
    public void setRestrictionTypeBase(String restrictionTypeBase) { this.restrictionTypeBase = restrictionTypeBase; }

    public String getRestrictionTypeMinLength() { return restrictionTypeMinLength; }
    public void setRestrictionTypeMinLength(String restrictionTypeMinLength) { this.restrictionTypeMinLength = restrictionTypeMinLength; }

    public String getRestrictionTypeMaxLength() { return restrictionTypeMaxLength; }
    public void setRestrictionTypeMaxLength(String restrictionTypeMaxLength) { this.restrictionTypeMaxLength = restrictionTypeMaxLength; }

    public String getRestrictionTypeFractionDigits() { return restrictionTypeFractionDigits; }
    public void setRestrictionTypeFractionDigits(String restrictionTypeFractionDigits) { this.restrictionTypeFractionDigits = restrictionTypeFractionDigits; }

    public String getRestrictionTypeDbLength() { return restrictionTypeDbLength; }
    public void setRestrictionTypeDbLength(String restrictionTypeDbLength) { this.restrictionTypeDbLength = restrictionTypeDbLength; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "UschemaDetail{uschemaId='" + uschemaId + "', restrictionTypeId='" + restrictionTypeId
                + "', restrictionTypeLongname='" + restrictionTypeLongname + "'}";
    }
}
