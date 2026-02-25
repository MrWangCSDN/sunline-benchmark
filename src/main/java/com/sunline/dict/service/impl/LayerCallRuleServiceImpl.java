package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sunline.dict.entity.LayerCallRule;
import com.sunline.dict.entity.LayerCallRuleItem;
import com.sunline.dict.mapper.LayerCallRuleItemMapper;
import com.sunline.dict.mapper.LayerCallRuleMapper;
import com.sunline.dict.service.LayerCallRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class LayerCallRuleServiceImpl implements LayerCallRuleService {
    
    private static final Logger log = LoggerFactory.getLogger(LayerCallRuleServiceImpl.class);
    
    @Autowired
    private LayerCallRuleMapper ruleMapper;
    
    @Autowired
    private LayerCallRuleItemMapper ruleItemMapper;
    
    @Override
    public List<LayerCallRule> getAllRules() {
        List<LayerCallRule> rules = ruleMapper.selectList(null);
        for (LayerCallRule rule : rules) {
            List<LayerCallRuleItem> items = ruleItemMapper.selectByRuleId(rule.getId());
            rule.setItems(items);
        }
        return rules;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LayerCallRule createRule(Map<String, Object> request) {
        String ruleName = (String) request.get("ruleName");
        String ruleDescription = (String) request.get("ruleDescription");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> constraints = (List<Map<String, String>>) request.get("constraints");
        
        LayerCallRule rule = new LayerCallRule();
        rule.setRuleName(ruleName);
        rule.setRuleDescription(ruleDescription);
        rule.setStatus(1);
        
        ruleMapper.insert(rule);
        
        for (int i = 0; i < constraints.size(); i++) {
            Map<String, String> c = constraints.get(i);
            LayerCallRuleItem item = new LayerCallRuleItem();
            item.setRuleId(rule.getId());
            item.setCallerLayer(c.get("callerLayer"));
            item.setCalleeLayer(c.get("calleeLayer"));
            item.setDomainConstraint(c.get("domainConstraint"));
            item.setItemOrder(i);
            ruleItemMapper.insert(item);
        }
        
        return rule;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LayerCallRule updateRule(Long ruleId, Map<String, Object> request) {
        LayerCallRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            throw new RuntimeException("规则不存在");
        }
        
        String ruleName = (String) request.get("ruleName");
        String ruleDescription = (String) request.get("ruleDescription");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> constraints = (List<Map<String, String>>) request.get("constraints");
        
        rule.setRuleName(ruleName);
        rule.setRuleDescription(ruleDescription);
        ruleMapper.updateById(rule);
        
        ruleItemMapper.deleteByRuleId(ruleId);
        
        for (int i = 0; i < constraints.size(); i++) {
            Map<String, String> c = constraints.get(i);
            LayerCallRuleItem item = new LayerCallRuleItem();
            item.setRuleId(ruleId);
            item.setCallerLayer(c.get("callerLayer"));
            item.setCalleeLayer(c.get("calleeLayer"));
            item.setDomainConstraint(c.get("domainConstraint"));
            item.setItemOrder(i);
            ruleItemMapper.insert(item);
        }
        
        return rule;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long ruleId) {
        ruleItemMapper.deleteByRuleId(ruleId);
        ruleMapper.deleteById(ruleId);
    }
}
