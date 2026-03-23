package com.sunline.dict.service;

import java.util.Map;

/**
 * 代码同步服务接口
 * 功能：根据 GitLab Webhook Push 事件，将 master 分支最新代码同步到本地服务器
 */
public interface CodeSyncService {

    /**
     * 处理 GitLab Push 事件，同步 master 分支代码到本地
     *
     * @param payload GitLab Webhook Push 事件 payload
     * @return 同步结果
     */
    Map<String, Object> syncCode(Map<String, Object> payload) throws Exception;
}
