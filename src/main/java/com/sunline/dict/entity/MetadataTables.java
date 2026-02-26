package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 表定义实体（对应 .tables.xml 文件的 schema 标签）
 */
@TableName("metadata_tables")
public class MetadataTables implements Serializable {

    private static final long serialVersionUID = 1L;

    /** schema.id 属性 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** schema.longname 属性 */
    private String longname;

    /** schema.package 属性 */
    private String packagePath;

    /** 来源文件名（提交的具体文件名） */
    private String fromJar;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLongname() { return longname; }
    public void setLongname(String longname) { this.longname = longname; }

    public String getPackagePath() { return packagePath; }
    public void setPackagePath(String packagePath) { this.packagePath = packagePath; }

    public String getFromJar() { return fromJar; }
    public void setFromJar(String fromJar) { this.fromJar = fromJar; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "MetadataTables{id='" + id + "', longname='" + longname + "', packagePath='" + packagePath + "', fromJar='" + fromJar + "'}";
    }
}
