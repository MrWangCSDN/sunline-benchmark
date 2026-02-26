package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 字典类型明细实体（对应 .d_schema.xml 中 complexType 下的 element 标签）
 */
@TableName("dict_detail")
public class DictDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 schema.id */
    private String dictId;

    /** complexType.id */
    private String dictComplexTypeId;

    /** complexType.longname */
    private String dictComplexTypeLongname;

    /** element.id */
    private String elementId;

    /** element.longname */
    private String elementLongname;

    /** element.dbname（可选） */
    private String elementDbname;

    /** element.desc 描述（可选） */
    private String elementDesc;

    /** element.versionType（可选） */
    private String elementVersionType;

    /** element.type 来源类型 */
    private String elementType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDictId() { return dictId; }
    public void setDictId(String dictId) { this.dictId = dictId; }

    public String getDictComplexTypeId() { return dictComplexTypeId; }
    public void setDictComplexTypeId(String dictComplexTypeId) { this.dictComplexTypeId = dictComplexTypeId; }

    public String getDictComplexTypeLongname() { return dictComplexTypeLongname; }
    public void setDictComplexTypeLongname(String dictComplexTypeLongname) { this.dictComplexTypeLongname = dictComplexTypeLongname; }

    public String getElementId() { return elementId; }
    public void setElementId(String elementId) { this.elementId = elementId; }

    public String getElementLongname() { return elementLongname; }
    public void setElementLongname(String elementLongname) { this.elementLongname = elementLongname; }

    public String getElementDbname() { return elementDbname; }
    public void setElementDbname(String elementDbname) { this.elementDbname = elementDbname; }

    public String getElementDesc() { return elementDesc; }
    public void setElementDesc(String elementDesc) { this.elementDesc = elementDesc; }

    public String getElementVersionType() { return elementVersionType; }
    public void setElementVersionType(String elementVersionType) { this.elementVersionType = elementVersionType; }

    public String getElementType() { return elementType; }
    public void setElementType(String elementType) { this.elementType = elementType; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "DictDetail{dictId='" + dictId + "', dictComplexTypeId='" + dictComplexTypeId
                + "', elementId='" + elementId + "', elementLongname='" + elementLongname + "'}";
    }
}
