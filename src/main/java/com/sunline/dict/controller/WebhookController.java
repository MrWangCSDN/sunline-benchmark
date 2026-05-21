package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.CallRelationScanService;
import com.sunline.dict.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Webhook接收控制器
 * 接收Git仓库的Push事件，解析.flowtrans.xml文件并落库
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {
    
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    
    @Autowired
    private WebhookService webhookService;

    @Autowired
    private CallRelationScanService callRelationScanService;
    
    /**
     * 接收GitHub Webhook
     * URL: POST /api/webhook/github
     */
    @PostMapping("/github")
    public Result<Map<String, Object>> handleGitHubWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event) {
        
        try {
            if (callRelationScanService.isScanning()) {
                log.info("全量扫描进行中，GitHub Webhook 暂停处理");
                return Result.success(Map.of("message", "全量扫描进行中，Webhook 暂停处理"));
            }

            log.info("收到GitHub Webhook，事件类型: {}", event);
            
            // 只处理push事件
            if (!"push".equals(event)) {
                log.info("非push事件，忽略");
                return Result.success(Map.of("message", "非push事件，已忽略"));
            }
            
            Map<String, Object> result = webhookService.handlePushEvent(payload);
            
            if ((boolean) result.get("success")) {
                return Result.success(result);
            } else {
                return Result.success(result); // 即使没有处理，也返回成功（避免Git重试）
            }
            
        } catch (Exception e) {
            log.error("处理GitHub Webhook失败", e);
            // 返回200，避免Git重试
            return Result.error("处理失败：" + e.getMessage());
        }
    }
    
    /**
     * 接收GitLab Webhook
     * URL: POST /api/webhook/gitlab
     */
    @PostMapping("/gitlab")
    public Result<Map<String, Object>> handleGitLabWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Gitlab-Event", required = false) String event) {
        
        try {
            if (callRelationScanService.isScanning()) {
                log.info("全量扫描进行中，GitLab Webhook 暂停处理");
                return Result.success(Map.of("message", "全量扫描进行中，Webhook 暂停处理"));
            }

            log.info("收到GitLab Webhook，事件类型: {}", event);
            
            // 只处理Push Hook事件
            if (!"Push Hook".equals(event) && !"push".equals(event)) {
                log.info("非push事件，忽略");
                return Result.success(Map.of("message", "非push事件，已忽略"));
            }
            
            Map<String, Object> result = webhookService.handleGitLabPushEvent(payload);

            // 异步触发调用关系增量扫描（从 payload 的 commits 中提取变更文件）
            try {
                List<String> changedFiles = extractChangedFiles(payload);
                if (!changedFiles.isEmpty()) {
                    asyncIncrementalScan(changedFiles);
                }
            } catch (Exception relEx) {
                log.warn("调用关系增量扫描触发失败（不影响主流程）：{}", relEx.getMessage());
            }
            
            if ((boolean) result.get("success")) {
                return Result.success(result);
            } else {
                return Result.success(result); // 即使没有处理，也返回成功（避免Git重试）
            }
            
        } catch (Exception e) {
            log.error("处理GitLab Webhook失败", e);
            // 返回200，避免Git重试
            return Result.error("处理失败：" + e.getMessage());
        }
    }
    
    /**
     * 通用Webhook接收端点（自动识别GitHub/GitLab）
     * URL: POST /api/webhook/git
     */
    @PostMapping("/git")
    public Result<Map<String, Object>> handleGitWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-GitHub-Event", required = false) String githubEvent,
            @RequestHeader(value = "X-Gitlab-Event", required = false) String gitlabEvent) {
        
        try {
            log.info("收到Git Webhook");
            
            // 判断是GitHub还是GitLab
            if (githubEvent != null) {
                log.info("识别为GitHub事件: {}", githubEvent);
                return handleGitHubWebhook(payload, githubEvent);
            } else if (gitlabEvent != null) {
                log.info("识别为GitLab事件: {}", gitlabEvent);
                return handleGitLabWebhook(payload, gitlabEvent);
            } else {
                log.warn("无法识别的Webhook来源");
                return Result.success(Map.of("message", "无法识别的Webhook来源"));
            }
            
        } catch (Exception e) {
            log.error("处理Git Webhook失败", e);
            return Result.error("处理失败：" + e.getMessage());
        }
    }
    
    /**
     * Webhook健康检查
     * URL: GET /api/webhook/health
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Webhook服务运行正常");
    }

    /**
     * 从 GitLab Push Webhook payload 的 commits 中提取所有变更文件路径。
     * GitLab payload 结构：commits[].{added[], modified[], removed[]}
     */
    @SuppressWarnings("unchecked")
    private List<String> extractChangedFiles(Map<String, Object> payload) {
        List<String> files = new ArrayList<>();
        Object commitsObj = payload.get("commits");
        if (!(commitsObj instanceof List)) return files;

        List<Map<String, Object>> commits = (List<Map<String, Object>>) commitsObj;
        for (Map<String, Object> commit : commits) {
            addFiles(files, commit.get("added"));
            addFiles(files, commit.get("modified"));
            addFiles(files, commit.get("removed"));
        }

        // 只保留 Java 文件
        files.removeIf(f -> !f.endsWith(".java"));

        if (!files.isEmpty()) {
            log.info("从 Webhook diff 提取到 {} 个 Java 变更文件", files.size());
        }
        return files;
    }

    @SuppressWarnings("unchecked")
    private void addFiles(List<String> target, Object filesObj) {
        if (filesObj instanceof List) {
            for (Object f : (List<Object>) filesObj) {
                if (f instanceof String && !target.contains(f)) {
                    target.add((String) f);
                }
            }
        }
    }

    /**
     * 异步执行调用关系增量扫描，不阻塞 Webhook 返回
     */
    @Async
    public void asyncIncrementalScan(List<String> changedFiles) {
        try {
            log.info("开始异步调用关系增量扫描，变更文件数：{}", changedFiles.size());
            Map<String, Object> result = callRelationScanService.incrementalScan(changedFiles);
            log.info("调用关系增量扫描完成：重新扫描 {} 个 Impl 文件，新增 {} 条边，耗时 {}ms",
                    result.get("rescanFiles"), result.get("newEdges"), result.get("costMs"));
        } catch (Exception e) {
            log.error("调用关系增量扫描异常", e);
        }
    }
}
