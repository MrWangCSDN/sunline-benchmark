package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 枚举类型明细实体（对应 .e_schema.xml 中 restrictionType 下的 enumeration 标签）
 * 每条记录对应一个 enumeration 枚举项，携带所属 restrictionType 的信息
 */
@TableName("eschema_detail")
public class EschemaDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 schema.id */
    private String eschemaId;

    /** restrictionType.id */
    private String restrictionTypeId;

    /** restrictionType.longname（可选） */
    private String restrictionTypeLongname;

    /** restrictionType.base（可选） */
    private String restrictionTypeBase;

    /** enumeration.id（可选） */
    private String enumerationId;

    /** enumeration.value（可选） */
    private String enumerationValue;

    /** enumeration.longname（可选） */
    private String enumerationLongname;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEschemaId() { return eschemaId; }
    public void setEschemaId(String eschemaId) { this.eschemaId = eschemaId; }

    public String getRestrictionTypeId() { return restrictionTypeId; }
    public void setRestrictionTypeId(String restrictionTypeId) { this.restrictionTypeId = restrictionTypeId; }

    public String getRestrictionTypeLongname() { return restrictionTypeLongname; }
    public void setRestrictionTypeLongname(String restrictionTypeLongname) { this.restrictionTypeLongname = restrictionTypeLongname; }

    public String getRestrictionTypeBase() { return restrictionTypeBase; }
    public void setRestrictionTypeBase(String restrictionTypeBase) { this.restrictionTypeBase = restrictionTypeBase; }

    public String getEnumerationId() { return enumerationId; }
    public void setEnumerationId(String enumerationId) { this.enumerationId = enumerationId; }

    public String getEnumerationValue() { return enumerationValue; }
    public void setEnumerationValue(String enumerationValue) { this.enumerationValue = enumerationValue; }

    public String getEnumerationLongname() { return enumerationLongname; }
    public void setEnumerationLongname(String enumerationLongname) { this.enumerationLongname = enumerationLongname; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "EschemaDetail{eschemaId='" + eschemaId + "', restrictionTypeId='" + restrictionTypeId
                + "', enumerationId='" + enumerationId + "', enumerationValue='" + enumerationValue + "'}";
    }
}
