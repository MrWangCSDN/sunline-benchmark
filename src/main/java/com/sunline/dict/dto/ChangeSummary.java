package com.sunline.dict.dto;

import java.io.Serializable;

/**
 * 变更摘要
 */
public class ChangeSummary implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Integer totalCount;      // 总数
    private Integer newCount;        // 新增数量
    private Integer updateCount;     // 修改数量
    private Integer deleteCount;     // 删除数量
    private Integer unchangedCount;  // 不变数量
    
    // Getters and Setters
    
    public Integer getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
    
    public Integer getNewCount() {
        return newCount;
    }
    
    public void setNewCount(Integer newCount) {
        this.newCount = newCount;
    }
    
    public Integer getUpdateCount() {
        return updateCount;
    }
    
    public void setUpdateCount(Integer updateCount) {
        this.updateCount = updateCount;
    }
    
    public Integer getDeleteCount() {
        return deleteCount;
    }
    
    public void setDeleteCount(Integer deleteCount) {
        this.deleteCount = deleteCount;
    }
    
    public Integer getUnchangedCount() {
        return unchangedCount;
    }
    
    public void setUnchangedCount(Integer unchangedCount) {
        this.unchangedCount = unchangedCount;
    }
}

