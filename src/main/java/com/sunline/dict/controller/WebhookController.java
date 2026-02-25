package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    
    /**
     * 接收GitHub Webhook
     * URL: POST /api/webhook/github
     */
    @PostMapping("/github")
    public Result<Map<String, Object>> handleGitHubWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event) {
        
        try {
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
            log.info("收到GitLab Webhook，事件类型: {}", event);
            
            // 只处理Push Hook事件
            if (!"Push Hook".equals(event) && !"push".equals(event)) {
                log.info("非push事件，忽略");
                return Result.success(Map.of("message", "非push事件，已忽略"));
            }
            
            Map<String, Object> result = webhookService.handleGitLabPushEvent(payload);
            
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
}
