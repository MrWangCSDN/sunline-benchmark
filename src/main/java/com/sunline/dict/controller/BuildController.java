package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.entity.BuildOperationRecord;
import com.sunline.dict.service.BuildOperationRecordService;
import com.sunline.dict.service.BuildProgressTracker;
import com.sunline.dict.service.BuildService;
import com.sunline.dict.service.CodeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 工程编译接口
 *
 * <p>设计原则：Webhook 不自动触发编译。
 * 编译与代码同步均由显式接口触发。
 *
 * <p>接口列表：
 * <ul>
 *   <li>GET  /api/build/batches          - 查询所有已配置批次（不执行编译）</li>
 *   <li>POST /api/build/clone/all        - 全量 clone/pull 所有批次工程的 master 分支</li>
 *   <li>POST /api/build/all              - 先全量拉取代码，再全量编译（同步，等待完成后返回）</li>
 *   <li>POST /api/build/all/async        - 先全量拉取代码，再全量编译（异步，返回 operationId）</li>
 *   <li>GET  /api/build/all/progress     - SSE 实时订阅编译进度（全量或批次均推到此处）</li>
 *   <li>GET  /api/build/all/status       - JSON 轮询当前编译状态快照</li>
 *   <li>GET  /api/build/records/recent   - 查询当前保留的最近一次全量拉取+编译任务</li>
 *   <li>GET  /api/build/records/{operationId} - 查询一次全量拉取+编译任务的完整明细</li>
 *   <li>POST /api/build/async-build/status - 更新 Neo4j 异步构建状态（免鉴权）</li>
 *   <li>POST /api/build/batch/{name}     - 触发单个批次编译（异步，立即返回）</li>
 *   <li>POST /api/build/project/{name}   - 触发单个工程编译（同步）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/build")
public class BuildController {

    private static final Logger log = LoggerFactory.getLogger(BuildController.class);

    @Autowired
    private BuildService buildService;

    @Autowired
    private BuildProgressTracker progressTracker;

    @Autowired
    private CodeSyncService codeSyncService;

    @Autowired
    private BuildOperationRecordService buildOperationRecordService;

    // ===================================================
    // 批次信息查询
    // ===================================================

    /**
     * 查询所有已配置批次（不执行编译）
     * GET /api/build/batches
     *
     * <p>返回示例：[{"batchIndex":0,"name":"第一批-基础字典","parallel":false,"projects":["ccbs-dict"],"projectCount":1}, ...]
     */
    @GetMapping("/batches")
    public Result<List<Map<String, Object>>> listBatches() {
        return Result.success(buildService.listBatches());
    }

    // ===================================================
    // 全量 clone
    // ===================================================

    /**
     * 全量 clone/pull：将 application.yml build.batches 中配置的所有工程 clone 到本地。
     * POST /api/build/clone/all
     *
     * <p>逻辑：
     * <ul>
     *   <li>目录不存在 → git clone -b master --single-branch</li>
     *   <li>目录已存在且是 git 仓库 → git fetch + reset --hard origin/master</li>
     * </ul>
     * <p>通过 GitLab API 按工程名搜索获取仓库地址，无需手动配置 URL。
     * <p>耗时较长（网络 + 代码量），建议在服务器上调用，不要在本地开发环境执行。
     */
    @PostMapping("/clone/all")
    public Result<List<Map<String, Object>>> cloneAll() {
        log.info("触发全量 clone/pull（同步）");
        List<Map<String, Object>> results = codeSyncService.cloneAllBatchProjects();
        long success = results.stream().filter(r -> !"FAILED".equals(r.get("status"))).count();
        long failed  = results.stream().filter(r -> "FAILED".equals(r.get("status"))).count();
        if (failed > 0) {
            return Result.error("全量 clone 完成，但有 " + failed + " 个工程失败，成功 " + success + " 个，详见 results");
        }
        return Result.success(results);
    }

    // ===================================================
    // 全量编译
    // ===================================================

