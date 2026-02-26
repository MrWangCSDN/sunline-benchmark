package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表定义索引实体（对应 .tables.xml 中 table/odbindexes/index 与 table/indexes/index 标签）
 * 每条记录对应一张表下的一个索引项（odbindex 或 index，未填充的字段为空）
 */
@TableName("metadata_tables_indexes")
public class MetadataTablesIndexes implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 schema.id */
    private String metadataTablesId;

    /** table.id */
    private String tableId;

    /** odbindexes/index.id（可选） */
    private String odbindexId;

    /** odbindexes/index.type（可选） */
    private String odbindexType;

    /** odbindexes/index.fields（可选） */
    private String odbindexFields;

    /** odbindexes/index.operate（可选） */
    private String odbindexOperate;

    /** indexes/index.id（可选） */
    private String indexId;

    /** indexes/index.type（可选） */
    private String indexType;

    /** indexes/index.fields（可选） */
    private String indexFields;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMetadataTablesId() { return metadataTablesId; }
    public void setMetadataTablesId(String metadataTablesId) { this.metadataTablesId = metadataTablesId; }

    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }

    public String getOdbindexId() { return odbindexId; }
    public void setOdbindexId(String odbindexId) { this.odbindexId = odbindexId; }

    public String getOdbindexType() { return odbindexType; }
    public void setOdbindexType(String odbindexType) { this.odbindexType = odbindexType; }

    public String getOdbindexFields() { return odbindexFields; }
    public void setOdbindexFields(String odbindexFields) { this.odbindexFields = odbindexFields; }

    public String getOdbindexOperate() { return odbindexOperate; }
    public void setOdbindexOperate(String odbindexOperate) { this.odbindexOperate = odbindexOperate; }

    public String getIndexId() { return indexId; }
    public void setIndexId(String indexId) { this.indexId = indexId; }

    public String getIndexType() { return indexType; }
    public void setIndexType(String indexType) { this.indexType = indexType; }

    public String getIndexFields() { return indexFields; }
    public void setIndexFields(String indexFields) { this.indexFields = indexFields; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "MetadataTablesIndexes{metadataTablesId='" + metadataTablesId + "', tableId='" + tableId
                + "', odbindexId='" + odbindexId + "', indexId='" + indexId + "'}";
    }
}
