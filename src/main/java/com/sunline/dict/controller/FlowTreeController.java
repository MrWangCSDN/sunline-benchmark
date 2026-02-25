package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.FlowTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易树Controller
 */
@RestController
@RequestMapping("/api/flow-tree")
@CrossOrigin
public class FlowTreeController {
    
    private static final Logger log = LoggerFactory.getLogger(FlowTreeController.class);
    
    @Autowired
    private FlowTreeService flowTreeService;
    
    /**
     * 根据flow_id生成交易JSON树
     */
    @GetMapping("/{flowId}")
    public Result<Map<String, Object>> getFlowTree(@PathVariable String flowId) {
        try {
            Map<String, Object> tree = flowTreeService.buildFlowTree(flowId);
            if (tree == null) {
                return Result.error("未找到交易：" + flowId);
            }
            return Result.success(tree);
        } catch (Exception e) {
            log.error("构建交易树失败：flowId={}", flowId, e);
            return Result.error("构建交易树失败：" + e.getMessage());
        }
    }
    
    /**
     * 生成所有交易的JSON树列表
     */
    @GetMapping("/all")
    public Result<List<Map<String, Object>>> getAllFlowTrees() {
        try {
            List<Map<String, Object>> trees = flowTreeService.buildAllFlowTrees();
            return Result.success(trees);
        } catch (Exception e) {
            log.error("构建所有交易树失败", e);
            return Result.error("构建所有交易树失败：" + e.getMessage());
        }
    }
    
    /**
     * 根据领域查询交易列表
     */
    @GetMapping("/flows/{domain}")
    public Result<List<Map<String, Object>>> getFlowListByDomain(@PathVariable String domain) {
        try {
            List<Map<String, Object>> flows = flowTreeService.getFlowListByDomain(domain);
            return Result.success(flows);
        } catch (Exception e) {
            log.error("查询交易列表失败：domain={}", domain, e);
            return Result.error("查询交易列表失败：" + e.getMessage());
        }
    }
    
    /**
     * 保存交易树JSON到本地文件
     */
    @PostMapping("/save")
    @SuppressWarnings("unchecked")
    public Result<Map<String, String>> saveFlowTree(@RequestBody Map<String, Object> request) {
        try {
            String domain = (String) request.get("domain");
            String flowId = (String) request.get("flowId");
            Map<String, Object> flowTree = (Map<String, Object>) request.get("flowTree");
            
            if (domain == null || flowId == null || flowTree == null) {
                return Result.error("参数不完整：domain、flowId、flowTree 均为必填");
            }
            
            String filePath = flowTreeService.saveFlowTreeToFile(domain, flowId, flowTree);
            
            Map<String, String> result = new HashMap<>();
            result.put("filePath", filePath);
            result.put("message", "交易树已保存到文件");
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("保存交易树失败", e);
            return Result.error("保存交易树失败：" + e.getMessage());
        }
    }
    
    /**
     * 检查交易树JSON文件是否存在
     */
    @GetMapping("/check/{flowId}")
    public Result<Map<String, Object>> checkFileExists(@PathVariable String flowId) {
        try {
            boolean exists = flowTreeService.checkFileExists(flowId);
            Map<String, Object> result = new HashMap<>();
            result.put("exists", exists);
            return Result.success(result);
        } catch (Exception e) {
            log.error("检查文件是否存在失败：flowId={}", flowId, e);
            return Result.error("检查失败：" + e.getMessage());
        }
    }
    
    /**
     * 加载已存在的交易树JSON文件
     */
    @GetMapping("/load/{flowId}")
    public Result<Map<String, Object>> loadFlowTree(@PathVariable String flowId) {
        try {
            Map<String, Object> tree = flowTreeService.loadFlowTreeFromFile(flowId);
            if (tree == null) {
                return Result.error("未找到交易树文件：" + flowId);
            }
            return Result.success(tree);
        } catch (Exception e) {
            log.error("加载交易树文件失败：flowId={}", flowId, e);
            return Result.error("加载失败：" + e.getMessage());
        }
    }
    
    /**
     * 全量生成所有交易的JSON文件（包含规则检查）
     */
    @PostMapping("/generate-all")
    public Result<Map<String, Object>> generateAllTrees() {
        try {
            log.info("开始全量生成所有交易树JSON文件（含规则检查）");
            Map<String, Object> result = flowTreeService.generateAllWithRuleCheck();
            
            int treeCount = (Integer) result.get("treeCount");
            int totalChecked = (Integer) result.get("totalChecked");
            int violationCount = (Integer) result.get("violationCount");
            
            result.put("totalCount", treeCount);
            result.put("message", String.format(
                "全量生成成功！生成 %d 个交易树文件，检查 %d 条调用关系，发现 %d 处违规", 
                treeCount, totalChecked, violationCount
            ));
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("全量生成交易树失败", e);
            return Result.error("全量生成失败：" + e.getMessage());
        }
    }
}

