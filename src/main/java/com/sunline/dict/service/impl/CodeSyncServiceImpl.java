package com.sunline.dict.service.impl;

import com.sunline.dict.service.CodeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 代码同步服务实现
 *
 * <p>工作流程：
 * <ol>
 *   <li>接收 GitLab Push 事件</li>
 *   <li>校验是否为 master 分支、project_id 是否在白名单</li>
 *   <li>目录存在且是 git 仓库 → git fetch + git reset --hard origin/master</li>
 *   <li>目录不存在 → git clone -b master --single-branch</li>
 * </ol>
 *
 * <p>本地存放路径：{code-sync.base-path}/{project_name}/
 */
@Service
public class CodeSyncServiceImpl implements CodeSyncService {

    private static final Logger log = LoggerFactory.getLogger(CodeSyncServiceImpl.class);

    @Value("${code-sync.base-path:/home/cbs/code}")
    private String basePath;

    @Value("${code-sync.allowed-project-ids:}")
    private String allowedProjectIds;

    @Value("${gitlab.access-token:}")
    private String accessToken;

    @Value("${code-sync.git-timeout-seconds:300}")
    private int gitTimeoutSeconds;

    /** 加载白名单 project_id 集合（懒初始化） */
    private volatile Set<Integer> allowedProjectIdSet;

    private Set<Integer> getAllowedProjectIds() {
        if (allowedProjectIdSet == null) {
            synchronized (this) {
                if (allowedProjectIdSet == null) {
                    allowedProjectIdSet = new HashSet<>();
                    if (allowedProjectIds != null && !allowedProjectIds.trim().isEmpty()) {
                        for (String id : allowedProjectIds.split(",")) {
                            try {
                                allowedProjectIdSet.add(Integer.parseInt(id.trim()));
                            } catch (NumberFormatException e) {
                                log.warn("无效的 project_id 配置：{}", id);
                            }
                        }
                    }
                    log.info("代码同步白名单 project_id：{}", allowedProjectIdSet);
                }
            }
        }
        return allowedProjectIdSet;
    }

    @Override
    public Map<String, Object> syncCode(Map<String, Object> payload) throws Exception {
        Map<String, Object> result = new HashMap<>();

        // 1. 提取 ref（分支）
        String ref = (String) payload.get("ref");
        if (ref == null || !ref.endsWith("/master") && !ref.equals("refs/heads/master")) {
            log.info("代码同步：非 master 分支，跳过。ref={}", ref);
            result.put("success", false);
            result.put("message", "非 master 分支，跳过");
            return result;
        }

        // 2. 提取 project 信息
        @SuppressWarnings("unchecked")
        Map<String, Object> project = (Map<String, Object>) payload.get("project");
        if (project == null) {
            result.put("success", false);
            result.put("message", "payload 中缺少 project 信息");
            return result;
        }

        Integer projectId = project.get("id") != null ? ((Number) project.get("id")).intValue() : null;
        String projectName = (String) project.get("name");
        String httpUrl = (String) project.get("http_url");
        if (httpUrl == null) httpUrl = (String) project.get("git_http_url");

        log.info("代码同步：projectId={}, projectName={}, httpUrl={}", projectId, projectName, httpUrl);

        // 3. 校验是否在白名单
        if (projectId == null || !getAllowedProjectIds().contains(projectId)) {
            log.info("代码同步：projectId={} 不在白名单，跳过", projectId);
            result.put("success", false);
            result.put("message", "project_id 不在白名单");
            return result;
        }

        if (httpUrl == null || httpUrl.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "无法获取仓库地址");
            return result;
        }

        // 4. 注入 token 到 URL：http://oauth2:{token}@gitlab.xxx.com/xxx/xxx.git
        String authenticatedUrl = injectToken(httpUrl, accessToken);

        // 5. 本地路径：basePath/projectName
        String localPath = basePath + File.separator + projectName;
        File localDir = new File(localPath);

        log.info("代码同步开始：projectId={}, projectName={}, localPath={}", projectId, projectName, localPath);

