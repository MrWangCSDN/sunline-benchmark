package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 服务明细实体（对应 serviceType/service/interface 下的 input/output field 标签）
 * 每条记录对应一个 field 字段，input 字段填充 interface_input_* 列，output 字段填充 interface_output_* 列
 */
@TableName("service_detail")
public class ServiceDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 serviceType.id */
    private String serviceTypeId;

    /** service.id */
    private String serviceId;

    /** service.name（可选） */
    private String serviceName;

    /** service.longname（可选） */
    private String serviceLongname;

    /** input/field.id（可选） */
    private String interfaceInputFieldId;

    /** input/field.longname（可选） */
    private String interfaceInputFieldLongname;

    /** input/field.type（可选） */
    private String interfaceInputFieldType;

    /** input/field.required（可选） */
    private String interfaceInputFieldRequired;

    /** input/field.multi（可选） */
    private String interfaceInputFieldMulti;

    /** output/field.id（可选） */
    private String interfaceOutputFieldId;

    /** output/field.longname（可选） */
    private String interfaceOutputFieldLongname;

    /** output/field.type（可选） */
    private String interfaceOutputFieldType;

    /** output/field.required（可选） */
    private String interfaceOutputFieldRequired;

    /** output/field.multi（可选） */
    private String interfaceOutputFieldMulti;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getServiceTypeId() { return serviceTypeId; }
    public void setServiceTypeId(String serviceTypeId) { this.serviceTypeId = serviceTypeId; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getServiceLongname() { return serviceLongname; }
    public void setServiceLongname(String serviceLongname) { this.serviceLongname = serviceLongname; }

    public String getInterfaceInputFieldId() { return interfaceInputFieldId; }
    public void setInterfaceInputFieldId(String interfaceInputFieldId) { this.interfaceInputFieldId = interfaceInputFieldId; }

    public String getInterfaceInputFieldLongname() { return interfaceInputFieldLongname; }
    public void setInterfaceInputFieldLongname(String interfaceInputFieldLongname) { this.interfaceInputFieldLongname = interfaceInputFieldLongname; }

    public String getInterfaceInputFieldType() { return interfaceInputFieldType; }
    public void setInterfaceInputFieldType(String interfaceInputFieldType) { this.interfaceInputFieldType = interfaceInputFieldType; }

    public String getInterfaceInputFieldRequired() { return interfaceInputFieldRequired; }
    public void setInterfaceInputFieldRequired(String interfaceInputFieldRequired) { this.interfaceInputFieldRequired = interfaceInputFieldRequired; }

    public String getInterfaceInputFieldMulti() { return interfaceInputFieldMulti; }
    public void setInterfaceInputFieldMulti(String interfaceInputFieldMulti) { this.interfaceInputFieldMulti = interfaceInputFieldMulti; }

    public String getInterfaceOutputFieldId() { return interfaceOutputFieldId; }
    public void setInterfaceOutputFieldId(String interfaceOutputFieldId) { this.interfaceOutputFieldId = interfaceOutputFieldId; }

    public String getInterfaceOutputFieldLongname() { return interfaceOutputFieldLongname; }
    public void setInterfaceOutputFieldLongname(String interfaceOutputFieldLongname) { this.interfaceOutputFieldLongname = interfaceOutputFieldLongname; }

    public String getInterfaceOutputFieldType() { return interfaceOutputFieldType; }
    public void setInterfaceOutputFieldType(String interfaceOutputFieldType) { this.interfaceOutputFieldType = interfaceOutputFieldType; }

    public String getInterfaceOutputFieldRequired() { return interfaceOutputFieldRequired; }
    public void setInterfaceOutputFieldRequired(String interfaceOutputFieldRequired) { this.interfaceOutputFieldRequired = interfaceOutputFieldRequired; }

    public String getInterfaceOutputFieldMulti() { return interfaceOutputFieldMulti; }
    public void setInterfaceOutputFieldMulti(String interfaceOutputFieldMulti) { this.interfaceOutputFieldMulti = interfaceOutputFieldMulti; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "ServiceDetail{serviceTypeId='" + serviceTypeId + "', serviceId='" + serviceId + "'}";
    }
}
