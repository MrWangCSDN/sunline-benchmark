package com.sunline.dict.dto;

import com.sunline.dict.entity.CodeExtensionData;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.entity.DomainData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 变更报告
 */
public class ChangeReport implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private List<Object> newList = new ArrayList<>();      // 新增（支持DictData、DomainData、CodeExtensionData）
    private List<Object> updateList = new ArrayList<>();   // 修改
    private List<Object> deleteList = new ArrayList<>();   // 删除
    private List<Object> unchangedList = new ArrayList<>(); // 不变
    
    private List<FieldChange> fieldChanges = new ArrayList<>(); // 字段级变更
    
    private ChangeSummary summary; // 变更摘要
    
    public void addNew(Object data) {
        if (data instanceof DictData) {
            ((DictData) data).setChangeType("NEW");
        }
        newList.add(data);
    }
    
    public void addUpdate(Object newData, Object oldData) {
        if (newData instanceof DictData && oldData instanceof DictData) {
            ((DictData) newData).setChangeType("UPDATE");
            // 记录字段级变更
            compareDictFields((DictData) newData, (DictData) oldData);
        } else if (newData instanceof DomainData && oldData instanceof DomainData) {
            // 记录域清单字段级变更
            compareDomainFields((DomainData) newData, (DomainData) oldData);
        } else if (newData instanceof CodeExtensionData && oldData instanceof CodeExtensionData) {
            // 记录代码扩展清单字段级变更
            compareCodeExtensionFields((CodeExtensionData) newData, (CodeExtensionData) oldData);
        }
        updateList.add(newData);
    }
    
    public void addDelete(Object data) {
        if (data instanceof DictData) {
            ((DictData) data).setChangeType("DELETE");
        }
        deleteList.add(data);
    }
    
    public void addUnchanged(Object data) {
        if (data instanceof DictData) {
            ((DictData) data).setChangeType("UNCHANGED");
        }
        unchangedList.add(data);
    }
    
    /**
     * 对比字典字段变化
     */
    private void compareDictFields(DictData newData, DictData oldData) {
        String code = newData.getDataItemCode();
        
        compareField(code, "英文简称", oldData.getEnglishAbbr(), newData.getEnglishAbbr());
        compareField(code, "中文名称", oldData.getChineseName(), newData.getChineseName());
        compareField(code, "字典属性", oldData.getDictAttr(), newData.getDictAttr());
        compareField(code, "领域中文名称", oldData.getDomainChineseName(), newData.getDomainChineseName());
        compareField(code, "数据类型", oldData.getDataType(), newData.getDataType());
        compareField(code, "数据格式", oldData.getDataFormat(), newData.getDataFormat());
        compareField(code, "Java-ESF名称", oldData.getJavaEsfName(), newData.getJavaEsfName());
        compareField(code, "ESF数据格式", oldData.getEsfDataFormat(), newData.getEsfDataFormat());
        compareField(code, "GaussDB数据格式", oldData.getGaussdbDataFormat(), newData.getGaussdbDataFormat());
        compareField(code, "GoldenDB数据格式", oldData.getGoldendbDataFormat(), newData.getGoldendbDataFormat());
    }
    
    /**
     * 对比域清单字段变化
     * 只对比数据格式（H列），因为域中文名称作为唯一键不会变化
     */
    private void compareDomainFields(DomainData newData, DomainData oldData) {
        String chineseName = newData.getChineseName();
        
        // 只对比数据格式（H列）
        compareField(chineseName, "数据格式", oldData.getDataFormat(), newData.getDataFormat());
    }
    
    /**
     * 对比代码扩展清单字段变化
     * 只对比以下字段：
     * - B列-代码域中文名称
     * - C列-代码取值
     * - D列-代码含义中文名称
     * - G列-代码描述
     */
    private void compareCodeExtensionFields(CodeExtensionData newData, CodeExtensionData oldData) {
        // 使用"代码域中文名称|代码取值"作为唯一标识
        String key = newData.getCodeDomainChineseName() + "|" + newData.getCodeValue();
        
        // 只对比需要校验的字段
        compareField(key, "代码域中文名称", oldData.getCodeDomainChineseName(), newData.getCodeDomainChineseName());
        compareField(key, "代码取值", oldData.getCodeValue(), newData.getCodeValue());
        compareField(key, "代码含义中文名称", oldData.getValueChineseName(), newData.getValueChineseName());
        compareField(key, "代码描述", oldData.getCodeDescription(), newData.getCodeDescription());
    }
    
    private void compareField(String code, String fieldName, String oldValue, String newValue) {
        // 空值处理
        oldValue = oldValue == null ? "" : oldValue.trim();
        newValue = newValue == null ? "" : newValue.trim();
        
        if (!oldValue.equals(newValue)) {
            fieldChanges.add(new FieldChange(code, fieldName, oldValue, newValue));
        }
    }
    
    /**
     * 生成变更摘要
     */
    public ChangeSummary generateSummary() {
        summary = new ChangeSummary();
        summary.setNewCount(newList.size());
        summary.setUpdateCount(updateList.size());
        summary.setDeleteCount(deleteList.size());
        summary.setUnchangedCount(unchangedList.size());
        summary.setTotalCount(newList.size() + updateList.size() + unchangedList.size());
        return summary;
    }
    
    // Getters and Setters
    
    public List<Object> getNewList() {
        return newList;
    }
    
    public void setNewList(List<Object> newList) {
        this.newList = newList;
    }
    
    public List<Object> getUpdateList() {
        return updateList;
    }
    
    public void setUpdateList(List<Object> updateList) {
        this.updateList = updateList;
    }
    
    public List<Object> getDeleteList() {
        return deleteList;
    }
    
    public void setDeleteList(List<Object> deleteList) {
        this.deleteList = deleteList;
    }
    
    public List<Object> getUnchangedList() {
        return unchangedList;
    }
    
    public void setUnchangedList(List<Object> unchangedList) {
        this.unchangedList = unchangedList;
    }
    
    public List<FieldChange> getFieldChanges() {
        return fieldChanges;
    }
    
    public void setFieldChanges(List<FieldChange> fieldChanges) {
        this.fieldChanges = fieldChanges;
    }
    
    public ChangeSummary getSummary() {
        return summary;
    }
    
    public void setSummary(ChangeSummary summary) {
        this.summary = summary;
    }
}

