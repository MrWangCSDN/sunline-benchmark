package com.sunline.dict.service;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 编译进度跟踪器（全量 / 单批次通用）
 *
 * <p>维护当前编译任务的整体状态，并通过 SSE 实时推送给订阅方。
 * 全量编译和单批次编译共用同一个状态机（同一时刻只允许一个任务运行）。
 */
@Component
public class BuildProgressTracker {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public enum Status { IDLE, RUNNING, SUCCESS, FAILED }
    public enum Phase { IDLE, PULLING, BUILDING, FINISHED }

    /** 触发模式：全量 or 单批次 */
    public enum Mode { ALL, BATCH }

    // -------- 当前状态（volatile 保证可见性）--------
    private volatile Status status = Status.IDLE;
    private volatile Phase  phase  = Phase.IDLE;
    private volatile Mode   mode   = Mode.ALL;
    private volatile String operationId = "";
    private volatile String taskLabel = "";       // 全量时为"全量拉取+编译"，单批次时为批次名
    private volatile String currentBatch = "";
    private volatile String currentProject = "";
    private volatile int totalBatches = 0;
    private volatile int completedBatches = 0;
    private volatile int totalPullProjects = 0;
    private volatile int completedPullProjects = 0;
    private volatile long startMs = 0;
    private volatile String finishMessage = "";

    /** 全部日志行（供新订阅者回放） */
    private final List<String> logs = new CopyOnWriteArrayList<>();

    /** 当前活跃的 SSE 连接 */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** 当前活跃批次 / 工程（支持并行场景） */
    private final List<String> activeBatches = new CopyOnWriteArrayList<>();
    private final List<String> activeProjects = new CopyOnWriteArrayList<>();

    // ===================================================
    // 供 BuildServiceImpl 调用的进度上报方法
    // ===================================================

    /** 重置状态，开始全量编译 */
    public void reset(String operationId, int totalBatches) {
        this.mode = Mode.ALL;
        this.phase = Phase.PULLING;
        this.operationId = operationId;
        this.taskLabel = "全量拉取+编译";
        this.status = Status.RUNNING;
        this.totalBatches = totalBatches;
        this.completedBatches = 0;
        this.totalPullProjects = 0;
        this.completedPullProjects = 0;
        this.currentBatch = "";
        this.currentProject = "";
        this.startMs = System.currentTimeMillis();
        this.finishMessage = "";
        activeBatches.clear();
        activeProjects.clear();
        logs.clear();
        addLog("========== 全量拉取+编译开始，共 " + totalBatches + " 批 ==========");
    }

    /** 重置状态，开始单批次编译 */
    public void resetForBatch(String batchName) {
        this.mode = Mode.BATCH;
        this.phase = Phase.BUILDING;
        this.operationId = "";
        this.taskLabel = batchName;
        this.status = Status.RUNNING;
        this.totalBatches = 1;
        this.completedBatches = 0;
        this.totalPullProjects = 0;
        this.completedPullProjects = 0;
        this.currentBatch = batchName;
        this.currentProject = "";
        this.startMs = System.currentTimeMillis();
        this.finishMessage = "";
        activeBatches.clear();
        activeProjects.clear();
        logs.clear();
        addLog("========== 批次编译开始：" + batchName + " ==========");
    }

    /** 编译前代码拉取阶段开始 */
    public void startPullPhase(int total) {
        this.phase = Phase.PULLING;
        this.totalPullProjects = total;
        this.completedPullProjects = 0;
        addLog("---------- 编译前代码拉取（共 " + total + " 个工程）----------");
    }

    /** 单个工程拉取结果 */
    public void logPullResult(String projectName, String status, String message) {
        String icon = "SUCCESS".equals(status) ? "✓"
                : ("SKIP".equals(status) ? "~"
                : ("CANCELLED".equals(status) ? "!" : "✗"));
        this.completedPullProjects++;
        addLog("[拉取] " + icon + " " + projectName + "：" + message);
    }

    /** 拉取阶段汇总 */
    public void finishPullPhase(long success, long skip, long failed, long cancelled) {
        this.phase = Phase.BUILDING;
        addLog("---------- 代码拉取完成：成功=" + success + " 跳过=" + skip
                + " 失败=" + failed + " 取消=" + cancelled + " ----------");
    }

