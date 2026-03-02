package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.GitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Git操作Controller
 */
@RestController
@RequestMapping("/api/git")
@CrossOrigin
public class GitController {
    
    private static final Logger log = LoggerFactory.getLogger(GitController.class);
    
    @Autowired
    private GitService gitService;
    
    /**
     * 获取Git工程列表
     */
    @GetMapping("/projects")
    public Result<List<Map<String, Object>>> getProjects() {
        try {
            List<Map<String, Object>> projects = gitService.getProjects();
            return Result.success(projects);
        } catch (Exception e) {
            log.error("获取Git工程列表失败", e);
            return Result.error("获取Git工程列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取分支列表
     */
    @GetMapping("/branches")
    public Result<List<String>> getBranches() {
        try {
            List<String> branches = gitService.getBranches();
            return Result.success(branches);
        } catch (Exception e) {
            log.error("获取分支列表失败", e);
            return Result.error("获取分支列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建分支
     */
    @PostMapping("/branches/create")
    public Result<List<Map<String, String>>> createBranches(@RequestBody Map<String, Object> request) {
        try {
            String newBranchName = (String) request.get("newBranchName");
            String sourceBranch = (String) request.get("sourceBranch");
            String sourceVersion = (String) request.get("sourceVersion");
            String newVersion = (String) request.get("newVersion");
            @SuppressWarnings("unchecked")
            List<Integer> projectIds = (List<Integer>) request.get("projectIds");
            
            if (newBranchName == null || newBranchName.trim().isEmpty()) {
                return Result.error("新分支名称不能为空");
            }
            if (sourceBranch == null || sourceBranch.trim().isEmpty()) {
                return Result.error("来源分支不能为空");
            }
            if (sourceVersion == null || sourceVersion.trim().isEmpty()) {
                return Result.error("来源分支版本号不能为空");
            }
            if (projectIds == null || projectIds.isEmpty()) {
                return Result.error("请至少选择一个Git工程");
            }
            
            List<Map<String, String>> results = gitService.createBranches(
                newBranchName.trim(), 
                sourceBranch.trim(), 
                projectIds,
                sourceVersion.trim(),
                newVersion != null ? newVersion.trim() : null
            );
            return Result.success(results);
        } catch (Exception e) {
            log.error("创建分支失败", e);
            return Result.error("创建分支失败: " + e.getMessage());
        }
    }
    
    /**
     * 邀请用户到项目（授予权限）
     */
    @PostMapping("/permissions/invite")
    public Result<List<Map<String, Object>>> inviteUsers(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> users = (List<String>) request.get("users");
            Integer accessLevel = (Integer) request.get("accessLevel");
            @SuppressWarnings("unchecked")
            List<Integer> projectIds = (List<Integer>) request.get("projectIds");
            
            if (users == null || users.isEmpty()) {
                return Result.error("用户列表不能为空");
            }
            if (accessLevel == null) {
                return Result.error("权限级别不能为空");
            }
            if (projectIds == null || projectIds.isEmpty()) {
                return Result.error("请至少选择一个Git工程");
            }
            
            List<Map<String, Object>> results = gitService.inviteUsers(users, accessLevel, projectIds);
            return Result.success(results);
        } catch (Exception e) {
            log.error("邀请用户失败", e);
            return Result.error("邀请用户失败: " + e.getMessage());
        }
    }
    
    /**
     * 收回用户权限
     */
    @PostMapping("/permissions/revoke")
    public Result<List<Map<String, Object>>> revokeUsers(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> users = (List<String>) request.get("users");
            @SuppressWarnings("unchecked")
            List<Integer> projectIds = (List<Integer>) request.get("projectIds");
            
            if (users == null || users.isEmpty()) {
                return Result.error("用户列表不能为空");
            }
            if (projectIds == null || projectIds.isEmpty()) {
                return Result.error("请至少选择一个Git工程");
            }
            
            List<Map<String, Object>> results = gitService.revokeUsers(users, projectIds);
            return Result.success(results);
        } catch (Exception e) {
            log.error("收回权限失败", e);
            return Result.error("收回权限失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新指定分支的pom.xml版本号
     */
    @PostMapping("/versions/update")
    public Result<List<Map<String, String>>> updateVersions(@RequestBody Map<String, Object> request) {
        try {
            String sourceBranch = (String) request.get("sourceBranch");
            String sourceVersion = (String) request.get("sourceVersion");
            String newVersion = (String) request.get("newVersion");
            @SuppressWarnings("unchecked")
            List<Integer> projectIds = (List<Integer>) request.get("projectIds");
            
            if (sourceBranch == null || sourceBranch.trim().isEmpty()) {
                return Result.error("来源分支不能为空");
            }
            if (sourceVersion == null || sourceVersion.trim().isEmpty()) {
                return Result.error("来源分支版本号不能为空");
            }
            if (newVersion == null || newVersion.trim().isEmpty()) {
                return Result.error("新版本号不能为空");
            }
            if (projectIds == null || projectIds.isEmpty()) {
                return Result.error("请至少选择一个Git工程");
            }
            
            List<Map<String, String>> results = gitService.updateVersions(
                sourceBranch.trim(),
                sourceVersion.trim(),
                newVersion.trim(),
                projectIds
            );
            return Result.success(results);
        } catch (Exception e) {
            log.error("更新版本号失败", e);
            return Result.error("更新版本号失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除分支
     */
    @PostMapping("/branches/delete")
    public Result<List<Map<String, String>>> deleteBranches(@RequestBody Map<String, Object> request) {
        try {
            String branchName = (String) request.get("branchName");
            @SuppressWarnings("unchecked")
            List<Integer> projectIds = (List<Integer>) request.get("projectIds");
            
            if (branchName == null || branchName.trim().isEmpty()) {
                return Result.error("分支名称不能为空");
            }
            if (projectIds == null || projectIds.isEmpty()) {
                return Result.error("请至少选择一个Git工程");
            }
            
            List<Map<String, String>> results = gitService.deleteBranches(
                branchName.trim(),
                projectIds
            );
            return Result.success(results);
        } catch (Exception e) {
            log.error("删除分支失败", e);
            return Result.error("删除分支失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新保护分支设置
     */
    @PostMapping("/protected-branches/update")
    public Result<List<Map<String, String>>> updateProtectedBranch(@RequestBody Map<String, Object> request) {
        try {
            String branchName = (String) request.get("branchName");
            Integer allowedToMerge = (Integer) request.get("allowedToMerge");
            Integer allowedToPush = (Integer) request.get("allowedToPush");
            Boolean allowForcePush = (Boolean) request.get("allowForcePush");
            @SuppressWarnings("unchecked")
            List<Integer> projectIds = (List<Integer>) request.get("projectIds");
            
            if (branchName == null || branchName.trim().isEmpty()) {
                return Result.error("分支名称不能为空");
            }
            if (allowedToMerge == null) {
                return Result.error("允许合并权限不能为空");
            }
            if (allowedToPush == null) {
                return Result.error("允许推送权限不能为空");
            }
            if (allowForcePush == null) {
                allowForcePush = false; // 默认不允许强制推送
            }
            if (projectIds == null || projectIds.isEmpty()) {
                return Result.error("请至少选择一个Git工程");
            }
            
            List<Map<String, String>> results = gitService.updateProtectedBranch(
                branchName.trim(),
                allowedToMerge,
                allowedToPush,
                allowForcePush,
                projectIds
            );
            return Result.success(results);
        } catch (Exception e) {
            log.error("更新保护分支设置失败", e);
            return Result.error("更新保护分支设置失败: " + e.getMessage());
        }
    }
    
    /**
     * 一键代码推送
     */
    @PostMapping("/code-push")
    public Result<List<Map<String, Object>>> pushCode(@RequestBody Map<String, Object> request) {
        try {
            String sourceFolder = (String) request.get("sourceFolder");
            String commitMessage = (String) request.get("commitMessage");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) request.get("tasks");

            if (sourceFolder == null || sourceFolder.trim().isEmpty()) {
                return Result.error("来源文件夹路径不能为空");
            }
            if (commitMessage == null || commitMessage.trim().isEmpty()) {
                return Result.error("提交信息不能为空");
            }
            if (tasks == null || tasks.isEmpty()) {
                return Result.error("推送任务列表不能为空");
            }

            List<Map<String, Object>> results = gitService.pushCode(
                sourceFolder.trim(),
                commitMessage.trim(),
                tasks
            );
            return Result.success(results);
        } catch (Exception e) {
            log.error("一键代码推送失败", e);
            return Result.error("一键代码推送失败: " + e.getMessage());
        }
    }

    /**
     * 批量克隆Git项目
     */
    @PostMapping("/clone")
    public Result<List<Map<String, String>>> cloneProjects(@RequestBody Map<String, Object> request) {
        try {
            String branchName = (String) request.get("branchName");
            String cloneDirectory = (String) request.get("cloneDirectory");
            @SuppressWarnings("unchecked")
            List<Integer> projectIds = (List<Integer>) request.get("projectIds");
            
            if (branchName == null || branchName.trim().isEmpty()) {
                return Result.error("分支名称不能为空");
            }
            if (cloneDirectory == null || cloneDirectory.trim().isEmpty()) {
                return Result.error("克隆目录不能为空");
            }
            if (projectIds == null || projectIds.isEmpty()) {
                return Result.error("请至少选择一个Git工程");
            }
            
            List<Map<String, String>> results = gitService.cloneProjects(
                branchName.trim(),
                cloneDirectory.trim(),
                projectIds
            );
            return Result.success(results);
        } catch (Exception e) {
            log.error("批量克隆项目失败", e);
            return Result.error("批量克隆项目失败: " + e.getMessage());
        }
    }
}

