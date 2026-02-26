package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.FlowStep;
import com.sunline.dict.entity.Flowtran;
import com.sunline.dict.mapper.FlowStepMapper;
import com.sunline.dict.mapper.FlowtranMapper;
import com.sunline.dict.service.ComplexXmlParseService;
import com.sunline.dict.service.DictXmlParseService;
import com.sunline.dict.service.FlowXmlParseService;
import com.sunline.dict.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * Webhook服务实现
 */
@Service
public class WebhookServiceImpl implements WebhookService {
    
    private static final Logger log = LoggerFactory.getLogger(WebhookServiceImpl.class);
    
    @Autowired
    private FlowXmlParseService flowXmlParseService;

    @Autowired
    private ComplexXmlParseService complexXmlParseService;

    @Autowired
    private DictXmlParseService dictXmlParseService;
    
    @Autowired
    private FlowtranMapper flowtranMapper;
    
    @Autowired
    private FlowStepMapper flowStepMapper;
    
    @Value("${gitlab.access-token:}")
    private String gitlabAccessToken;
    
    @Value("${gitlab.validate-uat-from-sit:false}")
    private boolean validateUatFromSit;
    
    @Value("${gitlab.bypass-keywords:}")
    private String bypassKeywords;
    
    @Value("${github.access-token:}")
    private String githubAccessToken;
    
