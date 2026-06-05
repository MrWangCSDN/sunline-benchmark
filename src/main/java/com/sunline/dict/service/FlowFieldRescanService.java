package com.sunline.dict.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 字段明细全量重扫服务（异步 + 进度）
 */
public interface FlowFieldRescanService {

    /**
     * 启动一次异步全量重扫。同一时间只允许一个 RUNNING。
     *
     * @param sourcePath 本地源码目录绝对路径
     * @return { "operationId": uuid, "totalFiles": N, "status": "RUNNING" }
     */
    Map<String, Object> startRescan(String sourcePath) throws Exception;

    /**
     * 实际异步执行重扫（由 startRescan 自注入代理调用以触发 @Async）。
     * 不应被 Controller 直接调用。
     */
    void runAsync(String operationId, List<Path> files);

    /**
     * 重扫单个 flow（同步）。
     * 实际从 sourcePath 下找名为 {flowId}.flowtrans.xml 的文件。
     *
     * @return { "inputCount": N, "outputCount": M }
     */
    Map<String, Integer> rescanOne(String flowId, String sourcePath) throws Exception;

    /**
     * 查询进度。operationId 不存在 → status="UNKNOWN"
     */
    Map<String, Object> getProgress(String operationId);
}
