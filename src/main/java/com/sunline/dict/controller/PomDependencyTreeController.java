package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.PomDependencyTreeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * POM依赖树检查控制器
 */
@RestController
@RequestMapping("/api/pom-dependency-tree")
public class PomDependencyTreeController {
    
    private static final Logger log = LoggerFactory.getLogger(PomDependencyTreeController.class);
    
    @Autowired
    private PomDependencyTreeService pomDependencyTreeService;
    
    /**
     * 构建POM依赖树
     */
    @PostMapping("/build")
    public Result<Map<String, Object>> buildDependencyTree(@RequestBody Map<String, Object> request) {
        try {
            Integer projectId = (Integer) request.get("projectId");
            String branch = (String) request.get("branch");
            
            if (projectId == null || branch == null || branch.trim().isEmpty()) {
                return Result.error("项目ID和分支名称不能为空");
            }
            
            log.info("开始构建POM依赖树: projectId={}, branch={}", projectId, branch);
            
            Map<String, Object> dependencyTree = pomDependencyTreeService.buildPomDependencyTree(projectId, branch);
            
            // 获取项目名称
            String projectName = (String) request.get("projectName");
            if (projectName != null && !projectName.trim().isEmpty()) {
                // 保存到文件
                String filePath = pomDependencyTreeService.saveDependencyTreeToFile(projectName, dependencyTree);
                Map<String, Object> result = new HashMap<>();
                result.put("dependencyTree", dependencyTree);
                result.put("filePath", filePath);
                return Result.success(result);
            }
            
            return Result.success(Map.of("dependencyTree", dependencyTree));
            
        } catch (Exception e) {
            log.error("构建POM依赖树失败", e);
            return Result.error("构建失败: " + e.getMessage());
        }
    }
}

