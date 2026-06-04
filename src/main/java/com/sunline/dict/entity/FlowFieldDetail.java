package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易字段明细表（flow_field_detail）
 * 来源：.flowtrans.xml 的 input/output 平铺
 */
@TableName("flow_field_detail")
public class FlowFieldDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 flowtran.id */
    private String flowId;

    /** 'input' | 'output' */
    private String ioType;

    /** XML <field id="..."> */
    private String fieldId;

    /** type 属性 */
    private String fieldType;

    /** longname 属性 */
    private String longname;

    /** MDict.X.yyy（无 ref 跳过整行） */
    private String ref;

    /** required="true"/"false" */
    private Boolean required;

    /** multi="true"/"false" */
    private Boolean multi;

    /** true = 来自 <fields> 容器内 */
    private Boolean arrayFlag;

    /** 来源信息 */
    private String sourceInfo;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }

    public String getIoType() { return ioType; }
    public void setIoType(String ioType) { this.ioType = ioType; }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }

    public String getLongname() { return longname; }
    public void setLongname(String longname) { this.longname = longname; }

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }

    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }

    public Boolean getMulti() { return multi; }
    public void setMulti(Boolean multi) { this.multi = multi; }

    public Boolean getArrayFlag() { return arrayFlag; }
    public void setArrayFlag(Boolean arrayFlag) { this.arrayFlag = arrayFlag; }

    public String getSourceInfo() { return sourceInfo; }
    public void setSourceInfo(String sourceInfo) { this.sourceInfo = sourceInfo; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    @Override
    public String toString() {
        return "FlowFieldDetail{" +
                "id=" + id +
                ", flowId='" + flowId + '\'' +
                ", ioType='" + ioType + '\'' +
                ", fieldId='" + fieldId + '\'' +
                ", fieldType='" + fieldType + '\'' +
                ", longname='" + longname + '\'' +
                ", ref='" + ref + '\'' +
                ", required=" + required +
                ", multi=" + multi +
                ", arrayFlag=" + arrayFlag +
                ", sourceInfo='" + sourceInfo + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}
