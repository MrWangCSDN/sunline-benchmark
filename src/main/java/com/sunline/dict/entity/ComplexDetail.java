package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 复合类型明细实体（对应 complexType 标签下的 element 标签）
 */
@TableName("complex_detail")
public class ComplexDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 schema.id */
    private String complexId;

    /** complexType.id */
    private String complexPojoId;

    /** complexType.longname */
    private String complexPojoLongname;

    /** element.id */
    private String elementId;

    /** element.longname */
    private String elementLongname;

    /** element.required（true=必输，false=非必输） */
    private String elementRequired;

    /** element.multi（true=多值，false=单值） */
    private String elementMulti;

    /** element.ref（字典来源） */
    private String elementRef;

    /** element.type（来源类型） */
    private String elementType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getComplexId() { return complexId; }
    public void setComplexId(String complexId) { this.complexId = complexId; }

    public String getComplexPojoId() { return complexPojoId; }
    public void setComplexPojoId(String complexPojoId) { this.complexPojoId = complexPojoId; }

    public String getComplexPojoLongname() { return complexPojoLongname; }
    public void setComplexPojoLongname(String complexPojoLongname) { this.complexPojoLongname = complexPojoLongname; }

    public String getElementId() { return elementId; }
    public void setElementId(String elementId) { this.elementId = elementId; }

    public String getElementLongname() { return elementLongname; }
    public void setElementLongname(String elementLongname) { this.elementLongname = elementLongname; }

    public String getElementRequired() { return elementRequired; }
    public void setElementRequired(String elementRequired) { this.elementRequired = elementRequired; }

    public String getElementMulti() { return elementMulti; }
    public void setElementMulti(String elementMulti) { this.elementMulti = elementMulti; }

    public String getElementRef() { return elementRef; }
    public void setElementRef(String elementRef) { this.elementRef = elementRef; }

    public String getElementType() { return elementType; }
    public void setElementType(String elementType) { this.elementType = elementType; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "ComplexDetail{complexId='" + complexId + "', complexPojoId='" + complexPojoId
                + "', elementId='" + elementId + "', elementLongname='" + elementLongname + "'}";
    }
}
