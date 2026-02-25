package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ServiceType实现文件信息实体类
 */
@TableName("service_type_impl_file")
public class ServiceTypeImplFile implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * ServiceImpl ID（来源于XML中<serviceImpl>节点的id属性）
     */
    private String serviceTypeImplId;
    
    /**
     * ServiceImpl长名称（来源于XML中<serviceImpl>节点的longname属性）
     */
    private String serviceImplLongName;
    
    /**
     * ServiceImpl类型（从文件名提取，如pbsImpl、pcsImpl、pbcbImpl等）
     */
    private String serviceImplKind;
    
    /**
     * 来源jar包名称（不含版本号）
     */
    private String serviceImplFromJar;
    
    /**
     * ServiceImpl包路径（来源于XML中<serviceImpl>节点的package属性）
     */
    private String serviceImplPackage;
    
    /**
     * ServiceType ID（来源于XML中<serviceImpl>节点的serviceType属性）
     */
    private String serviceTypeId;
    
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
    
    public String getServiceTypeImplId() {
        return serviceTypeImplId;
    }
    
    public void setServiceTypeImplId(String serviceTypeImplId) {
        this.serviceTypeImplId = serviceTypeImplId;
    }
    
    public String getServiceImplLongName() {
        return serviceImplLongName;
    }
    
    public void setServiceImplLongName(String serviceImplLongName) {
        this.serviceImplLongName = serviceImplLongName;
    }
    
    public String getServiceImplKind() {
        return serviceImplKind;
    }
    
    public void setServiceImplKind(String serviceImplKind) {
        this.serviceImplKind = serviceImplKind;
    }
    
    public String getServiceImplFromJar() {
        return serviceImplFromJar;
    }
    
    public void setServiceImplFromJar(String serviceImplFromJar) {
        this.serviceImplFromJar = serviceImplFromJar;
    }
    
    public String getServiceImplPackage() {
        return serviceImplPackage;
    }
    
    public void setServiceImplPackage(String serviceImplPackage) {
        this.serviceImplPackage = serviceImplPackage;
    }
    
    public String getServiceTypeId() {
        return serviceTypeId;
    }
    
    public void setServiceTypeId(String serviceTypeId) {
        this.serviceTypeId = serviceTypeId;
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
        return "ServiceTypeImplFile{" +
                "id=" + id +
                ", serviceTypeImplId='" + serviceTypeImplId + '\'' +
                ", serviceImplLongName='" + serviceImplLongName + '\'' +
                ", serviceImplKind='" + serviceImplKind + '\'' +
                ", serviceImplFromJar='" + serviceImplFromJar + '\'' +
                ", serviceImplPackage='" + serviceImplPackage + '\'' +
                ", serviceTypeId='" + serviceTypeId + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}