    @Override
    public Map<String, Object> handlePushEvent(Map<String, Object> payload) throws Exception {
        log.info("收到Git Push事件");
        
        // 获取ref（分支）
        String ref = (String) payload.get("ref");
        if (ref == null || !ref.endsWith("/master")) {
            log.info("非master分支push事件，忽略。ref: {}", ref);
            return createResult(false, "非master分支push事件", 0, 0);
        }
        
        log.info("master分支push事件，开始处理");
        
        // 获取commits列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> commits = (List<Map<String, Object>>) payload.get("commits");
        if (commits == null || commits.isEmpty()) {
            log.info("没有commits信息");
            return createResult(false, "没有commits信息", 0, 0);
        }
        
        // 收集新增/修改 与 删除 的文件（flowtrans.xml / c_schema.xml / d_schema.xml 分开存）
        Set<String> flowtransFiles = new HashSet<>();
        Set<String> removedFlowtransFiles = new HashSet<>();
        Set<String> schemaFiles = new HashSet<>();
        Set<String> removedSchemaFiles = new HashSet<>();
        Set<String> dictFiles = new HashSet<>();
        Set<String> removedDictFiles = new HashSet<>();
        for (Map<String, Object> commit : commits) {
            collectFlowtransFiles(commit, flowtransFiles, removedFlowtransFiles);
            collectSchemaFiles(commit, schemaFiles, removedSchemaFiles);
            collectDictFiles(commit, dictFiles, removedDictFiles);
        }
        
        log.info("找到 {} 个新增/修改的.flowtrans.xml，{} 个已删除；{} 个新增/修改的.c_schema.xml，{} 个已删除；{} 个新增/修改的.d_schema.xml，{} 个已删除",
                flowtransFiles.size(), removedFlowtransFiles.size(), schemaFiles.size(), removedSchemaFiles.size(),
                dictFiles.size(), removedDictFiles.size());
        
        if (flowtransFiles.isEmpty() && removedFlowtransFiles.isEmpty()
                && schemaFiles.isEmpty() && removedSchemaFiles.isEmpty()
                && dictFiles.isEmpty() && removedDictFiles.isEmpty()) {
            return createResult(false, "没有找到需要处理的文件变更", 0, 0);
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> repository = (Map<String, Object>) payload.get("repository");
        String projectName = repository != null ? (String) repository.get("name") : "unknown";
        String gitUrl = repository != null ? (String) repository.get("clone_url") : null;
        
        int totalFlowtran = 0;
        int totalFlowStep = 0;
        
        // 处理 flowtrans.xml 删除
        for (String filePath : removedFlowtransFiles) {
            String sourceInfo = projectName + ":" + filePath;
            int[] deleted = deleteFlowtranBySourceInfo(sourceInfo);
            totalFlowtran -= deleted[0];
            totalFlowStep -= deleted[1];
        }
        // 处理 c_schema.xml 删除
        for (String filePath : removedSchemaFiles) {
            String sourceInfo = projectName + ":" + filePath;
            complexXmlParseService.deleteBySourceInfo(sourceInfo);
        }
        // 处理 d_schema.xml 删除
        for (String filePath : removedDictFiles) {
            String sourceInfo = projectName + ":" + filePath;
            dictXmlParseService.deleteBySourceInfo(sourceInfo);
        }
        
        // 处理 flowtrans.xml 新增/修改
        for (String filePath : flowtransFiles) {
            try {
                String fileContent = downloadFileFromGit(gitUrl, filePath, ref);
                if (fileContent != null) {
                    String sourceInfo = projectName + ":" + filePath;
                    Map<String, Object> parseResult = flowXmlParseService.parseAndSave(fileContent, sourceInfo);
                    totalFlowtran += (int) parseResult.getOrDefault("flowtranCount", 0);
                    totalFlowStep += (int) parseResult.getOrDefault("flowStepCount", 0);
                }
            } catch (Exception e) {
                log.error("处理 flowtrans.xml 失败: {}", filePath, e);
            }
        }
        // 处理 c_schema.xml 新增/修改
        for (String filePath : schemaFiles) {
            try {
                String fileContent = downloadFileFromGit(gitUrl, filePath, ref);
                if (fileContent != null) {
                    String sourceInfo = projectName + ":" + filePath;
                    Map<String, Object> parseResult = complexXmlParseService.parseAndSave(fileContent, sourceInfo);
                    log.info("c_schema.xml 解析完成：complex={}, detail={}, 来源={}",
                            parseResult.get("complexCount"), parseResult.get("complexDetailCount"), filePath);
                }
            } catch (Exception e) {
                log.error("处理 c_schema.xml 失败: {}", filePath, e);
            }
        }
        // 处理 d_schema.xml 新增/修改
        for (String filePath : dictFiles) {
            try {
                String fileContent = downloadFileFromGit(gitUrl, filePath, ref);
                if (fileContent != null) {
                    String sourceInfo = projectName + ":" + filePath;
                    Map<String, Object> parseResult = dictXmlParseService.parseAndSave(fileContent, sourceInfo);
                    log.info("d_schema.xml 解析完成：dict={}, detail={}, 来源={}",
                            parseResult.get("dictCount"), parseResult.get("dictDetailCount"), filePath);
                }
            } catch (Exception e) {
                log.error("处理 d_schema.xml 失败: {}", filePath, e);
            }
        }
        
        log.info("Webhook处理完成，交易数：{}, 步骤数：{}", totalFlowtran, totalFlowStep);
        
        return createResult(true, "处理成功", totalFlowtran, totalFlowStep);
    }
    
    @Override
    public Map<String, Object> handleGitLabPushEvent(Map<String, Object> payload) throws Exception {
        log.info("收到GitLab Push事件");
        
        // 获取ref（分支）与项目信息
        String ref = (String) payload.get("ref");
        @SuppressWarnings("unchecked")
        Map<String, Object> project = (Map<String, Object>) payload.get("project");
        Integer projectId = project != null ? (Integer) project.get("id") : null;
        String gitlabUrl = project != null ? (String) project.get("web_url") : null;
        String pathWithNamespace = project != null ? (String) project.get("path_with_namespace") : null;
        
        // UAT 分支校验：检查 commit 是否在 sit 分支存在
        if (ref != null && ref.equals("refs/heads/uat") && validateUatFromSit) {
            log.info("检测到 uat 分支 push，开始校验 commit 是否在 sit 分支存在");
            String validationError = validateCommitsInSit(payload, gitlabUrl, projectId, pathWithNamespace);
            if (validationError != null) {
                log.error("UAT 分支校验失败: {}", validationError);
                return createResult(false, validationError, 0, 0);
            }
            log.info("UAT 分支校验通过");
        }
        
        // 只处理 master 分支的 flowtrans.xml（其它分支也允许 push，但不解析文件）
        if (ref == null || !ref.equals("refs/heads/master")) {
            log.info("非master分支push事件，不处理 flowtrans.xml。ref: {}", ref);
            return createResult(true, "非master分支，跳过 flowtrans 处理", 0, 0);
        }
        
        log.info("master分支push事件，开始处理 flowtrans.xml");
        
        // 获取commits列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> commits = (List<Map<String, Object>>) payload.get("commits");
        if (commits == null || commits.isEmpty()) {
            log.info("没有commits信息");
            return createResult(false, "没有commits信息", 0, 0);
        }
        
        // 收集新增/修改 与 删除 的文件（flowtrans.xml / c_schema.xml / d_schema.xml 分开存）
        Set<String> flowtransFiles = new HashSet<>();
        Set<String> removedFlowtransFiles = new HashSet<>();
        Set<String> schemaFiles = new HashSet<>();
        Set<String> removedSchemaFiles = new HashSet<>();
        Set<String> dictFiles = new HashSet<>();
        Set<String> removedDictFiles = new HashSet<>();
        for (Map<String, Object> commit : commits) {
            collectFlowtransFiles(commit, flowtransFiles, removedFlowtransFiles);
            collectSchemaFiles(commit, schemaFiles, removedSchemaFiles);
            collectDictFiles(commit, dictFiles, removedDictFiles);
        }
        
        log.info("找到 {} 个新增/修改的.flowtrans.xml，{} 个已删除；{} 个新增/修改的.c_schema.xml，{} 个已删除；{} 个新增/修改的.d_schema.xml，{} 个已删除",
                flowtransFiles.size(), removedFlowtransFiles.size(), schemaFiles.size(), removedSchemaFiles.size(),
                dictFiles.size(), removedDictFiles.size());
        
        if (flowtransFiles.isEmpty() && removedFlowtransFiles.isEmpty()
                && schemaFiles.isEmpty() && removedSchemaFiles.isEmpty()
                && dictFiles.isEmpty() && removedDictFiles.isEmpty()) {
            return createResult(false, "没有找到需要处理的文件变更", 0, 0);
        }
        
        String projectName = project != null ? (String) project.get("name") : "unknown";
        
        int totalFlowtran = 0;
        int totalFlowStep = 0;
        
        // 处理 flowtrans.xml 删除
        for (String filePath : removedFlowtransFiles) {
            String sourceInfo = projectName + ":master:" + filePath;
            int[] deleted = deleteFlowtranBySourceInfo(sourceInfo);
            totalFlowtran -= deleted[0];
            totalFlowStep -= deleted[1];
        }
        // 处理 c_schema.xml 删除
        for (String filePath : removedSchemaFiles) {
            String sourceInfo = projectName + ":master:" + filePath;
            complexXmlParseService.deleteBySourceInfo(sourceInfo);
        }
        // 处理 d_schema.xml 删除
        for (String filePath : removedDictFiles) {
            String sourceInfo = projectName + ":master:" + filePath;
            dictXmlParseService.deleteBySourceInfo(sourceInfo);
        }
        
        // 处理 flowtrans.xml 新增/修改
        for (String filePath : flowtransFiles) {
            try {
                String fileContent = downloadFileFromGitLab(gitlabUrl, projectId, pathWithNamespace, filePath, "master");
                if (fileContent != null) {
                    String sourceInfo = projectName + ":master:" + filePath;
                    Map<String, Object> parseResult = flowXmlParseService.parseAndSave(fileContent, sourceInfo);
                    totalFlowtran += (int) parseResult.getOrDefault("flowtranCount", 0);
                    totalFlowStep += (int) parseResult.getOrDefault("flowStepCount", 0);
                }
            } catch (Exception e) {
                log.error("处理 flowtrans.xml 失败: {}", filePath, e);
            }
        }
        // 处理 c_schema.xml 新增/修改
        for (String filePath : schemaFiles) {
            try {
                String fileContent = downloadFileFromGitLab(gitlabUrl, projectId, pathWithNamespace, filePath, "master");
                if (fileContent != null) {
                    String sourceInfo = projectName + ":master:" + filePath;
                    Map<String, Object> parseResult = complexXmlParseService.parseAndSave(fileContent, sourceInfo);
                    log.info("c_schema.xml 解析完成：complex={}, detail={}, 来源={}",
                            parseResult.get("complexCount"), parseResult.get("complexDetailCount"), filePath);
                }
            } catch (Exception e) {
                log.error("处理 c_schema.xml 失败: {}", filePath, e);
            }
        }
        // 处理 d_schema.xml 新增/修改
        for (String filePath : dictFiles) {
            try {
                String fileContent = downloadFileFromGitLab(gitlabUrl, projectId, pathWithNamespace, filePath, "master");
                if (fileContent != null) {
                    String sourceInfo = projectName + ":master:" + filePath;
                    Map<String, Object> parseResult = dictXmlParseService.parseAndSave(fileContent, sourceInfo);
                    log.info("d_schema.xml 解析完成：dict={}, detail={}, 来源={}",
                            parseResult.get("dictCount"), parseResult.get("dictDetailCount"), filePath);
                }
            } catch (Exception e) {
                log.error("处理 d_schema.xml 失败: {}", filePath, e);
            }
        }
        
        log.info("GitLab Webhook处理完成，交易数：{}, 步骤数：{}", totalFlowtran, totalFlowStep);
        
        return createResult(true, "处理成功", totalFlowtran, totalFlowStep);
    }
    
    /**
     * 从 commit 中收集新增/修改 与 删除 的 .flowtrans.xml 文件
     */
    private void collectFlowtransFiles(Map<String, Object> commit, Set<String> flowtransFiles, Set<String> removedFlowtransFiles) {
        @SuppressWarnings("unchecked")
        List<String> added = (List<String>) commit.get("added");
        @SuppressWarnings("unchecked")
        List<String> modified = (List<String>) commit.get("modified");
        @SuppressWarnings("unchecked")
        List<String> removed = (List<String>) commit.get("removed");
        
        if (added != null) {
            for (String file : added) {
                if (file.endsWith(".flowtrans.xml")) {
                    flowtransFiles.add(file);
                    log.info("发现新增文件: {}", file);
                }
            }
        }
        if (modified != null) {
            for (String file : modified) {
                if (file.endsWith(".flowtrans.xml")) {
                    flowtransFiles.add(file);
                    log.info("发现修改文件: {}", file);
                }
            }
        }
        if (removed != null) {
            for (String file : removed) {
                if (file.endsWith(".flowtrans.xml")) {
                    removedFlowtransFiles.add(file);
                    log.info("发现删除文件: {}", file);
                }
            }
        }
    }
    
    /**
     * 从 commit 中收集新增/修改 与 删除 的 .c_schema.xml 文件
     */
    private void collectSchemaFiles(Map<String, Object> commit, Set<String> schemaFiles, Set<String> removedSchemaFiles) {
        @SuppressWarnings("unchecked")
        List<String> added = (List<String>) commit.get("added");
        @SuppressWarnings("unchecked")
        List<String> modified = (List<String>) commit.get("modified");
        @SuppressWarnings("unchecked")
        List<String> removed = (List<String>) commit.get("removed");
        
        if (added != null) {
            for (String file : added) {
                if (file.endsWith(".c_schema.xml")) {
                    schemaFiles.add(file);
                    log.info("发现新增 c_schema.xml: {}", file);
                }
            }
        }
        if (modified != null) {
            for (String file : modified) {
                if (file.endsWith(".c_schema.xml")) {
                    schemaFiles.add(file);
                    log.info("发现修改 c_schema.xml: {}", file);
                }
            }
        }
        if (removed != null) {
            for (String file : removed) {
                if (file.endsWith(".c_schema.xml")) {
                    removedSchemaFiles.add(file);
                    log.info("发现删除 c_schema.xml: {}", file);
                }
            }
        }
    }
    
    /**
     * 从 commit 中收集新增/修改 与 删除 的 .d_schema.xml 文件
     */
    private void collectDictFiles(Map<String, Object> commit, Set<String> dictFiles, Set<String> removedDictFiles) {
        @SuppressWarnings("unchecked")
        List<String> added = (List<String>) commit.get("added");
        @SuppressWarnings("unchecked")
        List<String> modified = (List<String>) commit.get("modified");
        @SuppressWarnings("unchecked")
        List<String> removed = (List<String>) commit.get("removed");

        if (added != null) {
            for (String file : added) {
                if (file.endsWith(".d_schema.xml")) {
                    dictFiles.add(file);
                    log.info("发现新增 d_schema.xml: {}", file);
                }
            }
        }
        if (modified != null) {
            for (String file : modified) {
                if (file.endsWith(".d_schema.xml")) {
                    dictFiles.add(file);
                    log.info("发现修改 d_schema.xml: {}", file);
                }
            }
        }
        if (removed != null) {
            for (String file : removed) {
                if (file.endsWith(".d_schema.xml")) {
                    removedDictFiles.add(file);
                    log.info("发现删除 d_schema.xml: {}", file);
                }
            }
        }
    }

    /**
     * 按来源标识删除 flowtran 及关联的 flow_step（先删 flow_step 再删 flowtran）
     * @param sourceInfo 与 flowtran.from_jar 一致，如 projectName:master:filePath
     * @return [删除的 flowtran 数, 删除的 flow_step 数]
     */
    private int[] deleteFlowtranBySourceInfo(String sourceInfo) {
        QueryWrapper<Flowtran> qw = new QueryWrapper<>();
        qw.eq("from_jar", sourceInfo);
        List<Flowtran> list = flowtranMapper.selectList(qw);
        int flowtranDeleted = 0;
        int flowStepDeleted = 0;
        for (Flowtran ft : list) {
            String flowId = ft.getId();
            QueryWrapper<FlowStep> stepQw = new QueryWrapper<>();
            stepQw.eq("flow_id", flowId);
            flowStepDeleted += flowStepMapper.delete(stepQw);
            flowtranDeleted += flowtranMapper.deleteById(flowId);
            log.info("已删除来源 {} 的 flowtran id={} 及其 flow_step", sourceInfo, flowId);
        }
        if (list.isEmpty()) {
            log.debug("未找到来源 {} 的 flowtran 记录", sourceInfo);
        }
        return new int[] { flowtranDeleted, flowStepDeleted };
    }
    
    /**
     * 从GitHub下载文件
     */
    private String downloadFileFromGit(String repoUrl, String filePath, String ref) throws Exception {
        if (repoUrl == null) {
            log.warn("仓库URL为空，无法下载文件");
            return null;
        }
        
        // 转换为raw文件URL
        // https://github.com/user/repo -> https://raw.githubusercontent.com/user/repo/master/path/to/file
        String rawUrl = repoUrl.replace("github.com", "raw.githubusercontent.com") + "/" + ref.replace("refs/heads/", "") + "/" + filePath;
        
        log.info("下载文件: {}", rawUrl);
        
        URL url = new URL(rawUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        
        if (githubAccessToken != null && !githubAccessToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "token " + githubAccessToken);
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            return content.toString();
        } else {
            log.error("下载文件失败，状态码: {}", responseCode);
            return null;
        }
    }
    
    /**
     * 从GitLab下载文件。
     * 优先使用 path_with_namespace（URL 编码）作为项目标识，部分自建 GitLab 用 path 更稳定；
     * 未带 token 访问私有项目时 GitLab 会返回 404（不暴露项目存在），需配置 gitlab.access-token。
     */
    private String downloadFileFromGitLab(String projectUrl, Integer projectId, String pathWithNamespace, String filePath, String branch) throws Exception {
        if (projectUrl == null) {
            log.warn("项目 URL 为空，无法下载文件");
            return null;
        }
        // 项目标识：优先 path_with_namespace（URL 编码），否则用数字 id
        String projectIdentifier = (pathWithNamespace != null && !pathWithNamespace.isEmpty())
                ? pathWithNamespace.replace("/", "%2F")
                : (projectId != null ? String.valueOf(projectId) : null);
        if (projectIdentifier == null) {
            log.warn("项目信息不完整（无 id 且无 path_with_namespace），无法下载文件");
            return null;
        }
        
        // 提取 GitLab 域名
        String gitlabDomain = projectUrl.substring(0, projectUrl.indexOf("/", 8));
        
        // 构建 API URL（文件路径中斜杠编码为 %2F）
        String encodedFilePath = filePath.replace("/", "%2F");
        String apiUrl = gitlabDomain + "/api/v4/projects/" + projectIdentifier + "/repository/files/" + encodedFilePath + "/raw?ref=" + branch;
        
        boolean hasToken = gitlabAccessToken != null && !gitlabAccessToken.isEmpty();
        if (!hasToken) {
            log.warn("未配置 gitlab.access-token，若项目为私有或内部项目，GitLab 可能返回 404");
        }
        log.info("下载文件: {}, 原始路径: {}, 使用项目标识: {}", apiUrl, filePath, projectIdentifier);
        
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        
        if (hasToken) {
            connection.setRequestProperty("PRIVATE-TOKEN", gitlabAccessToken);
        }
        
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            log.info("文件下载成功: {}, 大小: {} 字节", filePath, content.length());
            return content.toString();
        } else {
            String errorMsg = "";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"))) {
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line);
                }
                errorMsg = error.toString();
            } catch (Exception e) {
                errorMsg = "无法读取错误信息";
            }
            log.error("下载文件失败，状态码: {}, 错误信息: {}, 文件路径: {}", responseCode, errorMsg, filePath);
            if (responseCode == 404 && errorMsg.contains("Project Not Found")) {
                log.error("提示: 404 Project Not Found 常见原因: 1) 私有/内部项目未配置 gitlab.access-token 或 token 无 read_repository 权限; 2) 自建 GitLab 若仍失败可确认 webhook 中 project.path_with_namespace 是否正确");
            }
            return null;
        }
    }
    
    /**
     * 校验 uat 分支的 commit 是否在 sit 分支存在（commit message 包含白名单关键字时跳过）
     * @return 校验失败时返回错误信息，校验通过返回 null
     */
    private String validateCommitsInSit(Map<String, Object> payload, String gitlabUrl, Integer projectId, String pathWithNamespace) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> commits = (List<Map<String, Object>>) payload.get("commits");
        if (commits == null || commits.isEmpty()) {
            return null; // 无 commit，不校验
        }
        
        String[] keywords = (bypassKeywords != null && !bypassKeywords.isEmpty()) 
                ? bypassKeywords.split(",") 
                : new String[0];
        
        String projectIdentifier = (pathWithNamespace != null && !pathWithNamespace.isEmpty())
                ? pathWithNamespace.replace("/", "%2F")
                : (projectId != null ? String.valueOf(projectId) : null);
        if (projectIdentifier == null || gitlabUrl == null) {
            log.warn("项目信息不完整，无法进行 uat 校验");
            return null;
        }
        
        String gitlabDomain = gitlabUrl.substring(0, gitlabUrl.indexOf("/", 8));
        String token = (gitlabAccessToken != null && !gitlabAccessToken.isEmpty()) ? gitlabAccessToken : null;
        
        List<String> violations = new ArrayList<>();
        
        for (Map<String, Object> commit : commits) {
            String sha = (String) commit.get("id");
            String message = (String) commit.get("message");
            
            if (sha == null) continue;
            
            // 检查白名单关键字
            boolean bypassed = false;
            if (message != null) {
                for (String keyword : keywords) {
                    if (message.contains(keyword.trim())) {
                        log.info("Commit {} 包含白名单关键字 '{}'，跳过校验", sha.substring(0, 8), keyword.trim());
                        bypassed = true;
                        break;
                    }
                }
            }
            
            if (bypassed) continue;
            
            // 调用 GitLab API 检查 commit 是否在 sit 分支
            boolean existsInSit = checkCommitInBranch(gitlabDomain, projectIdentifier, sha, "sit", token);
            if (!existsInSit) {
                String msg = String.format("Commit %s (%s) 不在 sit 分支", 
                        sha.substring(0, 8), 
                        message != null ? message.split("\n")[0] : "");
                violations.add(msg);
                log.warn(msg);
            }
        }
        
        if (!violations.isEmpty()) {
            return "UAT 分支校验失败，以下 commit 需先提交到 sit 分支: " + String.join("; ", violations);
        }
        
        return null;
    }
    
    /**
     * 检查指定 commit 是否在某个分支上
     * @param gitlabDomain GitLab 域名，如 https://gitlab.spdb.com
     * @param projectIdentifier 项目标识（path 编码或 id）
     * @param commitSha commit SHA
     * @param branchName 分支名（如 sit）
     * @param token access token
     * @return true=存在，false=不存在
     */
    private boolean checkCommitInBranch(String gitlabDomain, String projectIdentifier, String commitSha, String branchName, String token) {
        try {
            // GitLab API: GET /api/v4/projects/:id/repository/commits/:sha/refs?type=branch
            // 返回包含该 commit 的所有分支
            String apiUrl = gitlabDomain + "/api/v4/projects/" + projectIdentifier + "/repository/commits/" + commitSha + "/refs?type=branch";
            
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("PRIVATE-TOKEN", token);
            }
            
            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    resp.append(line);
                }
                reader.close();
                
                // 简单字符串检查：响应中是否包含 "name":"sit" 或 "name":"分支名"
                String respStr = resp.toString();
                boolean found = respStr.contains("\"name\":\"" + branchName + "\"");
                log.debug("Commit {} 在分支 {} 上: {}", commitSha.substring(0, 8), branchName, found);
                return found;
            } else {
                log.warn("检查 commit {} 是否在分支 {} 失败，状态码: {}", commitSha.substring(0, 8), branchName, code);
                return false;
            }
        } catch (Exception e) {
            log.error("检查 commit {} 是否在分支 {} 异常", commitSha.substring(0, 8), branchName, e);
            return false;
        }
    }
    
    /**
     * 创建返回结果
     */
    private Map<String, Object> createResult(boolean success, String message, int flowtranCount, int flowStepCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", message);
        result.put("flowtranCount", flowtranCount);
        result.put("flowStepCount", flowStepCount);
        return result;
    }
}