    /** 批次开始 */
    public void startBatch(String batchName, int batchIndex) {
        if (!activeBatches.contains(batchName)) {
            activeBatches.add(batchName);
        }
        this.currentBatch = joinActive(activeBatches);
        addLog("---------- [" + (batchIndex + 1) + "/" + totalBatches + "] " + batchName + " 开始 ----------");
    }

    /** 批次结束 */
    public void finishBatch(String batchName, long successCount, long failedCount) {
        this.completedBatches++;
        activeBatches.remove(batchName);
        this.currentBatch = joinActive(activeBatches);
        addLog("---------- " + batchName + " 完成：成功=" + successCount + " 失败=" + failedCount + " ----------");
    }

    /** 工程开始编译 */
    public void startProject(String projectName) {
        if (!activeProjects.contains(projectName)) {
            activeProjects.add(projectName);
        }
        this.currentProject = joinActive(activeProjects);
        addLog("[" + projectName + "] 开始编译...");
    }

    /** 工程编译结束 */
    public void finishProject(String projectName, String statusStr, long costSeconds) {
        activeProjects.remove(projectName);
        this.currentProject = joinActive(activeProjects);
        addLog("[" + projectName + "] " + statusStr + "（耗时 " + costSeconds + "s）");
    }

    /** 编译任务结束（全量或批次通用） */
    public void finish(boolean success) {
        long totalSeconds = (System.currentTimeMillis() - startMs) / 1000;
        this.status = success ? Status.SUCCESS : Status.FAILED;
        this.phase = Phase.FINISHED;
        activeBatches.clear();
        activeProjects.clear();
        this.currentBatch = "";
        this.currentProject = "";
        String label = taskLabel.isEmpty() ? "编译" : taskLabel;
        this.finishMessage = (success ? "✓ " : "✗ ") + label + (success ? " 成功" : " 失败") + "，总耗时 " + totalSeconds + "s";
        addLog("========== " + finishMessage + " ==========");
        broadcast("done", finishMessage);
        for (SseEmitter e : emitters) {
            try { e.complete(); } catch (Exception ignored) {}
        }
        emitters.clear();
    }

    // ===================================================
    // 供 Controller 调用
    // ===================================================

    /**
     * 新建一个 SSE 订阅，先回放历史日志，再实时推送后续内容。
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1 小时超时

        // 回放已有日志
        try {
            for (String line : logs) {
                emitter.send(SseEmitter.event().name("log").data((Object) line));
            }
            // 如果已结束，直接发 done 并关闭
            if (status == Status.SUCCESS || status == Status.FAILED) {
                emitter.send(SseEmitter.event().name("done").data((Object) finishMessage));
                emitter.complete();
                return emitter;
            }
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        return emitter;
    }

    /**
     * 返回当前编译状态快照（用于 JSON 轮询）
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status.name());
        map.put("phase", phase.name());
        map.put("mode", mode.name());
        map.put("operationId", operationId);
        map.put("taskLabel", taskLabel);
        map.put("totalBatches", totalBatches);
        map.put("completedBatches", completedBatches);
        map.put("totalPullProjects", totalPullProjects);
        map.put("completedPullProjects", completedPullProjects);
        map.put("currentBatch", currentBatch);
        map.put("currentProject", currentProject);
        map.put("activeBatches", new ArrayList<>(activeBatches));
        map.put("activeProjects", new ArrayList<>(activeProjects));
        map.put("costSeconds", startMs > 0 ? (System.currentTimeMillis() - startMs) / 1000 : 0);
        map.put("finishMessage", finishMessage);
        // 最近 50 行日志
        List<String> recent = new ArrayList<>(logs);
        if (recent.size() > 50) {
            recent = recent.subList(recent.size() - 50, recent.size());
        }
        map.put("recentLogs", recent);
        return map;
    }

    // ===================================================
    // 内部工具
    // ===================================================

    private void addLog(String message) {
        String line = "[" + LocalDateTime.now().format(FMT) + "] " + message;
        logs.add(line);
        broadcast("log", line);
    }

    private String joinActive(List<String> items) {
        return items.isEmpty() ? "" : String.join(", ", items);
    }

    private void broadcast(String eventName, String data) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data((Object) data));
            } catch (Exception e) {
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
    }
}
