package com.sunline.dict.service.impl;

import com.sunline.dict.config.BuildConfig;
import com.sunline.dict.service.CodeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    @Value("${git.gitlab.url:http://gitlab.spdb.com}")
    private String gitlabUrl;

    @Autowired
    private BuildConfig buildConfig;

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

    // ─────────────── 编译前兜底拉取（不依赖 webhook payload）───────────────

    @Override
    public Map<String, Object> pullProject(String projectName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", projectName);

        String localPath = basePath + File.separator + projectName;
        File localDir = new File(localPath);
        result.put("localPath", localPath);

        if (!localDir.exists()) {
            result.put("status", "SKIP");
            result.put("message", "目录不存在，无法拉取：" + localPath);
            result.put("errorType", "MISSING_DIR");
            result.put("errorMessage", "目录不存在：" + localPath);
            log.warn("pullProject 跳过（目录不存在）：{}", localPath);
            return result;
        }

        if (!new File(localDir, ".git").exists()) {
            result.put("status", "SKIP");
            result.put("message", "目录不是 git 仓库，无法拉取：" + localPath);
            result.put("errorType", "INVALID_REPO");
            result.put("errorMessage", "目录不是 git 仓库：" + localPath);
            log.warn("pullProject 跳过（非 git 仓库）：{}", localPath);
            return result;
        }

        try {
            log.info("pullProject 开始：{}", localPath);
            String output = gitPullByDir(localDir);
            result.put("status", "SUCCESS");
            result.put("message", "拉取成功");
            result.put("output", output);
            log.info("pullProject 完成：{}", projectName);
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("message", "拉取失败：" + e.getMessage());
            result.put("errorType", e.getClass().getName());
            result.put("errorMessage", e.getMessage());
            result.put("errorStack", stackTrace(e));
            log.error("pullProject 失败：{}", projectName, e);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> pullProjects(List<String> projectNames) {
        List<Map<String, Object>> results = new ArrayList<>();
        log.info("批量拉取开始，共 {} 个工程：{}", projectNames.size(), projectNames);
        for (String name : projectNames) {
            results.add(pullProject(name));
        }
        long success = results.stream().filter(r -> "SUCCESS".equals(r.get("status"))).count();
        long skip    = results.stream().filter(r -> "SKIP".equals(r.get("status"))).count();
        long failed  = results.stream().filter(r -> "FAILED".equals(r.get("status"))).count();
        log.info("批量拉取完成：成功={} 跳过={} 失败={}", success, skip, failed);
        return results;
    }

    /**
     * 对已有 git 仓库做强制拉取（不更改 remote URL，使用已有 origin 配置）
     */
    private String gitPullByDir(File repoDir) throws Exception {
        StringBuilder sb = new StringBuilder();
        runGit(repoDir, sb, "git", "fetch", "origin", "master");
        runGit(repoDir, sb, "git", "checkout", "master");
        runGit(repoDir, sb, "git", "reset", "--hard", "origin/master");
        runGit(repoDir, sb, "git", "clean", "-fd");
        return sb.toString();
    }

    // ─────────────── 全量 clone ───────────────

    @Override
    public List<Map<String, Object>> cloneAllBatchProjects() {
        // 从 build.batches 收集所有工程名（去重，保持顺序）
        List<String> allProjects = buildConfig.getBatches().stream()
                .flatMap(b -> b.getProjects().stream())
                .distinct()
                .toList();

        log.info("==================== 全量 clone 开始，共 {} 个工程，多线程并行执行 ====================", allProjects.size());
        log.info("工程列表：{}", allProjects);

        // 确保 basePath 存在
        File baseDir = new File(basePath);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
            log.info("创建 basePath：{}", basePath);
        }

        // 线程数取工程数和 CPU 核数的较小值，最多 8 个线程
        int threadCount = Math.min(allProjects.size(), Math.min(Runtime.getRuntime().availableProcessors(), 8));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        log.info("线程池大小：{}", threadCount);

        List<Map<String, Object>> results = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new ArrayList<>();

        for (String projectName : allProjects) {
            futures.add(executor.submit(() -> {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("project", projectName);
                try {
                    String localPath = basePath + File.separator + projectName;
                    File localDir = new File(localPath);

                    if (localDir.exists() && new File(localDir, ".git").exists()) {
                        log.info("[{}] 已存在，执行强制 pull", projectName);
                        String httpUrl = resolveProjectHttpUrl(projectName);
                        String authenticatedUrl = injectToken(httpUrl, accessToken);
                        String output = gitPull(localDir, authenticatedUrl);
                        result.put("status", "PULLED");
                        result.put("message", "已存在，强制 pull 成功");
                        result.put("output", output);
                    } else {
                        log.info("[{}] 不存在，执行 clone", projectName);
                        String httpUrl = resolveProjectHttpUrl(projectName);
                        String authenticatedUrl = injectToken(httpUrl, accessToken);
                        String output = gitClone(authenticatedUrl, localPath);
                        result.put("status", "CLONED");
                        result.put("message", "clone 成功");
                        result.put("output", output);
                    }
                } catch (Exception e) {
                    log.error("[{}] 处理失败：{}", projectName, e.getMessage(), e);
                    result.put("status", "FAILED");
                    result.put("message", e.getMessage());
                }
                results.add(result);
            }));
        }

        // 等待所有任务完成
        executor.shutdown();
        try {
            // 超时时间 = 单工程超时 * 工程数（最坏情况串行），留足余量
            boolean done = executor.awaitTermination((long) gitTimeoutSeconds * allProjects.size(), TimeUnit.SECONDS);
            if (!done) {
                log.warn("全量 clone 等待超时，部分工程可能未完成");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            log.error("全量 clone 被中断", e);
        }

        long success = results.stream().filter(r -> !"FAILED".equals(r.get("status"))).count();
        long failed  = results.stream().filter(r -> "FAILED".equals(r.get("status"))).count();
        log.info("==================== 全量 clone 完成：成功={} 失败={} ====================", success, failed);

        // 按原始工程顺序排序结果
        results.sort((a, b) -> {
            int ia = allProjects.indexOf(a.get("project"));
            int ib = allProjects.indexOf(b.get("project"));
            return Integer.compare(ia, ib);
        });
        return results;
    }

    /**
     * 通过 GitLab API 按工程名搜索，返回 http_url_to_repo。
     * 搜索结果可能有多个，取 name 完全匹配的第一个。
     */
    private String resolveProjectHttpUrl(String projectName) throws Exception {
        String apiUrl = gitlabUrl + "/api/v4/projects?search="
                + java.net.URLEncoder.encode(projectName, StandardCharsets.UTF_8)
                + "&per_page=20&simple=false";

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("PRIVATE-TOKEN", accessToken);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new RuntimeException("GitLab API 返回 " + code + "，无法查询工程：" + projectName);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }

        // 简单 JSON 解析：找 name 完全匹配的项目的 http_url_to_repo
        String json = sb.toString();
        // 按 "name":"projectName" 定位，再找 http_url_to_repo
        String namePattern = "\"name\":\"" + projectName + "\"";
        int idx = json.indexOf(namePattern);
        if (idx < 0) {
            // 尝试 path 匹配
            namePattern = "\"path\":\"" + projectName + "\"";
            idx = json.indexOf(namePattern);
        }
        if (idx < 0) {
            throw new RuntimeException("GitLab API 未找到工程：" + projectName + "（搜索结果中无精确匹配）");
        }

        // 从该位置向后找 http_url_to_repo
        String snippet = json.substring(idx);
        String urlKey = "\"http_url_to_repo\":\"";
        int urlIdx = snippet.indexOf(urlKey);
        if (urlIdx < 0) {
            throw new RuntimeException("GitLab API 响应中未找到 http_url_to_repo，工程：" + projectName);
        }
        int start = urlIdx + urlKey.length();
        int end = snippet.indexOf("\"", start);
        String httpUrl = snippet.substring(start, end);
        log.info("工程 {} 解析到 URL：{}", projectName, httpUrl.replaceAll("(https?://)([^@]+@)", "$1***@"));
        return httpUrl;
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

    private String stackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}
