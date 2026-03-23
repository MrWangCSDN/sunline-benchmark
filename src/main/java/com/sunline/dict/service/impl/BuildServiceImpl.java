package com.sunline.dict.service.impl;

import com.sunline.dict.config.BuildConfig;
import com.sunline.dict.service.BuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工程编译服务实现
 *
 * <p>执行逻辑：
 * <ol>
 *   <li>批次之间串行（前一批全部成功才开始下一批）</li>
 *   <li>批次内部并行（同一批中的工程同时开始编译）</li>
 * </ol>
 */
@Service
public class BuildServiceImpl implements BuildService {

    private static final Logger log = LoggerFactory.getLogger(BuildServiceImpl.class);

    @Autowired
    private BuildConfig buildConfig;

    /** 防止并发重复触发 */
    private final AtomicBoolean building = new AtomicBoolean(false);

    @Override
    public List<Map<String, Object>> buildAll() {
        if (!building.compareAndSet(false, true)) {
            Map<String, Object> busyResult = new LinkedHashMap<>();
            busyResult.put("status", "BUSY");
            busyResult.put("message", "当前有编译任务正在进行，请稍后再试");
            return List.of(busyResult);
        }

        List<Map<String, Object>> allBatchResults = new ArrayList<>();
        long totalStart = System.currentTimeMillis();

        List<BuildConfig.BatchConfig> batches = buildConfig.getBatches();

        try {
            ensureLogDir();
            log.info("==================== 开始全量编译，共 {} 批 ====================", batches.size());

            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                BuildConfig.BatchConfig batchCfg = batches.get(batchIndex);
                String batchName = batchCfg.getName() != null ? batchCfg.getName() : "第" + (batchIndex + 1) + "批";
                boolean parallel = batchCfg.isParallel();
                List<String> projects = batchCfg.getProjects();

                log.info("---------- {} 开始（{}，共 {} 个工程）----------",
                        batchName, parallel ? "并行" : "串行", projects.size());

                Map<String, Object> batchResult = new LinkedHashMap<>();
                batchResult.put("batch", batchName);
                batchResult.put("mode", parallel ? "并行" : "串行");

                List<Map<String, Object>> projectResults;
                if (parallel) {
                    projectResults = buildBatchParallel(projects, batchName);
                } else {
                    projectResults = buildBatchSerial(projects, batchName);
                }

                batchResult.put("projects", projectResults);

                // 统计成功/失败
                long successCount = projectResults.stream()
                        .filter(r -> "SUCCESS".equals(r.get("status"))).count();
                long failedCount = projectResults.size() - successCount;
                batchResult.put("successCount", successCount);
                batchResult.put("failedCount", failedCount);

                allBatchResults.add(batchResult);

                log.info("---------- {} 完成：成功={}, 失败={} ----------",
                        batchName, successCount, failedCount);

                // 有失败则停止后续批次
                if (failedCount > 0) {
                    log.error("批次 {} 存在编译失败，停止后续批次", batchName);

                    Map<String, Object> abortResult = new LinkedHashMap<>();
                    abortResult.put("batch", "后续批次");
                    abortResult.put("status", "ABORTED");
                    abortResult.put("message", "由于 " + batchName + " 编译失败，后续批次已跳过");
                    allBatchResults.add(abortResult);
                    break;
                }
            }

        } finally {
            building.set(false);
        }

