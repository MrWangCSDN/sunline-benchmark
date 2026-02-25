package com.sunline.dict.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunline.dict.service.GitService;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Git操作服务实现 - 使用GitLab API
 */
@Service
public class GitServiceImpl implements GitService {
    
    private static final Logger log = LoggerFactory.getLogger(GitServiceImpl.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * GitLab地址
     */
    @Value("${git.gitlab.url:https://gitlab.spdb.com}")
    private String gitlabUrl;
    
    /**
     * GitLab用户名
     */
    @Value("${git.gitlab.username:c-wangsh8}")
    private String gitlabUsername;
    
    /**
     * GitLab密码
     */
    @Value("${git.gitlab.password:Liang@201314}")
    private String gitlabPassword;
    
    /**
     * GitLab Personal Access Token（如果配置了token，优先使用token）
     */
    @Value("${git.gitlab.token:}")
    private String gitlabToken;
    
    /**
     * Git工程列表配置，格式：GitLab项目ID，用逗号分隔
     * 例如：64145,64142,63227,63467,63474
     */
    @Value("${git.projects.list:}")
    private String gitProjectsList;
    
    /**
     * 获取GitLab认证头
     * 优先使用Token认证，如果Token未配置则使用Basic认证（用户名+密码）
     */
    private String getAuthHeader() {
        if (gitlabToken != null && !gitlabToken.trim().isEmpty()) {
            // 使用Token认证（推荐方式）
            log.debug("使用GitLab Token认证");
            return "Bearer " + gitlabToken.trim();
        } else {
            // 使用Basic认证（用户名+密码）
            log.debug("使用GitLab Basic认证（用户名+密码）");
            String credentials = gitlabUsername + ":" + gitlabPassword;
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            return "Basic " + encoded;
        }
    }
    
    /**
     * 执行GitLab API请求
     */
    private JsonNode executeApiRequest(String endpoint) throws Exception {
        String url = gitlabUrl + "/api/v4" + endpoint;
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", getAuthHeader());
            request.setHeader("Content-Type", "application/json");
            
            return httpClient.execute(request, (ClassicHttpResponse response) -> {
                try {
                    String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    
                    if (response.getCode() >= 200 && response.getCode() < 300) {
                        return objectMapper.readTree(responseBody);
                    } else {
                        throw new RuntimeException("GitLab API请求失败: " + response.getCode() + " - " + responseBody);
                    }
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    /**
     * 执行GitLab API POST请求
     */
    private JsonNode executeApiPostRequest(String endpoint, Map<String, Object> data) throws Exception {
        String url = gitlabUrl + "/api/v4" + endpoint;
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(url);
            request.setHeader("Authorization", getAuthHeader());
            request.setHeader("Content-Type", "application/json");
            
            String jsonBody = objectMapper.writeValueAsString(data);
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
            return httpClient.execute(request, (ClassicHttpResponse response) -> {
                try {
                    String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    
                    if (response.getCode() >= 200 && response.getCode() < 300) {
                        if (responseBody == null || responseBody.trim().isEmpty()) {
                            // 对于某些POST请求（如更新成员），可能返回空响应
                            return objectMapper.createObjectNode();
                        }
                        return objectMapper.readTree(responseBody);
                    } else {
                        throw new Exception("GitLab API请求失败: " + response.getCode() + " - " + responseBody);
                    }
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    @Override
    public List<Map<String, Object>> getProjects() {
        List<Map<String, Object>> projects = new ArrayList<>();
        
        // 如果配置了项目列表，优先使用配置
        if (gitProjectsList != null && !gitProjectsList.trim().isEmpty()) {
            // 直接使用GitLab项目ID列表，格式：64145,64142,63227
            String[] projectIds = gitProjectsList.split(",");
            for (String projectIdStr : projectIds) {
                projectIdStr = projectIdStr.trim();
                if (!projectIdStr.isEmpty()) {
                    try {
                        // 使用GitLab项目ID从API获取项目详细信息
                        String gitlabProjectId = projectIdStr.trim();
                        JsonNode projectJson = executeApiRequest("/projects/" + gitlabProjectId);
                        
                        Map<String, Object> project = new HashMap<>();
                        int projectId = projectJson.get("id").asInt();
                        project.put("id", projectId);
                        project.put("gitlabProjectId", String.valueOf(projectId));
                        project.put("name", projectJson.get("name").asText());
                        
                        if (projectJson.has("path_with_namespace")) {
                            project.put("path", projectJson.get("path_with_namespace").asText());
                        } else if (projectJson.has("path")) {
                            project.put("path", projectJson.get("path").asText());
                        } else {
                            project.put("path", projectJson.get("name").asText());
                        }
                        
                        projects.add(project);
                        log.debug("成功加载Git项目: {} (ID: {})", project.get("name"), projectId);
                    } catch (Exception e) {
                        log.warn("获取GitLab项目 {} 详细信息失败: {}", projectIdStr, e.getMessage());
                    }
                }
            }
            log.info("从配置中加载了 {} 个项目", projects.size());
        } else {
            // 如果没有配置，从GitLab API获取用户的所有项目
            try {
                // 获取用户的所有项目（包括用户拥有的和作为成员的项目）
                // owned=true 获取用户拥有的项目，membership=true 获取用户作为成员的项目
                // 使用分页获取所有项目
                int page = 1;
                int perPage = 100;
                boolean hasMore = true;
                
                while (hasMore) {
                    String endpoint = String.format("/projects?owned=true&membership=true&per_page=%d&page=%d", perPage, page);
                    JsonNode projectsJson = executeApiRequest(endpoint);
                    
                    if (projectsJson.isArray() && projectsJson.size() > 0) {
                        for (JsonNode project : projectsJson) {
                            Map<String, Object> projectMap = new HashMap<>();
                            int projectId = project.get("id").asInt();
                            projectMap.put("id", projectId);
                            projectMap.put("gitlabProjectId", String.valueOf(projectId));
                            projectMap.put("name", project.get("name").asText());
                            projectMap.put("path", project.has("path_with_namespace") ? 
                                project.get("path_with_namespace").asText() : project.get("path").asText());
                            projects.add(projectMap);
                        }
                        
                        // 如果返回的项目数少于perPage，说明已经是最后一页
                        if (projectsJson.size() < perPage) {
                            hasMore = false;
                        } else {
                            page++;
                        }
                    } else {
                        hasMore = false;
                    }
                }
                
                log.info("从GitLab获取到 {} 个项目", projects.size());
            } catch (Exception e) {
                log.error("从GitLab获取项目列表失败", e);
            }
        }
        
        return projects;
    }
    
    @Override
    public List<String> getBranches() {
        Set<String> branches = new HashSet<>();
        
        // 从所有工程中获取分支列表
        List<Map<String, Object>> projects = getProjects();
        for (Map<String, Object> project : projects) {
            try {
                String projectId = String.valueOf(project.get("gitlabProjectId") != null ? 
                    project.get("gitlabProjectId") : project.get("id"));
                
                JsonNode branchesJson = executeApiRequest("/projects/" + projectId + "/repository/branches");
                
                if (branchesJson.isArray()) {
                    for (JsonNode branch : branchesJson) {
                        branches.add(branch.get("name").asText());
                    }
                }
            } catch (Exception e) {
                log.warn("获取工程 {} 的分支列表失败: {}", project.get("name"), e.getMessage());
            }
        }
        
        // 如果没有获取到分支，返回默认分支列表
        if (branches.isEmpty()) {
            return Arrays.asList("main", "master", "develop", "dev");
        }
        
        return branches.stream().sorted().collect(java.util.stream.Collectors.toList());
    }
    
    @Override
    public List<Map<String, String>> createBranches(String newBranchName, String sourceBranch, List<Integer> projectIds, 
                                                     String sourceVersion, String newVersion) {
        List<Map<String, String>> results = new ArrayList<>();
        List<Map<String, Object>> projects = getProjects();
        
        for (Map<String, Object> project : projects) {
            Integer projectId = (Integer) project.get("id");
            if (!projectIds.contains(projectId)) {
                continue;
            }
            
            String projectName = (String) project.get("name");
            String gitlabProjectId = String.valueOf(project.get("gitlabProjectId") != null ? 
                project.get("gitlabProjectId") : project.get("id"));
            
            Map<String, String> result = new HashMap<>();
            result.put("project", projectName);
            
            try {
                // 创建分支
                createBranch(gitlabProjectId, newBranchName, sourceBranch);
                
                // 如果需要替换版本号
                if (newVersion != null && !newVersion.trim().isEmpty()) {
                    try {
                        // 替换pom.xml版本号
                        updatePomVersions(gitlabProjectId, newBranchName, sourceVersion, newVersion);
                        result.put("message", "✅ 分支创建成功，版本号已替换为: " + newVersion);
                    } catch (Exception e) {
                        log.error("在工程 {} 中替换版本号失败", projectName, e);
                        result.put("message", "✅ 分支创建成功，但版本号替换失败: " + e.getMessage());
                    }
                } else {
                    result.put("message", "✅ 分支创建成功");
                }
            } catch (Exception e) {
                log.error("在工程 {} 中创建分支失败", projectName, e);
                result.put("message", "❌ 创建失败: " + e.getMessage());
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 在指定GitLab项目中创建分支
     */
    private void createBranch(String gitlabProjectId, String newBranchName, String sourceBranch) throws Exception {
        // 使用GitLab API创建分支
        Map<String, Object> data = new HashMap<>();
        data.put("branch", newBranchName);
        data.put("ref", sourceBranch);
        
        try {
            executeApiPostRequest("/projects/" + gitlabProjectId + "/repository/branches", data);
            log.info("在GitLab项目 {} 中成功创建分支 {}，基于分支 {}", gitlabProjectId, newBranchName, sourceBranch);
        } catch (Exception e) {
            // 检查是否是分支已存在的错误
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                throw new Exception("分支已存在: " + newBranchName);
            }
            throw e;
        }
    }
    
    /**
     * 更新pom.xml文件的版本号
     */
    private void updatePomVersions(String gitlabProjectId, String branchName, String sourceVersion, String newVersion) throws Exception {
        log.info("开始在项目 {} 的分支 {} 中查找并替换pom.xml版本号: {} -> {}", gitlabProjectId, branchName, sourceVersion, newVersion);
        
        // 递归查找所有pom.xml文件
        List<String> pomFiles = findAllPomFiles(gitlabProjectId, branchName);
        
        if (pomFiles.isEmpty()) {
            log.warn("项目 {} 中未找到pom.xml文件", gitlabProjectId);
            return;
        }
        
        log.info("找到 {} 个pom.xml文件，开始替换版本号", pomFiles.size());
        
        // 构建批量提交的数据
        List<Map<String, Object>> actions = new ArrayList<>();
        
        int successCount = 0;
        int skipCount = 0;
        
        for (String pomPath : pomFiles) {
            try {
                log.info("处理文件: {}", pomPath);
                // 获取文件内容
                String fileContent = getFileContent(gitlabProjectId, branchName, pomPath);
                log.debug("文件内容长度: {}, 是否包含源版本号 {}: {}", 
                         fileContent.length(), sourceVersion, fileContent.contains(sourceVersion));
                
                // 替换版本号（使用正则表达式，支持多种格式）
                String newContent = replaceVersionInPom(fileContent, sourceVersion, newVersion);
                
                // 如果内容有变化，添加到提交列表
                if (!newContent.equals(fileContent)) {
                    Map<String, Object> action = new HashMap<>();
                    action.put("action", "update");
                    action.put("file_path", pomPath);
                    action.put("content", newContent);
                    action.put("encoding", "text");
                    actions.add(action);
                    successCount++;
                    log.info("文件 {} 版本号已更新，准备提交", pomPath);
                } else {
                    skipCount++;
                    log.warn("文件 {} 版本号未发生变化，可能版本号不匹配", pomPath);
                }
            } catch (Exception e) {
                skipCount++;
                // 如果是文件不存在，记录警告而不是错误，继续处理其他文件
                if (e.getMessage() != null && e.getMessage().contains("404")) {
                    log.warn("跳过文件 {} (文件不存在或不在该分支中): {}", pomPath, e.getMessage());
                } else {
                    log.error("处理文件 {} 时出错，跳过该文件: {}", pomPath, e.getMessage());
                }
            }
        }
        
        log.info("文件处理完成: 成功 {} 个，跳过 {} 个", successCount, skipCount);
        
        // 如果有文件需要更新，批量提交
        if (!actions.isEmpty()) {
            commitFiles(gitlabProjectId, branchName, actions, "merge 版本号修改");
            log.info("成功更新了 {} 个pom.xml文件的版本号", actions.size());
        } else {
            log.info("未找到需要更新的pom.xml文件（版本号可能不匹配）");
        }
    }
    
    /**
     * 递归查找所有pom.xml文件（支持多模块项目）
     * 使用GitLab API的递归tree接口，查找工程下所有目录的pom.xml文件
     */
    private List<String> findAllPomFiles(String gitlabProjectId, String branchName) throws Exception {
        List<String> pomFiles = new ArrayList<>();
        
        try {
            log.info("开始查找项目 {} 分支 {} 中的所有pom.xml文件（递归搜索）", gitlabProjectId, branchName);
            
            // 使用递归方式获取所有文件
            // 分页获取，确保获取所有文件（如果文件很多）
            int page = 1;
            int perPage = 100;
            boolean hasMore = true;
            
            while (hasMore) {
                // 构建API端点，使用recursive=true递归获取所有文件和目录
                String endpoint = "/projects/" + gitlabProjectId + "/repository/tree";
                endpoint += "?ref=" + branchName;
                endpoint += "&recursive=true";
                endpoint += "&per_page=" + perPage;
                endpoint += "&page=" + page;
                
                log.debug("请求目录树: page={}", page);
                
                JsonNode treeJson = executeApiRequest(endpoint);
                
                if (treeJson.isArray()) {
                    int itemCount = 0;
                    int pomCountInPage = 0;
                    
                    for (JsonNode item : treeJson) {
                        itemCount++;
                        
                        // 获取文件路径和类型
                        if (!item.has("path")) {
                            continue;
                        }
                        
                        String filePath = item.get("path").asText();
                        String fileType = item.has("type") ? item.get("type").asText() : "blob";
                        
                        // 查找pom.xml文件（blob类型表示文件，tree类型表示目录）
                        if ("blob".equals(fileType) && filePath.endsWith("pom.xml")) {
                            pomFiles.add(filePath);
                            pomCountInPage++;
                            log.debug("找到pom.xml文件: {}", filePath);
                        }
                    }
                    
                    log.debug("第 {} 页: 总共 {} 个文件/目录项，其中 {} 个pom.xml文件", page, itemCount, pomCountInPage);
                    
                    // 如果返回的数量少于perPage，说明已经是最后一页
                    if (treeJson.size() < perPage) {
                        hasMore = false;
                    } else {
                        page++;
                    }
                } else {
                    hasMore = false;
                }
            }
            
            log.info("总共找到 {} 个pom.xml文件", pomFiles.size());
            if (!pomFiles.isEmpty()) {
                log.info("pom.xml文件列表:");
                for (String pomFile : pomFiles) {
                    log.info("  - {}", pomFile);
                }
            } else {
                log.warn("未找到任何pom.xml文件，请检查分支名称和项目配置");
            }
        } catch (Exception e) {
            log.error("查找pom.xml文件失败: {}", e.getMessage(), e);
            throw new Exception("查找pom.xml文件失败: " + e.getMessage(), e);
        }
        
        return pomFiles;
    }
    
    /**
     * 获取文件内容
     * @throws Exception 如果文件不存在（404）或其他错误
     */
    private String getFileContent(String gitlabProjectId, String branchName, String filePath) throws Exception {
        // GitLab API要求文件路径需要进行URL编码，路径分隔符/应该编码为%2F
        String encodedPath = encodePathForGitLab(filePath);
        String endpoint = "/projects/" + gitlabProjectId + "/repository/files/" + encodedPath + "/raw";
        endpoint += "?ref=" + java.net.URLEncoder.encode(branchName, StandardCharsets.UTF_8);
        
        // 直接获取原始文件内容
        String url = gitlabUrl + "/api/v4" + endpoint;
        
        log.debug("获取文件内容 - URL: {}", url);
        log.debug("文件路径: {} -> 编码后: {}", filePath, encodedPath);
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setHeader("Authorization", getAuthHeader());
            
            return httpClient.execute(request, (ClassicHttpResponse response) -> {
                try {
                    int statusCode = response.getCode();
                    if (statusCode >= 200 && statusCode < 300) {
                        return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    } else if (statusCode == 404) {
                        String errorBody = "";
                        try {
                            errorBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        } catch (Exception ignored) {}
                        log.warn("文件不存在 (404) - 路径: {}, 分支: {}, 编码路径: {}, 错误信息: {}", 
                                filePath, branchName, encodedPath, errorBody);
                        throw new Exception("文件不存在 (404): " + filePath + " 在分支 " + branchName + " - " + errorBody);
                    } else {
                        String errorBody = "";
                        try {
                            errorBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        } catch (Exception ignored) {}
                        throw new Exception("获取文件失败 (" + statusCode + "): " + filePath + " - " + errorBody);
                    }
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    /**
     * 在pom.xml中全局替换版本号
     * 直接字符串替换，简单高效
     */
    private String replaceVersionInPom(String pomContent, String sourceVersion, String newVersion) {
        if (pomContent == null || pomContent.isEmpty()) {
            log.warn("pom.xml内容为空，无法替换版本号");
            return pomContent;
        }
        
        String originalContent = pomContent;
        
        log.debug("开始全局替换版本号: {} -> {}", sourceVersion, newVersion);
        
        // 先检查是否包含源版本号
        if (!pomContent.contains(sourceVersion)) {
            log.warn("pom.xml中未找到源版本号: {}", sourceVersion);
            return pomContent;
        }
        
        // 直接全局替换版本号字符串
        pomContent = pomContent.replace(sourceVersion, newVersion);
        
        // 检查替换结果
        if (!pomContent.equals(originalContent)) {
            // 计算替换次数
            int replaceCount = (originalContent.length() - originalContent.replace(sourceVersion, "").length()) / sourceVersion.length();
            log.info("成功全局替换版本号，替换了 {} 处", replaceCount);
        } else {
            log.warn("版本号替换失败，pom.xml内容未发生变化。源版本号: {}", sourceVersion);
        }
        
        return pomContent;
    }
    
    /**
     * URL编码路径（用于GitLab API）
     * GitLab API要求文件路径中的斜杠/编码为%2F，点.编码为%2E
     * 例如：ap-batch/pom.xml -> ap-batch%2Fpom%2Exml
     * 
     * 注意：URLEncoder.encode会把/编码为%2F，但不会编码.，所以需要手动处理.
     */
    private String encodePathForGitLab(String path) {
        try {
            if (path == null || path.isEmpty()) {
                return path;
            }
            // 先用URLEncoder编码（会把/编码为%2F，空格编码为+，但不会编码.）
            String encoded = java.net.URLEncoder.encode(path, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20")  // 空格编码为%20而不是+
                    .replace(".", "%2E"); // 点号编码为%2E（GitLab API要求，URLEncoder不会编码.）
            
            // 确保/被编码为%2F（URLEncoder应该已经编码了，但为了保险再检查一次）
            if (encoded.contains("/")) {
                encoded = encoded.replace("/", "%2F");
            }
            
            log.debug("路径编码: {} -> {}", path, encoded);
            return encoded;
        } catch (Exception e) {
            log.warn("路径编码失败，使用原始路径: {}", path, e);
            return path;
        }
    }
    
    /**
     * URL编码路径（保留方法以兼容其他可能的用途）
     * @deprecated 请使用 encodePathForGitLab 方法
     */
    @Deprecated
    private String encodePath(String path) {
        return encodePathForGitLab(path);
    }
    
    /**
     * 批量提交文件修改
     */
    private void commitFiles(String gitlabProjectId, String branchName, List<Map<String, Object>> actions, String commitMessage) throws Exception {
        Map<String, Object> commitData = new HashMap<>();
        commitData.put("branch", branchName);
        commitData.put("commit_message", commitMessage);
        commitData.put("actions", actions);
        
        String endpoint = "/projects/" + gitlabProjectId + "/repository/commits";
        executeApiPostRequest(endpoint, commitData);
        log.info("成功提交 {} 个文件的修改到分支 {}", actions.size(), branchName);
    }
    
    @Override
    public List<Map<String, Object>> inviteUsers(List<String> users, int accessLevel, List<Integer> projectIds) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Integer projectId : projectIds) {
            // 获取项目信息
            String projectName = "项目ID: " + projectId;
            try {
                JsonNode projectJson = executeApiRequest("/projects/" + projectId);
                projectName = projectJson.get("name").asText();
            } catch (Exception e) {
                log.warn("获取项目 {} 信息失败: {}", projectId, e.getMessage());
            }
            
            for (String user : users) {
                Map<String, Object> result = new HashMap<>();
                result.put("project", projectName);
                result.put("user", user);
                
                try {
                    // 先查找用户ID
                    Integer userId = findUserIdByUsernameOrEmail(user);
                    if (userId == null) {
                        result.put("success", false);
                        result.put("message", "用户不存在: " + user);
                        results.add(result);
                        continue;
                    }
                    
                    // 检查用户是否已经是项目成员
                    boolean isMember = checkUserIsMember(projectId, userId);
                    if (isMember) {
                        // 如果已经是成员，更新权限
                        updateMemberAccessLevel(projectId, userId, accessLevel);
                        result.put("success", true);
                        result.put("message", "权限已更新为 " + getAccessLevelName(accessLevel));
                    } else {
                        // 邀请新用户
                        addProjectMember(projectId, userId, accessLevel);
                        result.put("success", true);
                        result.put("message", "已授予 " + getAccessLevelName(accessLevel) + " 权限");
                    }
                } catch (Exception e) {
                    log.error("邀请用户 {} 到项目 {} 失败", user, projectId, e);
                    result.put("success", false);
                    result.put("message", "操作失败: " + e.getMessage());
                }
                
                results.add(result);
            }
        }
        
        return results;
    }
    
    @Override
    public List<Map<String, Object>> revokeUsers(List<String> users, List<Integer> projectIds) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Integer projectId : projectIds) {
            // 获取项目信息
            String projectName = "项目ID: " + projectId;
            try {
                JsonNode projectJson = executeApiRequest("/projects/" + projectId);
                projectName = projectJson.get("name").asText();
            } catch (Exception e) {
                log.warn("获取项目 {} 信息失败: {}", projectId, e.getMessage());
            }
            
            for (String user : users) {
                Map<String, Object> result = new HashMap<>();
                result.put("project", projectName);
                result.put("user", user);
                
                try {
                    // 先查找用户ID
                    Integer userId = findUserIdByUsernameOrEmail(user);
                    if (userId == null) {
                        result.put("success", false);
                        result.put("message", "用户不存在: " + user);
                        results.add(result);
                        continue;
                    }
                    
                    // 检查用户是否是项目成员
                    boolean isMember = checkUserIsMember(projectId, userId);
                    if (!isMember) {
                        result.put("success", false);
                        result.put("message", "用户不是项目成员");
                        results.add(result);
                        continue;
                    }
                    
                    // 移除用户权限
                    removeProjectMember(projectId, userId);
                    result.put("success", true);
                    result.put("message", "权限已收回");
                } catch (Exception e) {
                    log.error("收回用户 {} 在项目 {} 的权限失败", user, projectId, e);
                    result.put("success", false);
                    result.put("message", "操作失败: " + e.getMessage());
                }
                
                results.add(result);
            }
        }
        
        return results;
    }
    
    /**
     * 根据用户名或邮箱查找用户ID
     */
    private Integer findUserIdByUsernameOrEmail(String usernameOrEmail) throws Exception {
        try {
            // 尝试通过用户名查找
            JsonNode usersJson = executeApiRequest("/users?username=" + usernameOrEmail);
            if (usersJson.isArray() && usersJson.size() > 0) {
                return usersJson.get(0).get("id").asInt();
            }
            
            // 尝试通过邮箱查找
            usersJson = executeApiRequest("/users?search=" + usernameOrEmail);
            if (usersJson.isArray()) {
                for (JsonNode user : usersJson) {
                    String email = user.has("email") ? user.get("email").asText() : "";
                    String username = user.has("username") ? user.get("username").asText() : "";
                    if (email.equals(usernameOrEmail) || username.equals(usernameOrEmail)) {
                        return user.get("id").asInt();
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("查找用户 {} 失败", usernameOrEmail, e);
            throw e;
        }
    }
    
    /**
     * 检查用户是否是项目成员
     */
    private boolean checkUserIsMember(Integer projectId, Integer userId) throws Exception {
        try {
            JsonNode membersJson = executeApiRequest("/projects/" + projectId + "/members");
            if (membersJson.isArray()) {
                for (JsonNode member : membersJson) {
                    if (member.get("id").asInt() == userId) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.error("检查用户 {} 是否是项目 {} 成员失败", userId, projectId, e);
            throw e;
        }
    }
    
    /**
     * 添加项目成员
     */
    private void addProjectMember(Integer projectId, Integer userId, int accessLevel) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("user_id", userId);
        data.put("access_level", accessLevel);
        // 设置为永久权限（不设置expires_at）
        
        executeApiPostRequest("/projects/" + projectId + "/members", data);
    }
    
    /**
     * 更新成员权限级别
     */
    private void updateMemberAccessLevel(Integer projectId, Integer userId, int accessLevel) throws Exception {
        Map<String, Object> data = new HashMap<>();
        data.put("access_level", accessLevel);
        
        executeApiPutRequest("/projects/" + projectId + "/members/" + userId, data);
    }
    
    /**
     * 移除项目成员
     */
    private void removeProjectMember(Integer projectId, Integer userId) throws Exception {
        String endpoint = "/projects/" + projectId + "/members/" + userId;
        String url = gitlabUrl + "/api/v4" + endpoint;
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            org.apache.hc.client5.http.classic.methods.HttpDelete request = 
                new org.apache.hc.client5.http.classic.methods.HttpDelete(url);
            request.setHeader("Authorization", getAuthHeader());
            request.setHeader("Content-Type", "application/json");
            
            httpClient.execute(request, (ClassicHttpResponse response) -> {
                if (response.getCode() >= 200 && response.getCode() < 300) {
                    return null;
                } else {
                    try {
                        String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        throw new RuntimeException("GitLab API请求失败: " + response.getCode() + " - " + responseBody);
                    } catch (Exception e) {
                        throw new RuntimeException("GitLab API请求失败: " + response.getCode());
                    }
                }
            });
        }
    }
    
    /**
     * 获取权限级别名称
     */
    private String getAccessLevelName(int accessLevel) {
        switch (accessLevel) {
            case 10: return "Guest";
            case 20: return "Reporter";
            case 30: return "Developer";
            case 40: return "Maintainer";
            default: return "Unknown";
        }
    }
    
    /**
     * 执行GitLab API PUT请求
     */
    private JsonNode executeApiPutRequest(String endpoint, Map<String, Object> data) throws Exception {
        String url = gitlabUrl + "/api/v4" + endpoint;
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            org.apache.hc.client5.http.classic.methods.HttpPut request = 
                new org.apache.hc.client5.http.classic.methods.HttpPut(url);
            request.setHeader("Authorization", getAuthHeader());
            request.setHeader("Content-Type", "application/json");
            
            String jsonBody = objectMapper.writeValueAsString(data);
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
            return httpClient.execute(request, (ClassicHttpResponse response) -> {
                try {
                    String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    
                    if (response.getCode() >= 200 && response.getCode() < 300) {
                        if (responseBody == null || responseBody.trim().isEmpty()) {
                            return objectMapper.createObjectNode();
                        }
                        return objectMapper.readTree(responseBody);
                    } else {
                        throw new Exception("GitLab API请求失败: " + response.getCode() + " - " + responseBody);
                    }
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }
            });
        }
    }
    
    /**
     * 更新指定分支的pom.xml版本号
     */
    @Override
    public List<Map<String, String>> updateVersions(String sourceBranch, String sourceVersion, String newVersion, List<Integer> projectIds) {
        List<Map<String, String>> results = new ArrayList<>();
        List<Map<String, Object>> projects = getProjects();

        for (Map<String, Object> project : projects) {
            Integer projectId = (Integer) project.get("id");
            if (!projectIds.contains(projectId)) {
                continue;
            }

            String projectName = (String) project.get("name");
            String gitlabProjectId = String.valueOf(project.get("gitlabProjectId") != null ? 
                project.get("gitlabProjectId") : project.get("id"));

            Map<String, String> result = new HashMap<>();
            result.put("project", projectName);

            try {
                // 更新pom.xml版本号
                updatePomVersions(gitlabProjectId, sourceBranch, sourceVersion, newVersion);
                result.put("message", "✅ 版本号已成功替换为: " + newVersion);
                log.info("在工程 {} 的分支 {} 中成功更新版本号: {} -> {}", projectName, sourceBranch, sourceVersion, newVersion);
            } catch (Exception e) {
                log.error("在工程 {} 的分支 {} 中更新版本号失败", projectName, sourceBranch, e);
                result.put("message", "❌ 更新失败: " + e.getMessage());
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 删除分支
     */
    @Override
    public List<Map<String, String>> deleteBranches(String branchName, List<Integer> projectIds) {
        List<Map<String, String>> results = new ArrayList<>();
        List<Map<String, Object>> projects = getProjects();

        for (Map<String, Object> project : projects) {
            Integer projectId = (Integer) project.get("id");
            if (!projectIds.contains(projectId)) {
                continue;
            }

            String projectName = (String) project.get("name");
            String gitlabProjectId = String.valueOf(project.get("gitlabProjectId") != null ? 
                project.get("gitlabProjectId") : project.get("id"));

            Map<String, String> result = new HashMap<>();
            result.put("project", projectName);

            try {
                deleteBranch(gitlabProjectId, branchName);
                result.put("message", "✅ 分支删除成功: " + branchName);
                log.info("在工程 {} 中成功删除分支 {}", projectName, branchName);
            } catch (Exception e) {
                log.error("在工程 {} 中删除分支 {} 失败", projectName, branchName, e);
                result.put("message", "❌ 删除失败: " + e.getMessage());
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 在指定GitLab项目中删除分支
     */
    private void deleteBranch(String gitlabProjectId, String branchName) throws Exception {
        // 使用GitLab API删除分支
        String endpoint = "/projects/" + gitlabProjectId + "/repository/branches/" + 
                         java.net.URLEncoder.encode(branchName, StandardCharsets.UTF_8);
        
        try {
            // GitLab删除分支使用DELETE方法
            String url = gitlabUrl + "/api/v4" + endpoint;
            
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpDelete request = new HttpDelete(url);
                request.setHeader("Authorization", getAuthHeader());
                
                httpClient.execute(request, (ClassicHttpResponse response) -> {
                    try {
                        int statusCode = response.getCode();
                        if (statusCode >= 200 && statusCode < 300) {
                            log.info("在GitLab项目 {} 中成功删除分支 {}", gitlabProjectId, branchName);
                            return null;
                        } else {
                            String errorBody = "";
                            try {
                                errorBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                            } catch (Exception ignored) {}
                            
                            if (statusCode == 404) {
                                throw new Exception("分支不存在: " + branchName + " - " + errorBody);
                            } else {
                                throw new Exception("删除分支失败 (" + statusCode + "): " + errorBody);
                            }
                        }
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (Exception e) {
            // 检查是否是分支不存在的错误
            if (e.getMessage() != null && (e.getMessage().contains("404") || e.getMessage().contains("不存在"))) {
                throw new Exception("分支不存在: " + branchName);
            }
            throw e;
        }
    }
    
    /**
     * 更新保护分支设置
     */
    @Override
    public List<Map<String, String>> updateProtectedBranch(String branchName, int allowedToMerge, 
                                                           int allowedToPush, boolean allowForcePush, 
                                                           List<Integer> projectIds) {
        List<Map<String, String>> results = new ArrayList<>();
        List<Map<String, Object>> projects = getProjects();

        for (Map<String, Object> project : projects) {
            Integer projectId = (Integer) project.get("id");
            if (!projectIds.contains(projectId)) {
                continue;
            }

            String projectName = (String) project.get("name");
            String gitlabProjectId = String.valueOf(project.get("gitlabProjectId") != null ? 
                project.get("gitlabProjectId") : project.get("id"));

            Map<String, String> result = new HashMap<>();
            result.put("project", projectName);

            try {
                protectBranch(gitlabProjectId, branchName, allowedToMerge, allowedToPush, allowForcePush);
                result.put("message", "✅ 保护分支设置成功");
                log.info("在工程 {} 中成功设置保护分支 {}", projectName, branchName);
            } catch (Exception e) {
                log.error("在工程 {} 中设置保护分支 {} 失败", projectName, branchName, e);
                result.put("message", "❌ 设置失败: " + e.getMessage());
            }
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 设置保护分支
     */
    private void protectBranch(String projectId, String branchName, int allowedToMerge, 
                              int allowedToPush, boolean allowForcePush) throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // 先尝试取消保护（如果已经被保护）
            try {
                String unprotectUrl = gitlabUrl + "/api/v4/projects/" + projectId + 
                                     "/protected_branches/" + branchName + "?private_token=" + gitlabToken;
                HttpDelete unprotectRequest = new HttpDelete(unprotectUrl);
                client.execute(unprotectRequest, response -> {
                    EntityUtils.consume(response.getEntity());
                    return null;
                });
                log.info("已取消分支 {} 的现有保护设置", branchName);
            } catch (Exception e) {
                // 分支可能原本就没被保护，忽略错误
                log.debug("取消保护分支时出错（可能原本未保护）: {}", e.getMessage());
            }
            
            // 重新设置保护分支
            String protectUrl = gitlabUrl + "/api/v4/projects/" + projectId + 
                               "/protected_branches?private_token=" + gitlabToken;
            HttpPost protectRequest = new HttpPost(protectUrl);
            protectRequest.setHeader("Content-Type", "application/json");
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", branchName);
            requestBody.put("push_access_level", allowedToPush);
            requestBody.put("merge_access_level", allowedToMerge);
            requestBody.put("allow_force_push", allowForcePush);
            
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            protectRequest.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
            client.execute(protectRequest, response -> {
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                if (statusCode >= 200 && statusCode < 300) {
                    log.info("成功设置保护分支: {}, 响应: {}", branchName, responseBody);
                } else {
                    throw new RuntimeException("设置保护分支失败，状态码: " + statusCode + ", 响应: " + responseBody);
                }
                
                return null;
            });
        }
    }
    
    @Override
    public List<Map<String, String>> cloneProjects(String branchName, String cloneDirectory, List<Integer> projectIds) {
        log.info("==================== 开始批量克隆Git项目 ====================");
        log.info("分支名: {}", branchName);
        log.info("克隆目录: {}", cloneDirectory);
        log.info("项目数量: {}", projectIds.size());
        
        List<Map<String, String>> results = new ArrayList<>();
        
        // 检查并创建克隆目录
        java.io.File cloneDir = new java.io.File(cloneDirectory);
        if (!cloneDir.exists()) {
            log.info("克隆目录不存在，尝试创建: {}", cloneDirectory);
            if (!cloneDir.mkdirs()) {
                String errorMsg = "无法创建克隆目录: " + cloneDirectory;
                log.error(errorMsg);
                for (Integer projectId : projectIds) {
                    Map<String, String> result = new HashMap<>();
                    result.put("project", "项目ID: " + projectId);
                    result.put("message", errorMsg);
                    results.add(result);
                }
                return results;
            }
            log.info("克隆目录创建成功: {}", cloneDirectory);
        } else {
            log.info("克隆目录已存在: {}", cloneDirectory);
        }
        
        // 遍历每个项目进行克隆
        for (Integer projectId : projectIds) {
            Map<String, String> result = new HashMap<>();
            String projectName = "未知项目";
            
            try {
                log.info("------------------------------------------------------------");
                log.info("开始克隆项目ID: {}", projectId);
                
                // 获取项目信息
                String endpoint = "/projects/" + projectId;
                JsonNode projectJson = executeApiRequest(endpoint);
                
                projectName = projectJson.get("name").asText();
                String httpUrlToRepo = projectJson.get("http_url_to_repo").asText();
                
                log.info("项目名称: {}", projectName);
                log.info("项目仓库地址: {}", httpUrlToRepo);
                
                // 构建包含认证信息的Git URL
                String authenticatedUrl = buildAuthenticatedGitUrl(httpUrlToRepo);
                
                // 构建目标目录
                java.io.File targetDir = new java.io.File(cloneDir, projectName);
                
                // 如果目标目录已存在，先删除或跳过
                if (targetDir.exists()) {
                    log.warn("目标目录已存在: {}, 将跳过克隆", targetDir.getAbsolutePath());
                    result.put("project", projectName);
                    result.put("message", "目录已存在，跳过克隆: " + targetDir.getAbsolutePath());
                    results.add(result);
                    continue;
                }
                
                // 执行git clone命令
                List<String> command = new ArrayList<>();
                command.add("git");
                command.add("clone");
                command.add("-b");
                command.add(branchName);
                command.add("--single-branch");
                command.add(authenticatedUrl);
                command.add(targetDir.getAbsolutePath());
                
                log.info("执行命令: git clone -b {} --single-branch <repo> {}", branchName, targetDir.getAbsolutePath());
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(cloneDir);
                pb.redirectErrorStream(true);
                
                Process process = pb.start();
                
                // 读取命令输出
                StringBuilder output = new StringBuilder();
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                        log.debug("Git输出: {}", line);
                    }
                }
                
                int exitCode = process.waitFor();
                
                if (exitCode == 0) {
                    String successMsg = "克隆成功，目录: " + targetDir.getAbsolutePath();
                    log.info("✓ {}", successMsg);
                    result.put("project", projectName);
                    result.put("message", successMsg);
                } else {
                    String errorMsg = "克隆失败，退出码: " + exitCode + ", 输出: " + output.toString();
                    log.error("✗ {}", errorMsg);
                    result.put("project", projectName);
                    result.put("message", errorMsg);
                }
                
                results.add(result);
                
            } catch (Exception e) {
                log.error("克隆项目失败: {}", projectName, e);
                result.put("project", projectName);
                result.put("message", "克隆失败: " + e.getMessage());
                results.add(result);
            }
        }
        
        log.info("==================== 批量克隆完成 ====================");
        return results;
    }
    
    /**
     * 构建包含认证信息的Git URL
     */
    private String buildAuthenticatedGitUrl(String httpUrlToRepo) {
        try {
            // 将 https://gitlab.spdb.com/xxx.git 转换为 https://用户名:密码@gitlab.spdb.com/xxx.git
            if (httpUrlToRepo.startsWith("https://")) {
                String urlWithoutProtocol = httpUrlToRepo.substring(8); // 移除 "https://"
                String credentials = gitlabUsername + ":" + gitlabPassword;
                return "https://" + credentials + "@" + urlWithoutProtocol;
            } else if (httpUrlToRepo.startsWith("http://")) {
                String urlWithoutProtocol = httpUrlToRepo.substring(7); // 移除 "http://"
                String credentials = gitlabUsername + ":" + gitlabPassword;
                return "http://" + credentials + "@" + urlWithoutProtocol;
            }
            return httpUrlToRepo;
        } catch (Exception e) {
            log.error("构建认证URL失败", e);
            return httpUrlToRepo;
        }
    }
}
