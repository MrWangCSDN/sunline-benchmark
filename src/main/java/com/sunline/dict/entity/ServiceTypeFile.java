package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ServiceType文件信息实体类
 */
@TableName("service_type_file")
public class ServiceTypeFile implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * ServiceType ID（来源于XML中<serviceType>节点的id属性）
     */
    private String serviceTypeId;
    
    /**
     * ServiceType长名称（来源于XML中<serviceType>节点的longname属性）
     */
    private String serviceTypeLongName;
    
    /**
     * ServiceType类型（从文件名提取，如pbs、pcs、pbcb等）
     */
    private String serviceTypeKind;
    
    /**
     * 来源jar包名称（不含版本号）
     */
    private String serviceTypeFromJar;
    
    /**
     * ServiceType包路径（来源于XML中<serviceType>节点的package属性）
     */
    private String serviceTypePackage;
    
    /**
     * Service ID（来源于XML中<service>节点的id属性）
     */
    private String serviceId;
    
    /**
     * Service名称（来源于XML中<service>节点的name属性）
     */
    private String serviceName;
    
    /**
     * Service长名称（来源于XML中<service>节点的longname属性）
     */
    private String serviceLongName;
    
    /**
     * 创建时间
     */
    /**
     * 错误调用列表（违规的接口调用，格式：接口ID(分层-领域)）
     */
    private String incorrectCalls;
    
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
    
    public String getServiceTypeId() {
        return serviceTypeId;
    }
    
    public void setServiceTypeId(String serviceTypeId) {
        this.serviceTypeId = serviceTypeId;
    }
    
    public String getServiceTypeLongName() {
        return serviceTypeLongName;
    }
    
    public void setServiceTypeLongName(String serviceTypeLongName) {
        this.serviceTypeLongName = serviceTypeLongName;
    }
    
    public String getServiceTypeKind() {
        return serviceTypeKind;
    }
    
    public void setServiceTypeKind(String serviceTypeKind) {
        this.serviceTypeKind = serviceTypeKind;
    }
    
    public String getServiceTypeFromJar() {
        return serviceTypeFromJar;
    }
    
    public void setServiceTypeFromJar(String serviceTypeFromJar) {
        this.serviceTypeFromJar = serviceTypeFromJar;
    }
    
    public String getServiceTypePackage() {
        return serviceTypePackage;
    }
    
    public void setServiceTypePackage(String serviceTypePackage) {
        this.serviceTypePackage = serviceTypePackage;
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getServiceLongName() {
        return serviceLongName;
    }
    
    public void setServiceLongName(String serviceLongName) {
        this.serviceLongName = serviceLongName;
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
    
    
    public String getIncorrectCalls() {
        return incorrectCalls;
    }
    
    public void setIncorrectCalls(String incorrectCalls) {
        this.incorrectCalls = incorrectCalls;
    }
    @Override
    public String toString() {
        return "ServiceTypeFile{" +
                "id=" + id +
                ", serviceTypeId='" + serviceTypeId + '\'' +
                ", serviceTypeLongName='" + serviceTypeLongName + '\'' +
                ", serviceTypeKind='" + serviceTypeKind + '\'' +
                ", serviceTypeFromJar='" + serviceTypeFromJar + '\'' +
                ", serviceTypePackage='" + serviceTypePackage + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", serviceLongName='" + serviceLongName + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
