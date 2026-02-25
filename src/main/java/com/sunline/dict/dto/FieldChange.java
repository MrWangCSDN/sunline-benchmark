package com.sunline.dict.dto;

import java.io.Serializable;

/**
 * 字段变更记录
 */
public class FieldChange implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String dataItemCode;  // 数据项编号
    private String fieldName;     // 字段名称
    private String oldValue;      // 旧值
    private String newValue;      // 新值
    
    public FieldChange() {
    }
    
    public FieldChange(String dataItemCode, String fieldName, String oldValue, String newValue) {
        this.dataItemCode = dataItemCode;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    
    // Getters and Setters
    
    public String getDataItemCode() {
        return dataItemCode;
    }
    
    public void setDataItemCode(String dataItemCode) {
        this.dataItemCode = dataItemCode;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public String getOldValue() {
        return oldValue;
    }
    
    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }
    
    public String getNewValue() {
        return newValue;
    }
    
    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }
}

