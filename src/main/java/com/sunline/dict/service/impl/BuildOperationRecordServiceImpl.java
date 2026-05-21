package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.entity.BuildOperationRecord;
import com.sunline.dict.mapper.BuildOperationRecordMapper;
import com.sunline.dict.service.BuildOperationRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 全量拉取+编译操作记录服务实现
 */
@Service
public class BuildOperationRecordServiceImpl implements BuildOperationRecordService {

    private static final String TYPE_TASK = "TASK";
    private static final String TYPE_PULL_PROJECT = "PULL_PROJECT";
    private static final String TYPE_BUILD_BATCH = "BUILD_BATCH";
    private static final String TYPE_BUILD_PROJECT = "BUILD_PROJECT";

    private static final String STAGE_ALL = "ALL";
    private static final String STAGE_PULL = "PULL";
    private static final String STAGE_BUILD = "BUILD";

    private static final DateTimeFormatter OPERATION_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final DateTimeFormatter VERSION_FMT = DateTimeFormatter.ofPattern("'V-'yyyy.MMdd.HH:mm:ss");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private BuildOperationRecordMapper buildOperationRecordMapper;

    @Override
    public String nextOperationId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "op_" + LocalDateTime.now().format(OPERATION_FMT) + "_" + suffix;
    }

    @Override
    public String nextVersionNo() {
        return LocalDateTime.now().format(VERSION_FMT);
    }

    @Override
    public Long startTask(String operationId, String versionNo, String triggerApi, String triggerMode, String operator,
                          int totalBatches, int totalPullProjects) {
        // 只保留最近一次全量执行记录，新任务开始前先清空旧数据
        buildOperationRecordMapper.delete(null);

        Map<String, Object> extraData = new LinkedHashMap<>();
        extraData.put("totalBatches", totalBatches);
        extraData.put("totalPullProjects", totalPullProjects);

        BuildOperationRecord record = baseRecord(operationId, versionNo, TYPE_TASK, STAGE_ALL, 0);
        record.setTriggerApi(triggerApi);
        record.setTriggerMode(triggerMode);
        record.setOperator(operator);
        record.setStatus("RUNNING");
        record.setAsyncBuildStatus("未触发");
        record.setResultMessage("任务已创建");
        record.setExtraJson(toJson(extraData));
        buildOperationRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public void finishTask(Long recordId, String status, String resultMessage,
                           String errorType, String errorMessage, String errorStack,
                           Map<String, Object> extraData) {
        finishRecord(recordId, status, resultMessage, errorType, errorMessage, errorStack, null, extraData);
    }

    @Override
    public Long startPullProject(String operationId, String versionNo, String projectName, int sortNo) {
        BuildOperationRecord record = baseRecord(operationId, versionNo, TYPE_PULL_PROJECT, STAGE_PULL, sortNo);
        record.setProjectName(projectName);
        record.setStatus("RUNNING");
        record.setResultMessage("等待拉取");
        buildOperationRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public void finishPullProject(Long recordId, String status, String resultMessage,
                                  String errorType, String errorMessage, String errorStack,
                                  Map<String, Object> extraData) {
        finishRecord(recordId, status, resultMessage, errorType, errorMessage, errorStack, null, extraData);
    }

    @Override
    public Long startBuildBatch(String operationId, String versionNo, int batchIndex, String batchName, int sortNo) {
        BuildOperationRecord record = baseRecord(operationId, versionNo, TYPE_BUILD_BATCH, STAGE_BUILD, sortNo);
        record.setBatchIndex(batchIndex);
        record.setBatchName(batchName);
        record.setStatus("RUNNING");
        record.setResultMessage("等待批次编译");
        buildOperationRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public void finishBuildBatch(Long recordId, String status, String resultMessage,
                                 Map<String, Object> extraData) {
        finishRecord(recordId, status, resultMessage, null, null, null, null, extraData);
    }

    @Override
    public Long startBuildProject(String operationId, String versionNo, int batchIndex, String batchName, String projectName, int sortNo) {
        BuildOperationRecord record = baseRecord(operationId, versionNo, TYPE_BUILD_PROJECT, STAGE_BUILD, sortNo);
        record.setBatchIndex(batchIndex);
        record.setBatchName(batchName);
        record.setProjectName(projectName);
        record.setStatus("RUNNING");
        record.setResultMessage("等待工程编译");
        buildOperationRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public void finishBuildProject(Long recordId, String status, String resultMessage,
                                   String errorType, String errorMessage, String errorStack,
                                   String logFile, Map<String, Object> extraData) {
        finishRecord(recordId, status, resultMessage, errorType, errorMessage, errorStack, logFile, extraData);
    }

    @Override
    public Long saveBuildBatchRecord(String operationId, String versionNo, int batchIndex, String batchName, int sortNo,
                                     String status, String resultMessage, Map<String, Object> extraData) {
        LocalDateTime now = LocalDateTime.now();
        BuildOperationRecord record = baseRecord(operationId, versionNo, TYPE_BUILD_BATCH, STAGE_BUILD, sortNo);
        record.setBatchIndex(batchIndex);
        record.setBatchName(batchName);
        record.setStatus(status);
        record.setResultMessage(resultMessage);
        record.setStartTime(now);
        record.setEndTime(now);
        record.setCostMs(0L);
        record.setExtraJson(toJson(extraData));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        buildOperationRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public Long saveBuildProjectRecord(String operationId, String versionNo, int batchIndex, String batchName, String projectName, int sortNo,
                                       String status, String resultMessage, String errorType, String errorMessage,
                                       String errorStack, String logFile, Map<String, Object> extraData) {
        LocalDateTime now = LocalDateTime.now();
        BuildOperationRecord record = baseRecord(operationId, versionNo, TYPE_BUILD_PROJECT, STAGE_BUILD, sortNo);
        record.setBatchIndex(batchIndex);
        record.setBatchName(batchName);
        record.setProjectName(projectName);
        record.setStatus(status);
        record.setResultMessage(resultMessage);
        record.setErrorType(blankToNull(errorType));
        record.setErrorMessage(blankToNull(errorMessage));
        record.setErrorStack(blankToNull(errorStack));
        record.setLogFile(blankToNull(logFile));
        record.setStartTime(now);
        record.setEndTime(now);
        record.setCostMs(0L);
        record.setExtraJson(toJson(extraData));
        record.setCreateTime(now);
        record.setUpdateTime(now);
        buildOperationRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    public Map<String, Object> queryOperationDetail(String operationId) {
        QueryWrapper<BuildOperationRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("operation_id", operationId)
                .orderByAsc("sort_no")
                .orderByAsc("id");

        List<BuildOperationRecord> records = buildOperationRecordMapper.selectList(queryWrapper);
        if (records == null || records.isEmpty()) {
            return null;
        }

        BuildOperationRecord task = null;
        List<BuildOperationRecord> pullRecords = new ArrayList<>();
        List<BuildOperationRecord> buildBatchRecords = new ArrayList<>();
        List<BuildOperationRecord> buildProjectRecords = new ArrayList<>();
        List<BuildOperationRecord> failedRecords = new ArrayList<>();

        for (BuildOperationRecord record : records) {
            if (TYPE_TASK.equals(record.getRecordType())) {
                task = record;
            } else if (TYPE_PULL_PROJECT.equals(record.getRecordType())) {
                pullRecords.add(record);
            } else if (TYPE_BUILD_BATCH.equals(record.getRecordType())) {
                buildBatchRecords.add(record);
            } else if (TYPE_BUILD_PROJECT.equals(record.getRecordType())) {
                buildProjectRecords.add(record);
            }

            String status = record.getStatus();
            if ("FAILED".equals(status) || "ERROR".equals(status) || "TIMEOUT".equals(status)) {
                failedRecords.add(record);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operationId", operationId);
        data.put("task", task);
        data.put("pullRecords", pullRecords);
        data.put("buildBatchRecords", buildBatchRecords);
        data.put("buildProjectRecords", buildProjectRecords);
        data.put("failedRecords", failedRecords);
        data.put("records", records);
        return data;
    }

    @Override
    public List<BuildOperationRecord> queryRecentTasks(int limit) {
        int safeLimit = 1;
        QueryWrapper<BuildOperationRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("record_type", TYPE_TASK)
                .orderByDesc("id")
                .last("LIMIT " + safeLimit);
        return buildOperationRecordMapper.selectList(queryWrapper);
    }

    @Override
    public void updateAsyncBuildStatus(String operationId, String asyncBuildStatus) {
        BuildOperationRecord taskRecord = findTaskRecord(operationId);
        if (taskRecord == null) {
            return;
        }

        BuildOperationRecord update = new BuildOperationRecord();
        update.setId(taskRecord.getId());
        update.setAsyncBuildStatus(blankToNull(asyncBuildStatus));
        update.setUpdateTime(LocalDateTime.now());
        buildOperationRecordMapper.updateById(update);
    }

    private BuildOperationRecord baseRecord(String operationId, String versionNo, String recordType, String stage, int sortNo) {
        LocalDateTime now = LocalDateTime.now();
        BuildOperationRecord record = new BuildOperationRecord();
        record.setOperationId(operationId);
        record.setVersionNo(versionNo);
        record.setRecordType(recordType);
        record.setStage(stage);
        record.setSortNo(sortNo);
        record.setStartTime(now);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        return record;
    }

    private void finishRecord(Long recordId, String status, String resultMessage,
                              String errorType, String errorMessage, String errorStack,
                              String logFile, Map<String, Object> extraData) {
        BuildOperationRecord existed = buildOperationRecordMapper.selectById(recordId);
        if (existed == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        BuildOperationRecord record = new BuildOperationRecord();
        record.setId(recordId);
        record.setStatus(status);
        record.setResultMessage(resultMessage);
        record.setErrorType(blankToNull(errorType));
        record.setErrorMessage(blankToNull(errorMessage));
        record.setErrorStack(blankToNull(errorStack));
        record.setLogFile(blankToNull(logFile));
        record.setEndTime(now);
        record.setUpdateTime(now);
        record.setExtraJson(toJson(extraData));
        if (existed.getStartTime() != null) {
            record.setCostMs(Duration.between(existed.getStartTime(), now).toMillis());
        }
        buildOperationRecordMapper.updateById(record);
    }

    private BuildOperationRecord findTaskRecord(String operationId) {
        QueryWrapper<BuildOperationRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("record_type", TYPE_TASK)
                .orderByDesc("id")
                .last("LIMIT 1");
        if (operationId != null && !operationId.trim().isEmpty()) {
            queryWrapper.eq("operation_id", operationId.trim());
        }
        return buildOperationRecordMapper.selectOne(queryWrapper);
    }

    private String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }
}
