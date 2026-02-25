package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分层调用规则实体类
 */
@TableName("layer_call_rule")
public class LayerCallRule implements Serializable {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleName;
    private String ruleDescription;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    // 规则项列表（不映射到数据库）
    private transient List<LayerCallRuleItem> items;
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    
    public String getRuleDescription() { return ruleDescription; }
    public void setRuleDescription(String ruleDescription) { this.ruleDescription = ruleDescription; }
    
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    
    public List<LayerCallRuleItem> getItems() { return items; }
    public void setItems(List<LayerCallRuleItem> items) { this.items = items; }
}

