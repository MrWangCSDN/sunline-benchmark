package com.sunline.dict.service;

/**
 * 分层调用规则检查服务接口
 */
public interface LayerCallRuleCheckService {
    
    /**
     * 检查所有flow_step的调用关系是否符合规则
     * 将违规信息记录到flow_step表的incorrect_calls字段
     * @return 检查结果统计
     */
    java.util.Map<String, Object> checkAllFlowSteps();
}