        long totalCost = (System.currentTimeMillis() - totalStart) / 1000;
        log.info("==================== 全量编译结束，总耗时 {}s ====================", totalCost);
        return allBatchResults;
    }

    /**
     * 串行编译一批工程（按顺序，一个接一个）
     */
    private List<Map<String, Object>> buildBatchSerial(List<String> projects, String batchName) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (String project : projects) {
            Map<String, Object> result = buildOne(project);
            results.add(result);
            if (!"SUCCESS".equals(result.get("status"))) {
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
                // 已中断，取消尚未完成的任务
                future.cancel(true);
                Map<String, Object> cancelResult = new LinkedHashMap<>();
                cancelResult.put("project", project);
                cancelResult.put("status", "CANCELLED");
                cancelResult.put("message", "批次内其他工程失败，已取消");
                results.add(cancelResult);
                log.warn("[{}] 批次已中断，取消编译：{}", batchName, project);
                continue;
            }

            try {
                Map<String, Object> result = future.get(buildConfig.getBuildTimeoutMinutes() + 5L, TimeUnit.MINUTES);
                results.add(result);

                if (!"SUCCESS".equals(result.get("status"))) {
                    // 该工程失败，标记中断，取消其余未开始/进行中的任务
                    aborted = true;
                    executor.shutdownNow(); // 中断线程池
                    log.error("[{}] 工程 {} 失败，批次内其余工程将被取消", batchName, project);
                }
            } catch (Exception e) {
                Map<String, Object> errResult = new LinkedHashMap<>();
                errResult.put("project", project);
                errResult.put("status", "ERROR");
                errResult.put("message", "获取编译结果失败：" + e.getMessage());
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

    @Override
    public Map<String, Object> buildOne(String projectName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", projectName);

        String projectDir = buildConfig.getBasePath() + File.separator + projectName;
        File dir = new File(projectDir);

        if (!dir.exists() || !dir.isDirectory()) {
            result.put("status", "SKIP");
            result.put("message", "目录不存在：" + projectDir);
            log.warn("工程目录不存在，跳过：{}", projectDir);
            return result;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String logFile = buildConfig.getLogPath() + File.separator + projectName + "_" + timestamp + ".log";

        log.info("[{}] 开始编译", projectName);
        long startMs = System.currentTimeMillis();

        try {
            ProcessBuilder pb = new ProcessBuilder(buildMvnArgs("clean", "install", "-DskipTests"));
            pb.directory(dir);
            pb.redirectErrorStream(true);
            pb.environment().put("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8");

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
                 FileWriter writer = new FileWriter(logFile)) {

                writer.write("=== 编译开始：" + projectName + " @ " + LocalDateTime.now() + " ===\n\n");
                String line;
                while ((line = reader.readLine()) != null) {
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
                result.put("costSeconds", costSeconds);
                result.put("logFile", logFile);
                log.error("[{}] 编译超时（{}s），超时限制={}min", projectName, costSeconds, buildConfig.getBuildTimeoutMinutes());
                return result;
            }

            int exitCode = process.exitValue();
            result.put("costSeconds", costSeconds);
            result.put("logFile", logFile);

            if (exitCode == 0) {
                result.put("status", "SUCCESS");
                result.put("message", "编译成功");
                log.info("[{}] 编译成功（{}s）", projectName, costSeconds);
            } else {
                result.put("status", "FAILED");
                result.put("message", "编译失败，exit=" + exitCode + "，详见：" + logFile);
                log.error("[{}] 编译失败（{}s），exit={}", projectName, costSeconds, exitCode);
            }

        } catch (Exception e) {
            long costSeconds = (System.currentTimeMillis() - startMs) / 1000;
            result.put("status", "ERROR");
            result.put("message", "编译异常：" + e.getMessage());
            result.put("costSeconds", costSeconds);
            result.put("logFile", logFile);
            log.error("[{}] 编译异常", projectName, e);
        }

        return result;
    }

    @Override
    @Async
    public void buildAllAsync() {
        log.info("异步编译任务已启动");
        List<Map<String, Object>> results = buildAll();
        log.info("异步编译完成，各批次结果：");
        results.forEach(r -> log.info("  批次：{} - 成功：{} 失败：{}",
                r.get("batch"), r.get("successCount"), r.get("failedCount")));
    }

    private String buildMvnCommand() {
        String mvnHome = buildConfig.getMvnHome();
        if (mvnHome != null && !mvnHome.trim().isEmpty()) {
            return mvnHome + File.separator + "bin" + File.separator + "mvn";
        }
        return "mvn";
    }

    /**
     * 构建完整的 mvn 命令参数列表（含 -s settings 参数）
     */
    private List<String> buildMvnArgs(String... goals) {
        List<String> args = new ArrayList<>();
        args.add(buildMvnCommand());
        String settingsFile = buildConfig.getSettingsFile();
        if (settingsFile != null && !settingsFile.trim().isEmpty()) {
            args.add("-s");
            args.add(settingsFile.trim());
        }
        for (String goal : goals) {
            args.add(goal);
        }
        return args;
    }

    private void ensureLogDir() {
        File dir = new File(buildConfig.getLogPath());
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }
}
