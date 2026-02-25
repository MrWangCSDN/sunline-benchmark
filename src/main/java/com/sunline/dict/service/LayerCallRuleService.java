package com.sunline.dict.service;

import com.sunline.dict.entity.LayerCallRule;

import java.util.List;
import java.util.Map;

/**
 * 分层调用规则服务接口
 */
public interface LayerCallRuleService {
    
    /**
     * 获取所有规则列表（包含规则项）
     */
    List<LayerCallRule> getAllRules();
    
    /**
     * 创建规则
     */
    LayerCallRule createRule(Map<String, Object> request);
    
    /**
     * 更新规则
     */
    LayerCallRule updateRule(Long ruleId, Map<String, Object> request);
    
    /**
     * 删除规则
     */
    void deleteRule(Long ruleId);
}

