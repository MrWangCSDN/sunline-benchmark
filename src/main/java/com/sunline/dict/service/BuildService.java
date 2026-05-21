package com.sunline.dict.service;

import java.util.List;
import java.util.Map;

/**
 * 工程编译服务接口
 *
 * <p>职责说明：
 * <ul>
 *   <li>Webhook 不自动触发编译</li>
 *   <li>编译由本接口的方法显式触发，支持全量和单批次两种粒度</li>
 * </ul>
 */
public interface BuildService {

    /**
     * 查询 application.yml 中配置的所有批次信息（不执行编译）
     *
     * @return 批次列表，每项包含 batchIndex / name / parallel / projects / projectCount
     */
    List<Map<String, Object>> listBatches();

    /**
     * 全量编译（同步，按批次串行执行，等待全部完成后返回）
     *
     * @return 每个批次的编译结果
     */
    List<Map<String, Object>> buildAll();

    /**
     * 全量编译（异步，立即返回，进度通过 SSE/status 接口查询）
     */
    String buildAllAsync();

    /**
     * 编译指定批次（同步，等待该批次完成后返回）
     *
     * @param batchName 批次名称，与 application.yml build.batches[].name 一致
     * @return 该批次内每个工程的编译结果
     * @throws IllegalArgumentException 批次名称不存在时抛出
     */
    Map<String, Object> buildBatch(String batchName);

    /**
     * 编译指定批次（异步，立即返回，进度通过 SSE/status 接口查询）
     *
     * @param batchName 批次名称
     */
    void buildBatchAsync(String batchName);

    /**
     * 编译单个工程（同步）
     *
     * @param projectName 工程目录名
     * @return 编译结果
     */
    Map<String, Object> buildOne(String projectName);
}
