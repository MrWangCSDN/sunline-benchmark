package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 导入历史实体类
 */
@TableName("import_history")
public class ImportHistory implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private LocalDateTime importTime;
    private String fileName;
    private String operator;
    
    // 字典统计
    private Integer dictNewCount;
    private Integer dictUpdateCount;
    private Integer dictDeleteCount;
    
    // 域清单统计
    private Integer domainNewCount;
    private Integer domainUpdateCount;
    private Integer domainDeleteCount;
    
    // 代码扩展清单统计
    private Integer codeNewCount;
    private Integer codeUpdateCount;
    private Integer codeDeleteCount;
    
    private Integer totalCount;
    private String status;
    private String changeDescription;
    private String remark;
    private LocalDateTime createTime;
    
    // Getter and Setter methods
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public LocalDateTime getImportTime() {
        return importTime;
    }
    
    public void setImportTime(LocalDateTime importTime) {
        this.importTime = importTime;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getOperator() {
        return operator;
    }
    
    public void setOperator(String operator) {
        this.operator = operator;
    }
    
    public Integer getDictNewCount() {
        return dictNewCount;
    }
    
    public void setDictNewCount(Integer dictNewCount) {
        this.dictNewCount = dictNewCount;
    }
    
    public Integer getDictUpdateCount() {
        return dictUpdateCount;
    }
    
    public void setDictUpdateCount(Integer dictUpdateCount) {
        this.dictUpdateCount = dictUpdateCount;
    }
    
    public Integer getDictDeleteCount() {
        return dictDeleteCount;
    }
    
    public void setDictDeleteCount(Integer dictDeleteCount) {
        this.dictDeleteCount = dictDeleteCount;
    }
    
    public Integer getDomainNewCount() {
        return domainNewCount;
    }
    
    public void setDomainNewCount(Integer domainNewCount) {
        this.domainNewCount = domainNewCount;
    }
    
    public Integer getDomainUpdateCount() {
        return domainUpdateCount;
    }
    
    public void setDomainUpdateCount(Integer domainUpdateCount) {
        this.domainUpdateCount = domainUpdateCount;
    }
    
    public Integer getDomainDeleteCount() {
        return domainDeleteCount;
    }
    
    public void setDomainDeleteCount(Integer domainDeleteCount) {
        this.domainDeleteCount = domainDeleteCount;
    }
    
    public Integer getCodeNewCount() {
        return codeNewCount;
    }
    
    public void setCodeNewCount(Integer codeNewCount) {
        this.codeNewCount = codeNewCount;
    }
    
    public Integer getCodeUpdateCount() {
        return codeUpdateCount;
    }
    
    public void setCodeUpdateCount(Integer codeUpdateCount) {
        this.codeUpdateCount = codeUpdateCount;
    }
    
    public Integer getCodeDeleteCount() {
        return codeDeleteCount;
    }
    
    public void setCodeDeleteCount(Integer codeDeleteCount) {
        this.codeDeleteCount = codeDeleteCount;
    }
    
    public Integer getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getChangeDescription() {
        return changeDescription;
    }
    
    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}

