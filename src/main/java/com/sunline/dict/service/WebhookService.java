package com.sunline.dict.service;

import java.util.Map;

/**
 * Webhook服务接口
 */
public interface WebhookService {
    
    /**
     * 处理Git Push事件
     * @param payload Webhook payload（JSON格式）
     * @return 处理结果
     */
    Map<String, Object> handlePushEvent(Map<String, Object> payload) throws Exception;
    
    /**
     * 处理GitLab Push事件
     * @param payload GitLab webhook payload
     * @return 处理结果
     */
    Map<String, Object> handleGitLabPushEvent(Map<String, Object> payload) throws Exception;
}
