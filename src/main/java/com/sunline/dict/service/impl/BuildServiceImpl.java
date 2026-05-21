package com.sunline.dict.service.impl;

import com.sunline.dict.config.BuildConfig;
import com.sunline.dict.service.BuildOperationRecordService;
import com.sunline.dict.service.BuildProgressTracker;
import com.sunline.dict.service.BuildService;
import com.sunline.dict.service.CodeSyncService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工程编译服务实现
 *
 * <p>执行逻辑：
 * <ol>
 *   <li>先并行拉取全部工程代码</li>
 *   <li>拉取任一工程失败/跳过时立即中断整次任务</li>
 *   <li>拉取成功后按批次顺序编译（第一批 -> 第二批 -> ...）</li>
 *   <li>每个批次内部再按配置决定串行或并行编译</li>
 * </ol>
 */
@Service
public class BuildServiceImpl implements BuildService {

    private static final Logger log = LoggerFactory.getLogger(BuildServiceImpl.class);
    private final HttpClient callbackHttpClient = HttpClient.newBuilder().build();
    private final ObjectMapper callbackObjectMapper = new ObjectMapper();

    @Autowired
    private BuildConfig buildConfig;

    @Autowired
    private BuildProgressTracker progressTracker;

    @Autowired
    private CodeSyncService codeSyncService;

    @Autowired
    private BuildOperationRecordService buildOperationRecordService;

    /** 防止并发重复触发 */
    private final AtomicBoolean building = new AtomicBoolean(false);

    @Override
    public List<Map<String, Object>> buildAll() {
        if (!building.compareAndSet(false, true)) {
            return busyBatchResults();
        }

        List<BuildConfig.BatchConfig> batches = buildConfig.getBatches();
        List<String> allProjects = extractAllProjects(batches);
        String operationId = buildOperationRecordService.nextOperationId();
        String versionNo = buildOperationRecordService.nextVersionNo();
        Long taskRecordId = buildOperationRecordService.startTask(
                operationId, versionNo, "/api/build/all", "API", null, batches.size(), allProjects.size());
        return buildAllInternal(operationId, versionNo, taskRecordId, batches, allProjects);
    }

    @Override
    public String buildAllAsync() {
        if (!building.compareAndSet(false, true)) {
            throw new IllegalStateException("当前有编译任务正在进行，请稍后再试");
        }

        List<BuildConfig.BatchConfig> batches = buildConfig.getBatches();
        List<String> allProjects = extractAllProjects(batches);
        String operationId = buildOperationRecordService.nextOperationId();
        String versionNo = buildOperationRecordService.nextVersionNo();
        Long taskRecordId = buildOperationRecordService.startTask(
                operationId, versionNo, "/api/build/all/async", "API", null, batches.size(), allProjects.size());

        CompletableFuture.runAsync(() -> {
            List<Map<String, Object>> results = buildAllInternal(operationId, versionNo, taskRecordId, batches, allProjects);
            log.info("全量编译异步完成，operationId={}, versionNo={}", operationId, versionNo);
            results.forEach(r -> log.info("  批次：{} - 状态：{}", r.get("batch"), r.get("status")));
        });
        return operationId;
    }

