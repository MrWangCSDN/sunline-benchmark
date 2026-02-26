package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 服务实体（对应 .pcs.xml / .pbs.xml 文件的 serviceType 标签）
 */
@TableName("service")
public class ServiceFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** serviceType.id 属性 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** serviceType.longname 属性 */
    private String longname;

    /** serviceType.package 属性 */
    private String packagePath;

    /** serviceType.kind 属性 */
    private String kind;

    /** serviceType.outBound 属性（可选） */
    private String outBound;

    /** 服务类型：pcs / pbs（由文件后缀决定） */
    private String serviceType;

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

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getOutBound() { return outBound; }
    public void setOutBound(String outBound) { this.outBound = outBound; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getFromJar() { return fromJar; }
    public void setFromJar(String fromJar) { this.fromJar = fromJar; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "ServiceFile{id='" + id + "', longname='" + longname + "', serviceType='" + serviceType + "', fromJar='" + fromJar + "'}";
    }
}
