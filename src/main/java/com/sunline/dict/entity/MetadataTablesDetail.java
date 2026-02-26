package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表定义字段明细实体（对应 .tables.xml 中 table/fields/field 标签）
 * 每条记录对应一张表下的一个字段
 */
@TableName("metadata_tables_detail")
public class MetadataTablesDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 schema.id */
    private String metadataTablesId;

    /** table.id */
    private String tableId;

    /** table.name */
    private String tableName;

    /** table.longname（可选） */
    private String tableLongname;

    /** table.extension（可选） */
    private String tableExtension;

    /** field.id（可选） */
    private String fieldId;

    /** field.dyname（可选） */
    private String fieldDbname;

    /** field.longname（可选） */
    private String fieldLongname;

    /** field.type（可选） */
    private String fieldType;

    /** field.nullable（可选） */
    private String fieldNullable;

    /** field.primarykey（可选） */
    private String fieldPrimarykey;

    /** field.ref（可选） */
    private String fieldRef;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMetadataTablesId() { return metadataTablesId; }
    public void setMetadataTablesId(String metadataTablesId) { this.metadataTablesId = metadataTablesId; }

    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getTableLongname() { return tableLongname; }
    public void setTableLongname(String tableLongname) { this.tableLongname = tableLongname; }

    public String getTableExtension() { return tableExtension; }
    public void setTableExtension(String tableExtension) { this.tableExtension = tableExtension; }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public String getFieldDbname() { return fieldDbname; }
    public void setFieldDbname(String fieldDbname) { this.fieldDbname = fieldDbname; }

    public String getFieldLongname() { return fieldLongname; }
    public void setFieldLongname(String fieldLongname) { this.fieldLongname = fieldLongname; }

    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }

    public String getFieldNullable() { return fieldNullable; }
    public void setFieldNullable(String fieldNullable) { this.fieldNullable = fieldNullable; }

    public String getFieldPrimarykey() { return fieldPrimarykey; }
    public void setFieldPrimarykey(String fieldPrimarykey) { this.fieldPrimarykey = fieldPrimarykey; }

    public String getFieldRef() { return fieldRef; }
    public void setFieldRef(String fieldRef) { this.fieldRef = fieldRef; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "MetadataTablesDetail{metadataTablesId='" + metadataTablesId + "', tableId='" + tableId
                + "', fieldId='" + fieldId + "', fieldDbname='" + fieldDbname + "'}";
    }
}
