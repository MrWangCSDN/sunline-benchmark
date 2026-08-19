package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

@TableName("flow_field_change_detail")
public class FlowFieldChangeDetail implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long logId;
    private String ioType;
    private String fieldPath;
    private String fieldId;
    private String changeType;
    private String oldSnapshot;
    private String newSnapshot;
    private String changedAttributes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public String getIoType() { return ioType; }
    public void setIoType(String ioType) { this.ioType = ioType; }
    public String getFieldPath() { return fieldPath; }
    public void setFieldPath(String fieldPath) { this.fieldPath = fieldPath; }
    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
    public String getOldSnapshot() { return oldSnapshot; }
    public void setOldSnapshot(String oldSnapshot) { this.oldSnapshot = oldSnapshot; }
    public String getNewSnapshot() { return newSnapshot; }
    public void setNewSnapshot(String newSnapshot) { this.newSnapshot = newSnapshot; }
    public String getChangedAttributes() { return changedAttributes; }
    public void setChangedAttributes(String changedAttributes) { this.changedAttributes = changedAttributes; }
}
