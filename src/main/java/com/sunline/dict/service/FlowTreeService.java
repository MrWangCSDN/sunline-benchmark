package com.sunline.dict.service;

import java.util.List;
import java.util.Map;

/**
 * 交易树服务接口
 */
public interface FlowTreeService {
    
    /**
     * 根据flow_id生成交易JSON树
     * @param flowId 交易ID
     * @return 交易JSON树结构
     */
    Map<String, Object> buildFlowTree(String flowId);
    
    /**
     * 生成所有交易的JSON树列表
     * @return 所有交易的JSON树列表
     */
    List<Map<String, Object>> buildAllFlowTrees();
    
    /**
     * 根据领域查询交易列表
     * @param domain 领域（comm、dept、sett、loan）
     * @return 交易列表，格式：[{id: "xxx", longname: "xxx", fromJar: "xxx"}]
     */
    List<Map<String, Object>> getFlowListByDomain(String domain);
    
    /**
     * 保存交易树JSON到本地文件
     * @param domain 领域
     * @param flowId 交易ID
     * @param flowTree 交易树JSON数据
     * @return 保存的文件路径
     */
    String saveFlowTreeToFile(String domain, String flowId, Map<String, Object> flowTree);
    
    /**
     * 检查交易树JSON文件是否存在
     * @param flowId 交易ID
     * @return 文件是否存在
     */
    boolean checkFileExists(String flowId);
    
    /**
     * 从本地文件加载交易树JSON
     * @param flowId 交易ID
     * @return 交易树JSON数据
     */
    Map<String, Object> loadFlowTreeFromFile(String flowId);
    
    /**
     * 全量生成所有交易树并保存到文件
     * @return 生成的文件数量
     */
    int generateAllTreesToFile();
    
    /**
     * 全量生成并执行规则检查
     * @return 包含生成统计和检查结果的Map
     */
    Map<String, Object> generateAllWithRuleCheck();
}

