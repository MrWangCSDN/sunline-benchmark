package com.sunline.dict.service;

import java.util.List;
import java.util.Map;

/**
 * 工程编译服务接口
 */
public interface BuildService {

    /**
     * 串行编译所有配置的工程（同步，等待全部完成后返回）
     *
     * @return 每个工程的编译结果列表
     */
    List<Map<String, Object>> buildAll();

    /**
     * 编译单个工程（同步）
     *
     * @param projectName 工程目录名
     * @return 编译结果
     */
    Map<String, Object> buildOne(String projectName);

    /**
     * 异步后台串行编译所有工程，立即返回，结果写入日志
     */
    void buildAllAsync();
}