    /**
     * 先全量拉取代码，再执行全量编译（同步，等待全部批次完成后返回）
     * POST /api/build/all
     * 工程较多时耗时极长，生产环境建议用 async 接口
     */
    @PostMapping("/all")
    public Result<List<Map<String, Object>>> buildAll() {
        log.info("触发全量拉取+编译（同步）");
        List<Map<String, Object>> results = buildService.buildAll();
        return Result.success(results);
    }

    /**
     * 先全量拉取代码，再执行全量编译（异步，立即返回 operationId，通过 SSE/status/records 接口查看进度）
     * POST /api/build/all/async
     */
    @PostMapping("/all/async")
    public Result<Map<String, Object>> buildAllAsync() {
        log.info("触发全量拉取+编译（异步）");
        try {
            String operationId = buildService.buildAllAsync();
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("operationId", operationId);
            data.put("statusApi", "/api/build/all/status");
            data.put("recordApi", "/api/build/records/" + operationId);
            return Result.success("全量拉取+编译已启动", data);
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        }
    }

    // ===================================================
    // 单批次编译
    // ===================================================

    /**
     * 触发单个批次编译（异步，立即返回）
     * POST /api/build/batch/{batchName}
     *
     * <p>batchName 需与 application.yml build.batches[].name 完全一致，
     * 可先调用 GET /api/build/batches 查询所有批次名称。
     * <p>进度通过 GET /api/build/all/progress（SSE）或 GET /api/build/all/status 查看。
     *
     * @param batchName 批次名称，如"第一批-基础字典"
     */
    @PostMapping("/batch/{batchName}")
    public Result<String> buildBatch(@PathVariable String batchName) {
        log.info("触发批次编译（异步）：{}", batchName);
        // 先校验批次是否存在
        boolean exists = buildService.listBatches().stream()
                .anyMatch(b -> batchName.equals(b.get("name")));
        if (!exists) {
            return Result.error("批次不存在：" + batchName + "，请先调用 GET /api/build/batches 查询可用批次");
        }
        buildService.buildBatchAsync(batchName);
        return Result.success("批次 [" + batchName + "] 编译已启动，请通过 GET /api/build/all/progress（SSE）或 GET /api/build/all/status 查看进度");
    }

    // ===================================================
    // 编译进度查询
    // ===================================================

    /**
     * SSE 实时订阅编译进度（全量和单批次均推送到此处）
     * GET /api/build/all/progress
     *
     * <p>连接后自动回放当前任务的历史日志，再实时推送后续内容。
     * <p>前端示例：
     * <pre>
     * const es = new EventSource('/api/build/all/progress');
     * es.addEventListener('log',  e => appendLine(e.data));
     * es.addEventListener('done', e => { appendLine(e.data); es.close(); });
     * </pre>
     */
    @GetMapping(value = "/all/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter allProgress() {
        return progressTracker.subscribe();
    }