        // 6. 确保 basePath 存在
        File baseDir = new File(basePath);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
            log.info("创建 basePath：{}", basePath);
        }

        String syncOutput;
        if (localDir.exists() && new File(localDir, ".git").exists()) {
            // 已存在：强制同步到 origin/master
            syncOutput = gitPull(localDir, authenticatedUrl);
        } else {
            // 不存在：克隆
            syncOutput = gitClone(authenticatedUrl, localPath);
        }

        log.info("代码同步完成：projectName={}\n{}", projectName, syncOutput);

        result.put("success", true);
        result.put("message", "同步成功");
        result.put("projectId", projectId);
        result.put("projectName", projectName);
        result.put("localPath", localPath);
        result.put("output", syncOutput);
        return result;
    }

    // ─────────────── git 操作 ───────────────

    /**
     * 已有仓库：fetch 最新 + 强制重置到 origin/master
     * 等价于：git fetch origin && git checkout master && git reset --hard origin/master
     */
    private String gitPull(File repoDir, String authenticatedUrl) throws Exception {
        log.info("git pull（强制重置）：{}", repoDir.getAbsolutePath());
        StringBuilder sb = new StringBuilder();

        // 先设置 remote url（确保 token 最新）
        runGit(repoDir, sb, "git", "remote", "set-url", "origin", authenticatedUrl);

        // 设置 git 用户信息（避免某些环境缺失）
        runGit(repoDir, sb, "git", "config", "user.email", "benchmark@sync.local");
        runGit(repoDir, sb, "git", "config", "user.name", "BenchmarkSync");

        // fetch
        runGit(repoDir, sb, "git", "fetch", "origin", "master");

        // 强制重置到 origin/master（覆盖本地任何修改）
        runGit(repoDir, sb, "git", "checkout", "master");
        runGit(repoDir, sb, "git", "reset", "--hard", "origin/master");

        // 清理未跟踪文件
        runGit(repoDir, sb, "git", "clean", "-fd");

        return sb.toString();
    }

    /**
     * 新仓库：克隆 master 分支（单分支，节省空间）
     */
    private String gitClone(String authenticatedUrl, String localPath) throws Exception {
        log.info("git clone：{} → {}", localPath);
        StringBuilder sb = new StringBuilder();
        runGit(new File(System.getProperty("user.home")), sb,
                "git", "clone",
                "-b", "master",
                "--single-branch",
                authenticatedUrl,
                localPath);
        return sb.toString();
    }

    /**
     * 执行 git 命令
     */
    private void runGit(File workDir, StringBuilder outputBuffer, String... cmd) throws Exception {
        log.debug("执行命令：{} (cwd={})", Arrays.toString(cmd), workDir.getAbsolutePath());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0"); // 禁止交互式密码

        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 脱敏：移除 URL 中的 token
                String sanitized = line.replaceAll("(https?://)([^@]+@)", "$1***@");
                output.append(sanitized).append("\n");
            }
        }

        boolean finished = process.waitFor(gitTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("git 命令超时（" + gitTimeoutSeconds + "s）：" + Arrays.toString(cmd));
        }

        int exitCode = process.exitValue();
        outputBuffer.append("[").append(cmd[1]).append("] exit=").append(exitCode).append("\n")
                    .append(output);

        if (exitCode != 0) {
            throw new RuntimeException("git 命令失败（exit=" + exitCode + "）：" + output);
        }
    }

    /**
     * 在 HTTP URL 中注入 token：
     * https://gitlab.com/xxx.git → https://oauth2:{token}@gitlab.com/xxx.git
     */
    private String injectToken(String httpUrl, String token) {
        if (token == null || token.trim().isEmpty()) return httpUrl;
        if (httpUrl.contains("@")) return httpUrl; // 已包含认证信息

        // https://host/... → https://oauth2:token@host/...
        int schemeEnd = httpUrl.indexOf("://");
        if (schemeEnd < 0) return httpUrl;
        String scheme = httpUrl.substring(0, schemeEnd + 3); // "https://"
        String rest = httpUrl.substring(schemeEnd + 3);
        return scheme + "oauth2:" + token + "@" + rest;
    }
}
