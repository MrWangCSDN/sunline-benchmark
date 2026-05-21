package com.sunline.dict.service;

import java.util.List;
import java.util.Map;

/**
 * 调用关系扫描服务
 *
 * <p>职责：扫描 Java 源码中的 SysUtil.getInstance() 和 KxxxDao 调用，
 * 构建服务/构件级别的调用关系图。
 *
 * <p>扫描范围：
 * <ol>
 *   <li>所有 *Impl.java 骨架实现类（PcsImpl/PbsImpl/PbcbImpl/PbcpImpl/PbccImpl/PbctImpl）</li>
 *   <li>所有含 SysUtil.getInstance 或 KxxxDao 的非 Impl 工具类（工具类注册表）</li>
 * </ol>
 */
public interface CallRelationScanService {

    /**
     * 全量扫描：清空 call_relation 表，重新扫描所有源码并重建全部边
     */
    Map<String, Object> fullScan();

    /**
     * 增量扫描：根据变更文件列表，只更新受影响的边
     */
    Map<String, Object> incrementalScan(List<String> changedFiles);

    /**
     * 重建 allNodeMap 缓存（直接从 service_detail + component_detail 表加载，按 service_type_id / component_id 去重）。
     * 当 service/component 对应的 XML 变更时由 Webhook 调用。
     */
    void rebuildNodeMap();

    /**
     * 重建 implMap 缓存（serviceImpl 表）。
     * 当 serviceImpl 对应的 XML 变更时由 Webhook 调用。
     */
    void rebuildImplMap();

    Map<String, Object> queryImpact(String id, String type);

    Map<String, Object> queryDependency(String id, String type);

    List<Map<String, Object>> queryViolations();

    Map<String, Object> querySummary();

    /**
     * 当前是否正在执行全量扫描。
     * Webhook 在此期间应暂停处理，避免并发冲突。
     */
    boolean isScanning();
}
