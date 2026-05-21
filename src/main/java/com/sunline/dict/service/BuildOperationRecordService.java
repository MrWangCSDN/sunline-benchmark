package com.sunline.dict.service;

import com.sunline.dict.entity.BuildOperationRecord;

import java.util.List;
import java.util.Map;

/**
 * 全量拉取+编译操作记录服务
 */
public interface BuildOperationRecordService {

    /**
     * 生成一次任务的唯一编号
     */
    String nextOperationId();

    /**
     * 生成一次任务的版本号，如 V-2026.0331.12:26:07
     */
    String nextVersionNo();

    /**
     * 创建任务总记录
     */
    Long startTask(String operationId, String versionNo, String triggerApi, String triggerMode, String operator,
                   int totalBatches, int totalPullProjects);

    /**
     * 更新任务总记录
     */
    void finishTask(Long recordId, String status, String resultMessage,
                    String errorType, String errorMessage, String errorStack,
                    Map<String, Object> extraData);

    /**
     * 创建拉取工程明细
     */
    Long startPullProject(String operationId, String versionNo, String projectName, int sortNo);

    /**
     * 更新拉取工程明细
     */
    void finishPullProject(Long recordId, String status, String resultMessage,
                           String errorType, String errorMessage, String errorStack,
                           Map<String, Object> extraData);

    /**
     * 创建编译批次明细
     */
    Long startBuildBatch(String operationId, String versionNo, int batchIndex, String batchName, int sortNo);

    /**
     * 更新编译批次明细
     */
    void finishBuildBatch(Long recordId, String status, String resultMessage,
                          Map<String, Object> extraData);

    /**
     * 创建编译工程明细
     */
    Long startBuildProject(String operationId, String versionNo, int batchIndex, String batchName, String projectName, int sortNo);

    /**
     * 更新编译工程明细
     */
    void finishBuildProject(Long recordId, String status, String resultMessage,
                            String errorType, String errorMessage, String errorStack,
                            String logFile, Map<String, Object> extraData);

    /**
     * 直接写入一条已结束的编译批次明细
     */
    Long saveBuildBatchRecord(String operationId, String versionNo, int batchIndex, String batchName, int sortNo,
                              String status, String resultMessage, Map<String, Object> extraData);

    /**
     * 直接写入一条已结束的编译工程明细
     */
    Long saveBuildProjectRecord(String operationId, String versionNo, int batchIndex, String batchName, String projectName, int sortNo,
                                String status, String resultMessage, String errorType, String errorMessage,
                                String errorStack, String logFile, Map<String, Object> extraData);

    /**
     * 查询一次任务的完整明细
     */
    Map<String, Object> queryOperationDetail(String operationId);

    /**
     * 查询当前保留的最近一次任务
     */
    List<BuildOperationRecord> queryRecentTasks(int limit);

    /**
     * 更新当前任务的异步构建状态
     */
    void updateAsyncBuildStatus(String operationId, String asyncBuildStatus);
}