    private List<Map<String, Object>> buildAllInternal(String operationId, String versionNo, Long taskRecordId,
                                                       List<BuildConfig.BatchConfig> batches,
                                                       List<String> allProjects) {
        List<Map<String, Object>> allBatchResults = new ArrayList<>();
        long totalStart = System.currentTimeMillis();
        progressTracker.reset(operationId, batches.size());

        boolean overallSuccess = false;
        String taskMessage = "全量拉取+编译成功";
        String taskErrorType = null;
        String taskErrorMessage = null;
        String taskErrorStack = null;

        long pullSuccess = 0;
        long pullSkip = 0;
        long pullFailed = 0;
        long pullCancelled = 0;

        try {
            ensureLogDir();

            progressTracker.startPullPhase(allProjects.size());
            log.info("==================== 编译前代码拉取开始，共 {} 个工程（并行） ====================",
                    allProjects.size());

            List<Map<String, Object>> pullResults = pullProjectsParallel(operationId, versionNo, allProjects);
            pullSuccess = countByStatus(pullResults, "SUCCESS");
            pullSkip = countByStatus(pullResults, "SKIP");
            pullFailed = countByStatuses(pullResults, "FAILED", "ERROR", "TIMEOUT");
            pullCancelled = countByStatus(pullResults, "CANCELLED");

            for (Map<String, Object> pr : pullResults) {
                progressTracker.logPullResult(
                        safeString(pr.get("project")),
                        safeString(pr.get("status")),
                        safeString(pr.get("message")));
            }
            progressTracker.finishPullPhase(pullSuccess, pullSkip, pullFailed, pullCancelled);

            Map<String, Object> firstPullProblem = findFirstProblemResult(pullResults);
            if (firstPullProblem != null) {
                taskMessage = "拉取阶段失败：" + safeString(firstPullProblem.get("project"));
                taskErrorType = safeString(firstPullProblem.get("errorType"));
                taskErrorMessage = safeString(firstPullProblem.get("errorMessage"));
                taskErrorStack = safeString(firstPullProblem.get("errorStack"));
                appendSkippedBatches(operationId, versionNo, allBatchResults, batches, 0, "拉取阶段失败，编译未执行");
                log.error("拉取阶段失败，终止后续编译，operationId={}，project={}",
                        operationId, firstPullProblem.get("project"));
                return allBatchResults;
            }

            log.info("==================== 开始全量编译，共 {} 批（批次间串行） ====================",
                    batches.size());

            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                BuildConfig.BatchConfig batchCfg = batches.get(batchIndex);
                Map<String, Object> batchResult = runBatch(operationId, versionNo, batchCfg, batchIndex);
                allBatchResults.add(batchResult);

                if (!isBatchSuccess(batchResult)) {
                    Map<String, Object> failedProject = findFirstProblemResult(
                            castProjectResults(batchResult.get("projects")));
                    String batchName = safeString(batchResult.get("batch"));
                    taskMessage = failedProject == null
                            ? "编译失败：" + batchName
                            : "编译失败：" + batchName + " / " + safeString(failedProject.get("project"));
                    if (failedProject != null) {
                        taskErrorType = safeString(failedProject.get("errorType"));
                        taskErrorMessage = safeString(failedProject.get("errorMessage"));
                        taskErrorStack = safeString(failedProject.get("errorStack"));
                    }
                    log.error("批次 {} 编译失败，停止后续批次，operationId={}", batchName, operationId);
                    appendSkippedBatches(operationId, versionNo, allBatchResults, batches, batchIndex + 1,
                            "前置批次失败，当前批次未执行");
                    return allBatchResults;
                }
            }

            overallSuccess = true;
            taskMessage = "全量拉取+编译成功";
            return allBatchResults;

        } catch (Exception e) {
            taskMessage = "全量拉取+编译异常：" + e.getMessage();
            taskErrorType = e.getClass().getName();
            taskErrorMessage = e.getMessage();
            taskErrorStack = stackTrace(e);
            log.error("全量拉取+编译异常，operationId={}", operationId, e);
            return allBatchResults;
        } finally {
            long totalCost = (System.currentTimeMillis() - totalStart) / 1000;

            Map<String, Object> taskExtra = new LinkedHashMap<>();
            taskExtra.put("totalBatches", batches.size());
            taskExtra.put("totalPullProjects", allProjects.size());
            taskExtra.put("completedBatches", allBatchResults.size());
            taskExtra.put("pullSuccess", pullSuccess);
            taskExtra.put("pullSkip", pullSkip);
            taskExtra.put("pullFailed", pullFailed);
            taskExtra.put("pullCancelled", pullCancelled);
            taskExtra.put("costSeconds", totalCost);

            buildOperationRecordService.finishTask(taskRecordId,
                    overallSuccess ? "SUCCESS" : "FAILED",
                    taskMessage,
                    taskErrorType,
                    taskErrorMessage,
                    taskErrorStack,
                    taskExtra);

            if (overallSuccess) {
                triggerSuccessCallback(operationId, versionNo);
            }

            progressTracker.finish(overallSuccess);
            if (overallSuccess) {
                clearLogDir();
            }
            building.set(false);
            log.info("==================== 全量编译结束，operationId={}，总耗时 {}s ====================",
                    operationId, totalCost);
        }
    }

    private void triggerSuccessCallback(String operationId, String versionNo) {
        BuildConfig.SuccessCallbackConfig callbackConfig = buildConfig.getSuccessCallback();
        if (callbackConfig == null || !callbackConfig.isEnabled()) {
            log.info("全量编译成功回调已关闭，operationId={}", operationId);
            return;
        }

        String callbackUrl = callbackConfig.getUrl();
        if (callbackUrl == null || callbackUrl.trim().isEmpty()) {
            log.warn("全量编译成功回调地址为空，跳过触发，operationId={}", operationId);
            return;
        }

        int timeoutSeconds = Math.max(1, callbackConfig.getTimeoutSeconds());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackUrl.trim()))
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("X-Build-Operation-Id", operationId)
                    .header("X-Build-Version-No", versionNo)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = callbackHttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                buildOperationRecordService.updateAsyncBuildStatus(
                        operationId, resolveAsyncBuildStatus(response.body(), "异步构建中"));
                log.info("全量编译成功后回调已触发成功，operationId={}, versionNo={}, url={}, status={}",
                        operationId, versionNo, callbackUrl, statusCode);
            } else {
                buildOperationRecordService.updateAsyncBuildStatus(operationId, "触发失败");
                log.warn("全量编译成功后回调返回非成功状态，operationId={}, versionNo={}, url={}, status={}, body={}",
                        operationId, versionNo, callbackUrl, statusCode, response.body());
            }
        } catch (Exception e) {
            buildOperationRecordService.updateAsyncBuildStatus(operationId, "触发失败");
            log.warn("全量编译成功后回调触发失败，operationId={}, versionNo={}, url={}",
                    operationId, versionNo, callbackUrl, e);
        }
    }

    private String resolveAsyncBuildStatus(String responseBody, String fallback) {
        try {
            if (responseBody == null || responseBody.isBlank()) {
                return fallback;
            }
            Map<String, Object> body = callbackObjectMapper.readValue(
                    responseBody, new TypeReference<Map<String, Object>>() {});
            Object data = body.get("data");
            if (!(data instanceof Map<?, ?> dataMap)) {
                return fallback;
            }
            Object asyncBuildStatus = dataMap.get("asyncBuildStatus");
            if (asyncBuildStatus == null) {
                return fallback;
            }
            String value = String.valueOf(asyncBuildStatus).trim();
            return value.isEmpty() ? fallback : value;
        } catch (Exception e) {
            log.warn("解析 8123 回调响应失败，使用默认异步构建状态，reason={}", e.getMessage());
            return fallback;
        }
    }

    private List<Map<String, Object>> pullProjectsParallel(String operationId, String versionNo, List<String> projects) {
        if (projects.isEmpty()) {
            return List.of();
        }

        int threadCount = Math.min(projects.size(),
                Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() * 2, 8)));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        List<Long> recordIds = new ArrayList<>();
        log.info("代码拉取线程池大小：{}", threadCount);

        for (int i = 0; i < projects.size(); i++) {
            String project = projects.get(i);
            recordIds.add(buildOperationRecordService.startPullProject(operationId, versionNo, project, pullSortNo(i)));
            futures.add(executor.submit(() -> codeSyncService.pullProject(project)));
        }

        List<Map<String, Object>> results = new ArrayList<>(Collections.nCopies(projects.size(), null));
        boolean aborted = false;

        for (int i = 0; i < projects.size(); i++) {
            String project = projects.get(i);
            Long recordId = recordIds.get(i);
            Future<Map<String, Object>> future = futures.get(i);

            Map<String, Object> result;
            if (aborted) {
                result = collectFutureAfterAbort(future, project, "拉取阶段已中断，其余工程取消");
            } else {
                result = collectPullFuture(project, future);
                if (!isPullSuccess(result)) {
                    aborted = true;
                    executor.shutdownNow();
                }
            }

            results.set(i, result);
            finishPullRecord(recordId, result);
        }

        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待代码拉取线程池结束时被中断");
        }
        return results;
    }

    private Map<String, Object> runBatch(String operationId, String versionNo, BuildConfig.BatchConfig batchCfg, int batchIndex) {
        String batchName = resolveBatchName(batchCfg, batchIndex);
        boolean parallel = batchCfg.isParallel();
        List<String> projects = batchCfg.getProjects();

        Long batchRecordId = buildOperationRecordService.startBuildBatch(
                operationId, versionNo, batchIndex, batchName, batchSortNo(batchIndex));
        progressTracker.startBatch(batchName, batchIndex);
        log.info("---------- {} 开始（{}，共 {} 个工程）----------",
                batchName, parallel ? "并行" : "串行", projects.size());

        List<Map<String, Object>> projectResults = parallel
                ? buildBatchParallel(operationId, versionNo, batchIndex, projects, batchName)
                : buildBatchSerial(operationId, versionNo, batchIndex, projects, batchName);

        long successCount = countByStatus(projectResults, "SUCCESS");
        long skippedCount = countByStatus(projectResults, "SKIPPED");
        long failedCount = projectResults.size() - successCount - skippedCount;
        String batchStatus = failedCount == 0 && skippedCount == 0 ? "SUCCESS" : "FAILED";

        Map<String, Object> batchResult = new LinkedHashMap<>();
        batchResult.put("batch", batchName);
        batchResult.put("mode", parallel ? "并行" : "串行");
        batchResult.put("successCount", successCount);
        batchResult.put("failedCount", failedCount);
        batchResult.put("skippedCount", skippedCount);
        batchResult.put("projects", projectResults);
        batchResult.put("status", batchStatus);
        batchResult.put("message", "SUCCESS".equals(batchStatus) ? "批次编译成功" : "批次编译失败");

        Map<String, Object> batchExtra = new LinkedHashMap<>();
        batchExtra.put("parallel", parallel);
        batchExtra.put("projectCount", projects.size());
        batchExtra.put("successCount", successCount);
        batchExtra.put("failedCount", failedCount);
        batchExtra.put("skippedCount", skippedCount);
        buildOperationRecordService.finishBuildBatch(batchRecordId, batchStatus,
                safeString(batchResult.get("message")), batchExtra);

        progressTracker.finishBatch(batchName, successCount, failedCount + skippedCount);
        log.info("---------- {} 完成：成功={}, 失败={}, 跳过={} ----------",
                batchName, successCount, failedCount, skippedCount);
        return batchResult;
    }

    private String resolveBatchName(BuildConfig.BatchConfig batchCfg, int batchIndex) {
        return batchCfg.getName() != null ? batchCfg.getName() : "第" + (batchIndex + 1) + "批";
    }

    private boolean isBatchSuccess(Map<String, Object> batchResult) {
        return "SUCCESS".equals(batchResult.get("status"));
    }

    /**
     * 串行编译一批工程（按顺序，一个接一个）
     */
    private List<Map<String, Object>> buildBatchSerial(List<String> projects, String batchName) {
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (String project : projects) {
            Map<String, Object> result = buildOne(project);
            results.add(result);
            if (!isBuildSuccess(result)) {
                log.error("[{}] 串行编译中止：{} 失败", batchName, project);
                break;
            }
        }
        return results;
    }

    /**
     * 并行编译一批工程。
     * 所有工程同时启动；任意一个失败后，立即取消其他未完成的工程并中断整批。
     */
    private List<Map<String, Object>> buildBatchParallel(List<String> projects, String batchName) {
        if (projects.isEmpty()) {
            return List.of();
        }
        int threadCount = projects.size();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();

        for (String project : projects) {
            futures.add(executor.submit(() -> buildOne(project)));
        }

        List<Map<String, Object>> results = new ArrayList<>();
        boolean aborted = false;

        for (int i = 0; i < futures.size(); i++) {
            String project = projects.get(i);
            Future<Map<String, Object>> future = futures.get(i);

            if (aborted) {
                Map<String, Object> cancelResult = collectFutureAfterAbort(future, project, "批次内其他工程失败，已取消");
                results.add(cancelResult);
                continue;
            }

            try {
                Map<String, Object> result = normalizeProjectResult(future.get(
                        buildConfig.getBuildTimeoutMinutes() + 5L, TimeUnit.MINUTES), project);
                results.add(result);

                if (!isBuildSuccess(result)) {
                    aborted = true;
                    executor.shutdownNow();
                    log.error("[{}] 工程 {} 失败，批次内其余工程将被取消", batchName, project);
                }
            } catch (CancellationException e) {
                Map<String, Object> cancelResult = cancelledResult(project, "批次内其他工程失败，已取消");
                results.add(cancelResult);
                aborted = true;
            } catch (TimeoutException e) {
                Map<String, Object> timeoutResult = timeoutResult(project, "等待编译结果超时");
                results.add(timeoutResult);
                aborted = true;
                executor.shutdownNow();
                log.error("[{}] 获取编译结果超时：{}，批次中断", batchName, project, e);
            } catch (Exception e) {
                Map<String, Object> errResult = failureResult(project, "获取编译结果失败：" + e.getMessage(), e);
                results.add(errResult);
                aborted = true;
                executor.shutdownNow();
                log.error("[{}] 并行编译异常：{}，批次中断", batchName, project, e);
            }
        }

        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return results;
    }

    private List<Map<String, Object>> buildBatchSerial(String operationId, String versionNo, int batchIndex,
                                                       List<String> projects, String batchName) {
        if (projects.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < projects.size(); i++) {
            String project = projects.get(i);
            Long recordId = buildOperationRecordService.startBuildProject(
                    operationId, versionNo, batchIndex, batchName, project, projectSortNo(batchIndex, i));
            Map<String, Object> result = buildOne(project);
            results.add(result);
            finishBuildProjectRecord(recordId, result);

            if (!isBuildSuccess(result)) {
                log.error("[{}] 串行编译中止：{} 失败", batchName, project);
                for (int j = i + 1; j < projects.size(); j++) {
                    String skippedProject = projects.get(j);
                    Map<String, Object> skipped = skippedResult(skippedProject, "同批次前置工程失败，未执行");
                    results.add(skipped);
                    buildOperationRecordService.saveBuildProjectRecord(
                            operationId, versionNo, batchIndex, batchName, skippedProject, projectSortNo(batchIndex, j),
                            "SKIPPED", safeString(skipped.get("message")), null, null, null, null, null);
                }
                break;
            }
        }
        return results;
    }

    private List<Map<String, Object>> buildBatchParallel(String operationId, String versionNo, int batchIndex,
                                                         List<String> projects, String batchName) {
        if (projects.isEmpty()) {
            return List.of();
        }

        int threadCount = projects.size();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        List<Long> recordIds = new ArrayList<>();

        for (int i = 0; i < projects.size(); i++) {
            String project = projects.get(i);
            recordIds.add(buildOperationRecordService.startBuildProject(
                    operationId, versionNo, batchIndex, batchName, project, projectSortNo(batchIndex, i)));
            futures.add(executor.submit(() -> buildOne(project)));
        }

        List<Map<String, Object>> results = new ArrayList<>(Collections.nCopies(projects.size(), null));
        boolean aborted = false;

        for (int i = 0; i < futures.size(); i++) {
            String project = projects.get(i);
            Long recordId = recordIds.get(i);
            Future<Map<String, Object>> future = futures.get(i);

            Map<String, Object> result;
            if (aborted) {
                result = collectFutureAfterAbort(future, project, "批次内其他工程失败，已取消");
            } else {
                result = collectBuildFuture(project, future);
                if (!isBuildSuccess(result)) {
                    aborted = true;
                    executor.shutdownNow();
                    log.error("[{}] 工程 {} 失败，批次内其余工程将被取消", batchName, project);
                }
            }

            results.set(i, result);
            finishBuildProjectRecord(recordId, result);
        }

        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return results;
    }

    @Override
    public Map<String, Object> buildOne(String projectName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", projectName);

        String projectDir = buildConfig.getBasePath() + File.separator + projectName;
        File dir = new File(projectDir);

        if (!dir.exists() || !dir.isDirectory()) {
            result.put("status", "SKIP");
            result.put("message", "目录不存在：" + projectDir);
            result.put("errorType", "MISSING_DIR");
            result.put("errorMessage", "目录不存在：" + projectDir);
            log.warn("工程目录不存在，跳过：{}", projectDir);
            return result;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String logFile = buildConfig.getLogPath() + File.separator + projectName + "_" + timestamp + ".log";

        progressTracker.startProject(projectName);
        log.info("[{}] 开始编译", projectName);
        long startMs = System.currentTimeMillis();
        Process process = null;

        try {
            ProcessBuilder pb = new ProcessBuilder(buildMvnArgs("clean", "install", "-DskipTests"));
            pb.directory(dir);
            pb.redirectErrorStream(true);
            pb.environment().put("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8");

            process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
                 FileWriter writer = new FileWriter(logFile)) {

                writer.write("=== 编译开始：" + projectName + " @ " + LocalDateTime.now() + " ===\n\n");
                String line;
                while ((line = reader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        process.destroyForcibly();
                        throw new InterruptedException("编译任务被取消");
                    }
                    writer.write(line + "\n");
                    if (line.contains("BUILD SUCCESS") || line.contains("BUILD FAILURE")
                            || line.contains("[ERROR]") || line.startsWith("[INFO] Building")) {
                        log.info("[{}] {}", projectName, line.trim());
                    }
                }
                writer.flush();
            }

            boolean finished = process.waitFor(buildConfig.getBuildTimeoutMinutes(), TimeUnit.MINUTES);
            long costSeconds = (System.currentTimeMillis() - startMs) / 1000;

            if (!finished) {
                process.destroyForcibly();
                result.put("status", "TIMEOUT");
                result.put("message", "编译超时（" + buildConfig.getBuildTimeoutMinutes() + " 分钟）");
                result.put("errorType", "TIMEOUT");
                result.put("errorMessage", "编译超时（" + buildConfig.getBuildTimeoutMinutes() + " 分钟）");
                result.put("errorStack", readLogTail(logFile, 80));
                result.put("costSeconds", costSeconds);
                result.put("logFile", logFile);
                progressTracker.finishProject(projectName, "✗ 编译超时", costSeconds);
                log.error("[{}] 编译超时（{}s），超时限制={}min", projectName, costSeconds,
                        buildConfig.getBuildTimeoutMinutes());
                return result;
            }

            int exitCode = process.exitValue();
            result.put("costSeconds", costSeconds);
            result.put("logFile", logFile);
            result.put("exitCode", exitCode);

            if (exitCode == 0) {
                result.put("status", "SUCCESS");
                result.put("message", "编译成功");
                progressTracker.finishProject(projectName, "✓ 编译成功", costSeconds);
                log.info("[{}] 编译成功（{}s）", projectName, costSeconds);
            } else {
                result.put("status", "FAILED");
                result.put("message", "编译失败，exit=" + exitCode + "，详见：" + logFile);
                result.put("errorType", "PROCESS_EXIT");
                result.put("errorMessage", "编译失败，exit=" + exitCode);
                result.put("errorStack", readLogTail(logFile, 80));
                progressTracker.finishProject(projectName, "✗ 编译失败 exit=" + exitCode, costSeconds);
                log.error("[{}] 编译失败（{}s），exit={}", projectName, costSeconds, exitCode);
            }

        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            long costSeconds = (System.currentTimeMillis() - startMs) / 1000;
            result.put("status", "CANCELLED");
            result.put("message", "编译被取消");
            result.put("errorType", "INTERRUPTED");
            result.put("errorMessage", "编译被取消");
            result.put("errorStack", stackTrace(e));
            result.put("costSeconds", costSeconds);
            result.put("logFile", logFile);
            progressTracker.finishProject(projectName, "✗ 编译被取消", costSeconds);
            log.warn("[{}] 编译被取消", projectName);
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            long costSeconds = (System.currentTimeMillis() - startMs) / 1000;
            result.put("status", "ERROR");
            result.put("message", "编译异常：" + e.getMessage());
            result.put("errorType", e.getClass().getName());
            result.put("errorMessage", e.getMessage());
            result.put("errorStack", stackTrace(e));
            result.put("costSeconds", costSeconds);
            result.put("logFile", logFile);
            progressTracker.finishProject(projectName, "✗ 编译异常", costSeconds);
            log.error("[{}] 编译异常", projectName, e);
        }

        return result;
    }

    @Override
    public Map<String, Object> buildBatch(String batchName) {
        if (!building.compareAndSet(false, true)) {
            Map<String, Object> busyResult = new LinkedHashMap<>();
            busyResult.put("batch", batchName);
            busyResult.put("status", "BUSY");
            busyResult.put("message", "当前有编译任务正在进行，请稍后再试");
            return busyResult;
        }

        BuildConfig.BatchConfig batchCfg = buildConfig.getBatches().stream()
                .filter(b -> batchName.equals(b.getName()))
                .findFirst()
                .orElse(null);

        if (batchCfg == null) {
            building.set(false);
            throw new IllegalArgumentException("批次不存在：" + batchName
                    + "，可用批次：" + buildConfig.getBatches().stream()
                    .map(BuildConfig.BatchConfig::getName).toList());
        }

        progressTracker.resetForBatch(batchName);
        boolean success = true;
        try {
            ensureLogDir();
            log.info("========== 开始批次编译：{} ==========", batchName);

            progressTracker.startBatch(batchName, 0);

            List<String> projects = batchCfg.getProjects();
            boolean parallel = batchCfg.isParallel();
            log.info("批次 {} （{}，共 {} 个工程）", batchName, parallel ? "并行" : "串行", projects.size());

            List<Map<String, Object>> projectResults = parallel
                    ? buildBatchParallel(projects, batchName)
                    : buildBatchSerial(projects, batchName);

            long successCount = countByStatus(projectResults, "SUCCESS");
            long skippedCount = countByStatus(projectResults, "SKIPPED");
            long failedCount = projectResults.size() - successCount - skippedCount;

            progressTracker.finishBatch(batchName, successCount, failedCount + skippedCount);
            success = failedCount == 0 && skippedCount == 0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("batch", batchName);
            result.put("mode", parallel ? "并行" : "串行");
            result.put("successCount", successCount);
            result.put("failedCount", failedCount);
            result.put("skippedCount", skippedCount);
            result.put("projects", projectResults);
            result.put("status", success ? "SUCCESS" : "FAILED");
            log.info("========== 批次编译结束：{} 成功={} 失败={} 跳过={} ==========",
                    batchName, successCount, failedCount, skippedCount);
            return result;

        } finally {
            progressTracker.finish(success);
            if (success) {
                clearLogDir();
            }
            building.set(false);
        }
    }

    @Override
    @Async
    public void buildBatchAsync(String batchName) {
        log.info("批次编译异步任务已启动：{}", batchName);
        try {
            Map<String, Object> result = buildBatch(batchName);
            log.info("批次编译异步完成：{} 成功={} 失败={}",
                    batchName, result.get("successCount"), result.get("failedCount"));
        } catch (IllegalArgumentException e) {
            log.error("批次编译异步启动失败：{}", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> listBatches() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<BuildConfig.BatchConfig> batches = buildConfig.getBatches();
        for (int i = 0; i < batches.size(); i++) {
            BuildConfig.BatchConfig cfg = batches.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("batchIndex", i);
            item.put("name", cfg.getName());
            item.put("parallel", cfg.isParallel());
            item.put("projects", cfg.getProjects());
            item.put("projectCount", cfg.getProjects().size());
            result.add(item);
        }
        return result;
    }

    private String buildMvnCommand() {
        String mvnHome = buildConfig.getMvnHome();
        if (mvnHome != null && !mvnHome.trim().isEmpty()) {
            return mvnHome + File.separator + "bin" + File.separator + "mvn";
        }
        return "mvn";
    }

    /**
     * 构建完整的 mvn 命令参数列表。
     * 固定加入 -U（--update-snapshots），确保每次编译都从远程拉取最新 SNAPSHOT jar，
     * 避免因本地 .m2 缓存的旧 SNAPSHOT 导致编译失败。
     */
    private List<String> buildMvnArgs(String... goals) {
        List<String> args = new ArrayList<>();
        args.add(buildMvnCommand());
        String settingsFile = buildConfig.getSettingsFile();
        if (settingsFile != null && !settingsFile.trim().isEmpty()) {
            args.add("-s");
            args.add(settingsFile.trim());
        }
        args.add("-U");
        Collections.addAll(args, goals);
        return args;
    }

    private void ensureLogDir() {
        File dir = new File(buildConfig.getLogPath());
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 清空日志目录下的所有日志文件（仅删文件，保留目录本身）。
     * 只在全量/批次编译全部成功后调用，失败时保留日志供排查。
     */
    private void clearLogDir() {
        File dir = new File(buildConfig.getLogPath());
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        int deleted = 0;
        for (File f : files) {
            if (f.isFile()) {
                if (f.delete()) {
                    deleted++;
                } else {
                    log.warn("日志文件删除失败：{}", f.getAbsolutePath());
                }
            }
        }
        log.info("编译成功，已清空日志目录：{} 个文件删除（{}）", deleted, dir.getAbsolutePath());
    }

    private List<String> extractAllProjects(List<BuildConfig.BatchConfig> batches) {
        return batches.stream()
                .flatMap(b -> b.getProjects().stream())
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> busyBatchResults() {
        Map<String, Object> busyResult = new LinkedHashMap<>();
        busyResult.put("status", "BUSY");
        busyResult.put("message", "当前有编译任务正在进行，请稍后再试");
        return List.of(busyResult);
    }

    private void appendSkippedBatches(String operationId, String versionNo, List<Map<String, Object>> allBatchResults,
                                      List<BuildConfig.BatchConfig> batches, int fromIndex, String reason) {
        for (int j = fromIndex; j < batches.size(); j++) {
            BuildConfig.BatchConfig skippedBatch = batches.get(j);
            String batchName = resolveBatchName(skippedBatch, j);

            List<Map<String, Object>> skippedProjects = new ArrayList<>();
            for (int k = 0; k < skippedBatch.getProjects().size(); k++) {
                String project = skippedBatch.getProjects().get(k);
                Map<String, Object> skippedProject = skippedResult(project, reason);
                skippedProjects.add(skippedProject);
                buildOperationRecordService.saveBuildProjectRecord(
                        operationId, versionNo, j, batchName, project, projectSortNo(j, k),
                        "SKIPPED", reason, null, null, null, null, null);
            }

            Map<String, Object> batchResult = new LinkedHashMap<>();
            batchResult.put("batch", batchName);
            batchResult.put("mode", skippedBatch.isParallel() ? "并行" : "串行");
            batchResult.put("status", "SKIPPED");
            batchResult.put("message", reason);
            batchResult.put("successCount", 0);
            batchResult.put("failedCount", 0);
            batchResult.put("skippedCount", skippedProjects.size());
            batchResult.put("projects", skippedProjects);
            allBatchResults.add(batchResult);

            Map<String, Object> batchExtra = new LinkedHashMap<>();
            batchExtra.put("parallel", skippedBatch.isParallel());
            batchExtra.put("projectCount", skippedBatch.getProjects().size());
            batchExtra.put("skippedCount", skippedProjects.size());
            buildOperationRecordService.saveBuildBatchRecord(
                    operationId, versionNo, j, batchName, batchSortNo(j), "SKIPPED", reason, batchExtra);
        }
    }

    private int pullSortNo(int projectIndex) {
        return 1000 + projectIndex;
    }

    private int batchSortNo(int batchIndex) {
        return 100000 + batchIndex * 1000;
    }

    private int projectSortNo(int batchIndex, int projectIndex) {
        return batchSortNo(batchIndex) + projectIndex + 1;
    }

    private void finishPullRecord(Long recordId, Map<String, Object> result) {
        Map<String, Object> extra = new LinkedHashMap<>();
        putIfPresent(extra, "output", result.get("output"));
        putIfPresent(extra, "localPath", result.get("localPath"));
        buildOperationRecordService.finishPullProject(
                recordId,
                safeString(result.get("status")),
                safeString(result.get("message")),
                safeString(result.get("errorType")),
                safeString(result.get("errorMessage")),
                safeString(result.get("errorStack")),
                extra);
    }

    private void finishBuildProjectRecord(Long recordId, Map<String, Object> result) {
        Map<String, Object> extra = new LinkedHashMap<>();
        putIfPresent(extra, "costSeconds", result.get("costSeconds"));
        putIfPresent(extra, "exitCode", result.get("exitCode"));
        buildOperationRecordService.finishBuildProject(
                recordId,
                safeString(result.get("status")),
                safeString(result.get("message")),
                safeString(result.get("errorType")),
                safeString(result.get("errorMessage")),
                safeString(result.get("errorStack")),
                safeString(result.get("logFile")),
                extra);
    }

    private Map<String, Object> collectPullFuture(String project, Future<Map<String, Object>> future) {
        try {
            return normalizeProjectResult(future.get(), project);
        } catch (CancellationException e) {
            return cancelledResult(project, "拉取任务被取消");
        } catch (Exception e) {
            log.error("并行拉取失败：{}", project, e);
            return failureResult(project, "并行拉取异常：" + e.getMessage(), e);
        }
    }

    private Map<String, Object> collectBuildFuture(String project, Future<Map<String, Object>> future) {
        try {
            return normalizeProjectResult(
                    future.get(buildConfig.getBuildTimeoutMinutes() + 5L, TimeUnit.MINUTES), project);
        } catch (CancellationException e) {
            return cancelledResult(project, "批次内其他工程失败，已取消");
        } catch (TimeoutException e) {
            future.cancel(true);
            return timeoutResult(project, "等待编译结果超时");
        } catch (Exception e) {
            log.error("并行编译失败：{}", project, e);
            return failureResult(project, "获取编译结果失败：" + e.getMessage(), e);
        }
    }

    private Map<String, Object> collectFutureAfterAbort(Future<Map<String, Object>> future, String project, String message) {
        if (!future.isDone()) {
            future.cancel(true);
        }
        try {
            return normalizeProjectResult(future.get(), project);
        } catch (CancellationException e) {
            return cancelledResult(project, message);
        } catch (Exception e) {
            return failureResult(project, "获取取消结果失败：" + e.getMessage(), e);
        }
    }

    private boolean isPullSuccess(Map<String, Object> result) {
        return "SUCCESS".equals(result.get("status"));
    }

    private boolean isBuildSuccess(Map<String, Object> result) {
        return "SUCCESS".equals(result.get("status"));
    }

    private long countByStatus(List<Map<String, Object>> results, String status) {
        return results.stream().filter(r -> status.equals(r.get("status"))).count();
    }

    private long countByStatuses(List<Map<String, Object>> results, String... statuses) {
        List<String> accepted = List.of(statuses);
        return results.stream().filter(r -> accepted.contains(r.get("status"))).count();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castProjectResults(Object value) {
        if (value instanceof List<?>) {
            return (List<Map<String, Object>>) value;
        }
        return List.of();
    }

    private Map<String, Object> findFirstProblemResult(List<Map<String, Object>> results) {
        for (Map<String, Object> result : results) {
            String status = safeString(result.get("status"));
            if ("FAILED".equals(status) || "ERROR".equals(status) || "TIMEOUT".equals(status) || "SKIP".equals(status)) {
                return result;
            }
        }
        for (Map<String, Object> result : results) {
            if (!"SUCCESS".equals(result.get("status"))) {
                return result;
            }
        }
        return null;
    }

    private Map<String, Object> normalizeProjectResult(Map<String, Object> result, String project) {
        if (result == null) {
            return failureResult(project, "任务未返回结果", null);
        }
        if (!result.containsKey("project")) {
            result.put("project", project);
        }
        return result;
    }

    private Map<String, Object> cancelledResult(String project, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project);
        result.put("status", "CANCELLED");
        result.put("message", message);
        result.put("errorType", "CANCELLED");
        result.put("errorMessage", message);
        return result;
    }

    private Map<String, Object> skippedResult(String project, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project);
        result.put("status", "SKIPPED");
        result.put("message", message);
        return result;
    }

    private Map<String, Object> timeoutResult(String project, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project);
        result.put("status", "TIMEOUT");
        result.put("message", message);
        result.put("errorType", "TIMEOUT");
        result.put("errorMessage", message);
        return result;
    }

    private Map<String, Object> failureResult(String project, String message, Throwable throwable) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", project);
        result.put("status", "FAILED");
        result.put("message", message);
        if (throwable != null) {
            result.put("errorType", throwable.getClass().getName());
            result.put("errorMessage", throwable.getMessage());
            result.put("errorStack", stackTrace(throwable));
        }
        return result;
    }

    private String readLogTail(String logFile, int maxLines) {
        if (logFile == null || logFile.trim().isEmpty()) {
            return null;
        }
        File file = new File(logFile);
        if (!file.exists() || !file.isFile()) {
            return null;
        }

        Deque<String> tail = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (tail.size() == maxLines) {
                    tail.removeFirst();
                }
                tail.addLast(line);
            }
        } catch (Exception e) {
            return "读取日志失败：" + e.getMessage();
        }
        return String.join("\n", tail);
    }

    private String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String safeString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
