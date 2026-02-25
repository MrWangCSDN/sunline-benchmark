package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 硬编码方法调用栈实体类
 */
@TableName("hard_code_method_stack")
public class HardCodeMethodStack implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 自增主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * ServiceType ID
     */
    private String serviceTypeId;
    
    /**
     * ServiceImpl ID
     */
    private String serviceTypeImplId;
    
    /**
     * ServiceType类型（如pbs、pcs等）
     */
    private String serviceTypeKind;
    
    /**
     * Service ID
     */
    private String serviceId;
    
    /**
     * Service名称
     */
    private String serviceName;
    
    /**
     * 代码中调用的ServiceType（SysUtil.getInstance/getRemoteInstance中的类名）
     */
    private String codeServiceType;
    
    /**
     * 调用的方法名
     */
    private String codeMethodType;
    
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
    
    public String getServiceTypeId() {
        return serviceTypeId;
    }
    
    public void setServiceTypeId(String serviceTypeId) {
        this.serviceTypeId = serviceTypeId;
    }
    
    public String getServiceTypeImplId() {
        return serviceTypeImplId;
    }
    
    public void setServiceTypeImplId(String serviceTypeImplId) {
        this.serviceTypeImplId = serviceTypeImplId;
    }
    
    public String getServiceTypeKind() {
        return serviceTypeKind;
    }
    
    public void setServiceTypeKind(String serviceTypeKind) {
        this.serviceTypeKind = serviceTypeKind;
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
    
    public String getCodeServiceType() {
        return codeServiceType;
    }
    
    public void setCodeServiceType(String codeServiceType) {
        this.codeServiceType = codeServiceType;
    }
    
    public String getCodeMethodType() {
        return codeMethodType;
    }
    
    public void setCodeMethodType(String codeMethodType) {
        this.codeMethodType = codeMethodType;
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
        return "HardCodeMethodStack{" +
                "id=" + id +
                ", serviceTypeId='" + serviceTypeId + '\'' +
                ", serviceTypeImplId='" + serviceTypeImplId + '\'' +
                ", serviceTypeKind='" + serviceTypeKind + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", codeServiceType='" + codeServiceType + '\'' +
                ", codeMethodType='" + codeMethodType + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}