    /**
     * JSON 轮询当前任务状态快照（全量拉取+编译 / 单批次编译通用）
     * GET /api/build/all/status
     *
     * <p>返回字段：
     * <ul>
     *   <li>status           - IDLE / RUNNING / SUCCESS / FAILED</li>
     *   <li>phase            - IDLE / PULLING / BUILDING / FINISHED</li>
     *   <li>mode             - ALL（全量）/ BATCH（单批次）</li>
     *   <li>operationId      - 当前或最近一次全量任务编号</li>
     *   <li>taskLabel        - 任务标签（"全量拉取+编译" 或 批次名）</li>
     *   <li>totalBatches     - 本次任务总批次数</li>
     *   <li>completedBatches - 已完成批次数</li>
     *   <li>totalPullProjects - 本次需拉取工程总数</li>
     *   <li>completedPullProjects - 已完成拉取工程数</li>
     *   <li>currentBatch     - 当前批次名</li>
     *   <li>currentProject   - 当前编译工程名</li>
     *   <li>activeBatches    - 当前活跃批次列表</li>
     *   <li>activeProjects   - 当前活跃工程列表</li>
     *   <li>costSeconds      - 已耗时（秒）</li>
     *   <li>finishMessage    - 结束提示（任务结束后有值）</li>
     *   <li>recentLogs       - 最近 50 行进度日志</li>
     * </ul>
     */
    @GetMapping("/all/status")
    public Result<Map<String, Object>> allStatus() {
        Map<String, Object> snapshot = progressTracker.snapshot();
        String operationId = snapshot.get("operationId") == null ? "" : String.valueOf(snapshot.get("operationId"));

        String asyncBuildStatus = "";
        try {
            if (operationId != null && !operationId.isBlank()) {
                Map<String, Object> detail = buildOperationRecordService.queryOperationDetail(operationId);
                if (detail != null && detail.get("task") instanceof BuildOperationRecord taskRecord) {
                    asyncBuildStatus = taskRecord.getAsyncBuildStatus();
                }
            }

            if (asyncBuildStatus == null || asyncBuildStatus.isBlank()) {
                List<BuildOperationRecord> recentTasks = buildOperationRecordService.queryRecentTasks(1);
                if (recentTasks != null && !recentTasks.isEmpty()) {
                    asyncBuildStatus = recentTasks.get(0).getAsyncBuildStatus();
                }
            }
        } catch (Exception e) {
            log.warn("查询 asyncBuildStatus 失败，降级返回空状态，operationId={}, reason={}",
                    operationId, e.getMessage());
        }

        snapshot.put("asyncBuildStatus", asyncBuildStatus == null ? "" : asyncBuildStatus);
        return Result.success(snapshot);
    }

    /**
     * 查询当前保留的最近一次全量拉取+编译任务
     * GET /api/build/records/recent
     */
    @GetMapping("/records/recent")
    public Result<List<BuildOperationRecord>> recentOperations(
            @RequestParam(defaultValue = "10") Integer limit) {
        try {
            return Result.success(buildOperationRecordService.queryRecentTasks(limit == null ? 10 : limit));
        } catch (Exception e) {
            log.warn("查询最近任务记录失败，降级返回空列表，reason={}", e.getMessage());
            return Result.success(List.of());
        }
    }

    /**
     * 查询一次全量拉取+编译任务的完整明细
     * GET /api/build/records/{operationId}
     */
    @GetMapping("/records/{operationId}")
    public Result<Map<String, Object>> operationDetail(@PathVariable String operationId) {
        Map<String, Object> detail = buildOperationRecordService.queryOperationDetail(operationId);
        if (detail == null) {
            return Result.error("未找到任务记录：" + operationId);
        }
        return Result.success(detail);
    }

    /**
     * 更新 Neo4j 异步构建状态
     * POST /api/build/async-build/status
     *
     * <p>用于 8123 服务回调当前或指定 operationId 的异步构建状态。
     * <p>状态示例：异步构建中 / 成功 / 失败
     */
    @PostMapping("/async-build/status")
    public Result<String> updateAsyncBuildStatus(@RequestBody(required = false) AsyncBuildStatusUpdateRequest request) {
        if (request == null || request.getStatus() == null || request.getStatus().trim().isEmpty()) {
            return Result.error("status 不能为空");
        }
        buildOperationRecordService.updateAsyncBuildStatus(request.getOperationId(), request.getStatus().trim());
        return Result.success("异步构建状态已更新");
    }

    // ===================================================
    // 单工程编译
    // ===================================================

    /**
     * 触发单个工程编译（同步）
     * POST /api/build/project/{name}
     *
     * @param name 工程目录名，如 ccbs-comm-api
     */
    @PostMapping("/project/{name}")
    public Result<Map<String, Object>> buildOne(@PathVariable String name) {
        log.info("触发单工程编译：{}", name);
        Map<String, Object> result = buildService.buildOne(name);
        return Result.success(result);
    }

    public static class AsyncBuildStatusUpdateRequest {
        private String operationId;
        private String status;

        public String getOperationId() {
            return operationId;
        }

        public void setOperationId(String operationId) {
            this.operationId = operationId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
