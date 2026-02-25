package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.entity.LayerCallRule;
import com.sunline.dict.service.LayerCallRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/layer-call-rule")
@CrossOrigin
public class LayerCallRuleController {
    
    private static final Logger log = LoggerFactory.getLogger(LayerCallRuleController.class);
    
    @Autowired
    private LayerCallRuleService layerCallRuleService;
    
    @GetMapping("/list")
    public Result<List<LayerCallRule>> listRules() {
        try {
            List<LayerCallRule> rules = layerCallRuleService.getAllRules();
            return Result.success(rules);
        } catch (Exception e) {
            log.error("查询规则列表失败", e);
            return Result.error("查询失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/create")
    public Result<LayerCallRule> createRule(@RequestBody Map<String, Object> request) {
        try {
            LayerCallRule rule = layerCallRuleService.createRule(request);
            return Result.success(rule);
        } catch (Exception e) {
            log.error("创建规则失败", e);
            return Result.error("创建失败：" + e.getMessage());
        }
    }
    
    @PostMapping("/update/{id}")
    public Result<LayerCallRule> updateRule(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            LayerCallRule rule = layerCallRuleService.updateRule(id, request);
            return Result.success(rule);
        } catch (Exception e) {
            log.error("更新规则失败", e);
            return Result.error("更新失败：" + e.getMessage());
        }
    }
    
    @DeleteMapping("/delete/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        try {
            layerCallRuleService.deleteRule(id);
            return Result.success(null);
        } catch (Exception e) {
            log.error("删除规则失败", e);
            return Result.error("删除失败：" + e.getMessage());
        }
    }
}
