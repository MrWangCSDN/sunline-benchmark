package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分层调用规则项实体类
 */
@TableName("layer_call_rule_item")
public class LayerCallRuleItem implements Serializable {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ruleId;
    private String callerLayer;
    private String calleeLayer;
    private String domainConstraint;
    private Integer itemOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    
    public String getCallerLayer() { return callerLayer; }
    public void setCallerLayer(String callerLayer) { this.callerLayer = callerLayer; }
    
    public String getCalleeLayer() { return calleeLayer; }
    public void setCalleeLayer(String calleeLayer) { this.calleeLayer = calleeLayer; }
    
    public String getDomainConstraint() { return domainConstraint; }
    public void setDomainConstraint(String domainConstraint) { this.domainConstraint = domainConstraint; }
    
    public Integer getItemOrder() { return itemOrder; }
    public void setItemOrder(Integer itemOrder) { this.itemOrder = itemOrder; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}

