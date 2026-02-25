package com.sunline.dict.service;

import java.util.Map;

/**
 * POM依赖树服务接口
 */
public interface PomDependencyTreeService {
    
    /**
     * 构建POM依赖树
     * @param projectId GitLab项目ID
     * @param branch 分支名称
     * @return POM依赖树JSON结构
     */
    Map<String, Object> buildPomDependencyTree(Integer projectId, String branch);
    
    /**
     * 保存POM依赖树到本地文件
     * @param projectName 工程名称
     * @param dependencyTree 依赖树数据
     * @return 文件保存路径
     */
    String saveDependencyTreeToFile(String projectName, Map<String, Object> dependencyTree);
}

