package com.sunline.dict.service;

import java.util.List;
import java.util.Map;

/**
 * 代码同步服务接口
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #syncCode} - 按 GitLab Push payload 同步单个工程</li>
 *   <li>{@link #pullProject} - 按本地目录名做 git pull（不依赖 webhook payload）</li>
 *   <li>{@link #pullProjects} - 批量拉取多个工程，用于编译前兜底</li>
 * </ul>
 */
public interface CodeSyncService {

    /**
     * 处理 GitLab Push 事件，同步 master 分支代码到本地
     *
     * @param payload GitLab Webhook Push 事件 payload
     * @return 同步结果
     */
    Map<String, Object> syncCode(Map<String, Object> payload) throws Exception;

    /**
     * 对单个工程执行 git pull（强制重置到 origin/master），不依赖 webhook payload。
     * 适用于编译前的兜底拉取。
     *
     * @param projectName 工程目录名（相对于 code-sync.base-path），如 ccbs-dict
     * @return 拉取结果：status + message + output + errorType/errorMessage/errorStack
     */
    Map<String, Object> pullProject(String projectName);

    /**
     * 批量拉取多个工程，按顺序串行执行。
     * 任意工程拉取失败时记录错误但不中断后续工程。
     *
     * @param projectNames 工程目录名列表
     * @return 每个工程的拉取结果列表
     */
    List<Map<String, Object>> pullProjects(List<String> projectNames);

    /**
     * 全量 clone：将 application.yml build.batches 中配置的所有工程从 GitLab clone 到本地。
     * <ul>
     *   <li>目录已存在且是 git 仓库 → 强制 pull（fetch + reset --hard origin/master）</li>
     *   <li>目录不存在 → git clone -b master --single-branch</li>
     * </ul>
     * 通过 GitLab API 按工程名搜索获取仓库地址，无需手动配置每个工程的 URL。
     *
     * @return 每个工程的 clone/pull 结果列表，包含 project / status / message / output
     */
    List<Map<String, Object>> cloneAllBatchProjects();
}
