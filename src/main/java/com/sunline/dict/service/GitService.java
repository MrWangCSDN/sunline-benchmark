package com.sunline.dict.service;

import java.util.List;
import java.util.Map;

/**
 * Git操作服务接口
 */
public interface GitService {
    
    /**
     * 获取Git工程列表
     */
    List<Map<String, Object>> getProjects();
    
    /**
     * 获取分支列表
     */
    List<String> getBranches();
    
    /**
     * 创建分支
     * @param newBranchName 新分支名称
     * @param sourceBranch 来源分支
     * @param projectIds 工程ID列表
     * @param sourceVersion 来源分支版本号
     * @param newVersion 新分支版本号（可为空，为空则不替换）
     * @return 创建结果列表，每个元素包含project和message
     */
    List<Map<String, String>> createBranches(String newBranchName, String sourceBranch, List<Integer> projectIds, 
                                             String sourceVersion, String newVersion);
    
    /**
     * 邀请用户到项目（授予权限）
     * @param users 用户列表（用户名或邮箱）
     * @param accessLevel 权限级别：10=Guest, 20=Reporter, 30=Developer, 40=Maintainer
     * @param projectIds 项目ID列表
     * @return 操作结果列表，每个元素包含project、user、success和message
     */
    List<Map<String, Object>> inviteUsers(List<String> users, int accessLevel, List<Integer> projectIds);
    
    /**
     * 收回用户权限
     * @param users 用户列表（用户名或邮箱）
     * @param projectIds 项目ID列表
     * @return 操作结果列表，每个元素包含project、user、success和message
     */
    List<Map<String, Object>> revokeUsers(List<String> users, List<Integer> projectIds);
    
    /**
     * 更新指定分支的pom.xml版本号
     * @param sourceBranch 目标分支名称
     * @param sourceVersion 来源分支版本号
     * @param newVersion 新版本号
     * @param projectIds 工程ID列表
     * @return 更新结果列表，每个元素包含project和message
     */
    List<Map<String, String>> updateVersions(String sourceBranch, String sourceVersion, String newVersion, List<Integer> projectIds);
    
    /**
     * 删除分支
     * @param branchName 要删除的分支名称
     * @param projectIds 工程ID列表
     * @return 删除结果列表，每个元素包含project和message
     */
    List<Map<String, String>> deleteBranches(String branchName, List<Integer> projectIds);
    
    /**
     * 更新保护分支设置
     * @param branchName 分支名称
     * @param allowedToMerge 允许合并的权限级别：0=No one, 30=Developer, 40=Maintainer
     * @param allowedToPush 允许推送的权限级别：0=No one, 30=Developer, 40=Maintainer
     * @param allowForcePush 是否允许强制推送
     * @param projectIds 工程ID列表
     * @return 操作结果列表，每个元素包含project和message
     */
    List<Map<String, String>> updateProtectedBranch(String branchName, int allowedToMerge, 
                                                    int allowedToPush, boolean allowForcePush, 
                                                    List<Integer> projectIds);
    
    /**
     * 批量克隆Git项目到指定目录
     * @param branchName 要克隆的分支名称
     * @param cloneDirectory 克隆到的本地目录
     * @param projectIds 工程ID列表
     * @return 克隆结果列表，每个元素包含project和message
     */
    List<Map<String, String>> cloneProjects(String branchName, String cloneDirectory, List<Integer> projectIds);

    /**
     * 一键代码推送：将本地文件夹内容强制覆盖推送到多个Git工程的指定分支
     * @param sourceFolder 本地来源文件夹绝对路径
     * @param commitMessage Git提交信息
     * @param tasks 推送任务列表，每项包含 projectId 和 branch
     * @return 推送结果列表，每项包含 project、branch、success、message
     */
    List<Map<String, Object>> pushCode(String sourceFolder, String commitMessage, List<Map<String, Object>> tasks);
}

