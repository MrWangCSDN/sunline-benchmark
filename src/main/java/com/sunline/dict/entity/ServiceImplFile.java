package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 服务实现实体（对应 .pcsImpl/.pbsImpl/.pbcbImpl/.pbcpImpl/.pbccImpl/.pbctImpl.xml 文件的 serviceImpl 标签）
 */
@TableName("serviceImpl")
public class ServiceImplFile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** serviceImpl.id 属性 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** serviceImpl.longname 属性（可选） */
    private String longname;

    /** serviceImpl.package 属性（可选） */
    private String packagePath;

    /** serviceImpl.serviceType 属性（可选） */
    private String serviceType;

    /** 服务实现类型：pcsImpl / pbsImpl / pbcbImpl / pbcpImpl / pbccImpl / pbctImpl（由文件后缀决定） */
    private String serviceImplType;

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

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getServiceImplType() { return serviceImplType; }
    public void setServiceImplType(String serviceImplType) { this.serviceImplType = serviceImplType; }

    public String getFromJar() { return fromJar; }
    public void setFromJar(String fromJar) { this.fromJar = fromJar; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "ServiceImplFile{id='" + id + "', longname='" + longname + "', serviceImplType='" + serviceImplType + "', fromJar='" + fromJar + "'}";
    }
}
