package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.CallRelationScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 调用关系图谱接口
 *
 * <p>接口列表：
 * <ul>
 *   <li>POST /api/relation/scan          - 触发全量扫描（重建全部边）</li>
 *   <li>POST /api/relation/scan/incr     - 增量扫描（传入变更文件列表）</li>
     *   <li>GET  /api/relation/impact        - 影响面查询（向上递归：谁调了我，type 小写如 pbs/pbcb）</li>
     *   <li>GET  /api/relation/dependency    - 依赖链查询（向下递归：我调了谁，type 小写如 pbs/pbcb）</li>
     *   <li>GET  /api/relation/violations    - 违规调用列表</li>
     *   <li>GET  /api/relation/summary       - 统计概览</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/relation")
public class CallRelationController {

    private static final Logger log = LoggerFactory.getLogger(CallRelationController.class);

    @Autowired
    private CallRelationScanService scanService;

    /**
     * 全量扫描：清空并重建全部调用关系
     */
    @PostMapping("/scan")
    public Result<Map<String, Object>> fullScan() {
        log.info("触发全量关系扫描");
        Map<String, Object> result = scanService.fullScan();
        return Result.success(result);
    }

    /**
     * 增量扫描：根据变更文件列表更新
     *
     * @param body 包含 changedFiles 字段，值为变更文件路径列表
     */
    @PostMapping("/scan/incr")
    public Result<Map<String, Object>> incrementalScan(@RequestBody Map<String, List<String>> body) {
        List<String> files = body.get("changedFiles");
        if (files == null || files.isEmpty()) {
            return Result.error("changedFiles 不能为空");
        }
        log.info("触发增量关系扫描，变更文件数：{}", files.size());
        Map<String, Object> result = scanService.incrementalScan(files);
        return Result.success(result);
    }

    /**
     * 影响面查询：给定节点，递归查所有调用方
     *
     * <p>示例：GET /api/relation/impact?id=AcctPbcb&type=PBCB
     * → 查出谁调了 AcctPbcb，以及间接的上游交易
     *
     * @param id   节点 ID
     * @param type 节点类型：pbf/pcs/pbs/pbcb/pbcp/pbcc/pbct/bcc（小写）
     */
    @GetMapping("/impact")
    public Result<Map<String, Object>> queryImpact(
            @RequestParam String id,
            @RequestParam String type) {
        Map<String, Object> result = scanService.queryImpact(id, type.toLowerCase());
        return Result.success(result);
    }

    /**
     * 依赖链查询：给定节点，递归查所有被调用方
     *
     * <p>示例：GET /api/relation/dependency?id=LoanApplyPbs&type=PBS
     * → 查出 LoanApplyPbs 调了哪些构件、表
     *
     * @param id   节点 ID
     * @param type 节点类型
     */
    @GetMapping("/dependency")
    public Result<Map<String, Object>> queryDependency(
            @RequestParam String id,
            @RequestParam String type) {
        Map<String, Object> result = scanService.queryDependency(id, type.toLowerCase());
        return Result.success(result);
    }

    /**
     * 查询所有违规调用
     */
    @GetMapping("/violations")
    public Result<List<Map<String, Object>>> queryViolations() {
        return Result.success(scanService.queryViolations());
    }

    /**
     * 统计概览
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> querySummary() {
        return Result.success(scanService.querySummary());
    }
}
