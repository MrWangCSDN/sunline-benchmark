package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.CallRelation;
import com.sunline.dict.entity.Component;
import com.sunline.dict.entity.ComponentDetail;
import com.sunline.dict.entity.ServiceDetail;
import com.sunline.dict.entity.ServiceFile;
import com.sunline.dict.entity.ServiceImplFile;
import com.sunline.dict.mapper.CallRelationMapper;
import com.sunline.dict.mapper.ComponentDetailMapper;
import com.sunline.dict.mapper.ComponentMapper;
import com.sunline.dict.mapper.ServiceDetailMapper;
import com.sunline.dict.mapper.ServiceFileMapper;
import com.sunline.dict.mapper.ServiceImplFileMapper;
import com.sunline.dict.service.CallRelationScanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CallRelationScanServiceImpl implements CallRelationScanService {

    private static final Logger log = LoggerFactory.getLogger(CallRelationScanServiceImpl.class);

    @Value("${code-sync.base-path:/home/cbs/code}")
    private String codePath;

    @Autowired private CallRelationMapper callRelationMapper;
    @Autowired private ServiceFileMapper serviceFileMapper;
    @Autowired private ServiceDetailMapper serviceDetailMapper;
    @Autowired private ComponentMapper componentMapper;
    @Autowired private ComponentDetailMapper componentDetailMapper;
    @Autowired private ServiceImplFileMapper serviceImplFileMapper;

    /** 全量扫描进行中标志，扫描期间 Webhook 暂停处理 */
    private final AtomicBoolean scanning = new AtomicBoolean(false);

    @Override
    public boolean isScanning() {
        return scanning.get();
    }

    // ============================== 正则模式 ==============================

    private static final Pattern PAT_CHAIN = Pattern.compile(
            "SysUtil\\.getInstance\\(\\s*(\\w+)\\.class\\s*\\)\\.(\\w+)\\s*\\(");

    private static final Pattern PAT_VAR_DECL = Pattern.compile(
            "(\\w+)\\s+(\\w+)\\s*=\\s*SysUtil\\.getInstance\\(\\s*(\\w+)\\.class\\s*\\)");

    private static final Pattern PAT_VAR_ASSIGN = Pattern.compile(
            "(\\w+)\\s*=\\s*SysUtil\\.getInstance\\(\\s*(\\w+)\\.class\\s*\\)");

    private static final Pattern PAT_METHOD = Pattern.compile(
            "(public|private|protected|static|final|synchronized|\\s)+" +
            "(\\w+(?:<[^>]+>)?(?:\\[\\])?)\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*(?:throws\\s+[\\w,\\s]+)?\\s*\\{");

    /** KxxxxxDao.method( 模式（首字母 K，以 Dao 结尾）→ BCC 层 */
    private static final Pattern PAT_DAO_CALL = Pattern.compile(
            "(K\\w+Dao)\\.(\\w+)\\s*\\(");

    private static final Set<String> IMPL_SUFFIXES = Set.of(
            "PcsImpl", "PbsImpl", "PbcbImpl", "PbcpImpl", "PbccImpl", "PbctImpl");

    // ============================== 内部数据结构 ==============================

    static class MethodBlock {
        String name;
        boolean isPublic;
        int startLine;
        int endLine;
        String body;
        MethodBlock(String name, boolean isPublic, int startLine, int endLine, String body) {
            this.name = name; this.isPublic = isPublic; this.startLine = startLine;
            this.endLine = endLine; this.body = body;
        }
    }

    static class CallInfo {
        String calleeClass;
        String calleeMethod;
        boolean isDirect;
        boolean isDao;

        CallInfo(String calleeClass, String calleeMethod, boolean isDirect) {
            this(calleeClass, calleeMethod, isDirect, false);
        }
        CallInfo(String calleeClass, String calleeMethod, boolean isDirect, boolean isDao) {
            this.calleeClass = calleeClass; this.calleeMethod = calleeMethod;
            this.isDirect = isDirect; this.isDao = isDao;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CallInfo)) return false;
            CallInfo that = (CallInfo) o;
            return Objects.equals(calleeClass, that.calleeClass) && Objects.equals(calleeMethod, that.calleeMethod);
        }
        @Override public int hashCode() { return Objects.hash(calleeClass, calleeMethod); }
    }

    /** 节点信息缓存（detail 表去重 + 父表关联 type / packagePath） */
    static class NodeInfo {
        String type;
        String domain;
        String serviceId;
        String serviceName;
        String serviceLongname;
    }

    /** 节点缓存 Map：key = service_type_id 或 component_id → NodeInfo */
    private final Map<String, NodeInfo> allNodeMap = new ConcurrentHashMap<>();
    /** 实现类缓存 Map：implId（如 CfInfQryPbccImpl）→ serviceType（如 CfInfQryPbcc，对应 service/component 表的 id） */
    private final Map<String, String> implMap = new ConcurrentHashMap<>();

    /** 工具类注册表 */
    private final ConcurrentHashMap<String, Map<String, List<CallInfo>>> utilityRegistry = new ConcurrentHashMap<>();

    /** 文件名索引：className → Path（一次性构建，避免反复 Files.walk） */
    private final Map<String, Path> fileIndex = new ConcurrentHashMap<>();

    /** 方法体缓存：className → List<MethodBlock>（避免传递闭包中反复读文件+解析） */
    private final Map<String, List<MethodBlock>> parsedMethodsCache = new ConcurrentHashMap<>();

    // ============================== 缓存构建 ==============================

    /**
     * 重建全部缓存（全量扫描前调用）
     */
    private void buildAllCaches() {
        rebuildNodeMap();
        rebuildImplMap();
    }

    /**
     * 重建 allNodeMap：
     * - 数据主体来自 service_detail / component_detail（按 service_type_id / component_id 去重）
     * - 关联 service / component 父表获取 type（service_type / component_type）和 packagePath（推导 domain）
     */
    @Override
    public void rebuildNodeMap() {
        allNodeMap.clear();

        // 1. 加载 service 父表索引：id → ServiceFile（取 serviceType + fromJar）
        Map<String, ServiceFile> serviceIndex = new HashMap<>();
        for (ServiceFile s : serviceFileMapper.selectList(new QueryWrapper<>())) {
            if (s.getId() != null) serviceIndex.put(s.getId(), s);
        }

        // 2. service_detail 表：按 service_type_id 去重，关联父表
        List<ServiceDetail> allSd = serviceDetailMapper.selectList(new QueryWrapper<>());
        for (ServiceDetail sd : allSd) {
            String key = sd.getServiceTypeId();
            if (key == null || allNodeMap.containsKey(key)) continue;
            NodeInfo ni = new NodeInfo();
            ni.serviceId = sd.getServiceId();
            ni.serviceName = sd.getServiceName();
            ni.serviceLongname = sd.getServiceLongname();
            ServiceFile parent = serviceIndex.get(key);
            if (parent != null) {
                ni.type = parent.getServiceType();
                ni.domain = deriveDomainFromPackagePath(parent.getPackagePath());
            }
            allNodeMap.put(key, ni);
        }
        int serviceCount = allNodeMap.size();
        log.info("service_detail 缓存加载完成（去重后）：{} 条", serviceCount);

        // 3. 加载 component 父表索引：id → Component（取 componentType + packagePath）
        Map<String, Component> componentIndex = new HashMap<>();
        for (Component c : componentMapper.selectList(new QueryWrapper<>())) {
            if (c.getId() != null) componentIndex.put(c.getId(), c);
        }

        // 4. component_detail 表：按 component_id 去重，关联父表
        List<ComponentDetail> allCd = componentDetailMapper.selectList(new QueryWrapper<>());
        for (ComponentDetail cd : allCd) {
            String key = cd.getComponentId();
            if (key == null || allNodeMap.containsKey(key)) continue;
            NodeInfo ni = new NodeInfo();
            ni.serviceId = cd.getServiceId();
            ni.serviceName = cd.getServiceName();
            ni.serviceLongname = cd.getServiceLongname();
            Component parent = componentIndex.get(key);
            if (parent != null) {
                ni.type = parent.getComponentType();
                ni.domain = deriveDomainFromPackagePath(parent.getPackagePath());
            }
            allNodeMap.put(key, ni);
        }
        log.info("component_detail 缓存加载完成（去重后）：{} 条，节点缓存总计：{} 条",
                allNodeMap.size() - serviceCount, allNodeMap.size());
    }

    /**
     * 从 packagePath 推导领域。
     * 格式：ccbs.spdb.ccbs.{领域}.xxx.xxx — 取第 4 段（index=3）
     */
    private static final Set<String> KNOWN_DOMAINS = Set.of("comm", "dept", "sett", "loan");

    private String deriveDomainFromPackagePath(String packagePath) {
        if (packagePath == null) return null;
        String[] parts = packagePath.toLowerCase().split("\\.");
        if (parts.length >= 4) {
            String candidate = parts[3];
            if (KNOWN_DOMAINS.contains(candidate)) return candidate;
        }
        // 兜底：任意位置包含已知领域段
        for (String part : packagePath.toLowerCase().split("\\.")) {
            if (KNOWN_DOMAINS.contains(part)) return part;
        }
        return null;
    }

    /**
     * 重建 implMap（serviceImpl 表）。
     * Webhook 解析 *Impl.xml 后调用。
     */
    @Override
    public void rebuildImplMap() {
        implMap.clear();
        List<ServiceImplFile> impls = serviceImplFileMapper.selectList(new QueryWrapper<>());
        for (ServiceImplFile impl : impls) {
            if (impl.getId() != null && impl.getServiceType() != null) {
                implMap.put(impl.getId(), impl.getServiceType());
            }
        }
        log.info("实现类缓存加载完成：{} 条", implMap.size());
    }


    // ============================== 公开接口实现 ==============================

    @Override
    public Map<String, Object> fullScan() {
        if (!scanning.compareAndSet(false, true)) {
            log.warn("全量扫描已在进行中，本次请求忽略");
            return Map.of("error", "全量扫描正在执行中，请稍后再试");
        }

        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        try {
        log.info("========== 全量扫描开始（Webhook 已暂停）==========");

        // 0. 重建全部缓存（最先执行）
        buildAllCaches();

        // 0.5 一次性构建文件名索引（className → Path），后续所有查找复用
        buildFileIndex();

        // 1. 收集含 getInstance 或 KxxxDao 的 Java 文件
        List<Path> allTargetFiles = grepTargetFiles();
        log.info("匹配文件数：{}", allTargetFiles.size());

        // 2. 分为 Impl 文件和工具类文件
        List<Path> implFiles = new ArrayList<>();
        List<Path> utilFiles = new ArrayList<>();
        for (Path p : allTargetFiles) {
            if (isImplFile(p)) implFiles.add(p);
            else utilFiles.add(p);
        }
        log.info("Impl 骨架文件：{} 个，工具类文件：{} 个", implFiles.size(), utilFiles.size());

        // 3. 构建工具类注册表
        utilityRegistry.clear();
        parsedMethodsCache.clear();
        for (Path p : utilFiles) buildUtilityRegistryFromFile(p);
        log.info("工具类注册表条目数（初始）：{}", utilityRegistry.size());

        // 3.1 桥梁类
        List<Path> bridgeFiles = grepBridgeFiles();
        for (Path p : bridgeFiles) buildUtilityRegistryFromFile(p);
        log.info("桥梁类文件：{} 个，注册表总条目数：{}", bridgeFiles.size(), utilityRegistry.size());

        // 3.2 传递闭包
        resolveUtilityTransitiveClosure();

        // 4. 清空旧数据
        callRelationMapper.delete(new QueryWrapper<>());
        log.info("已清空 call_relation 表");

        // 5. 多线程扫描 Impl 文件
        List<CallRelation> allEdges = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(8, Runtime.getRuntime().availableProcessors()));
        List<Future<?>> futures = new ArrayList<>();
        for (Path implFile : implFiles) {
            futures.add(executor.submit(() -> {
                try {
                    List<CallRelation> edges = scanImplFile(implFile);
                    allEdges.addAll(edges);
                } catch (Exception e) {
                    log.error("扫描文件失败：{}", implFile, e);
                }
            }));
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) { log.error("扫描线程异常", e); }
        }
        executor.shutdown();
        log.info("Impl 文件扫描完成，共生成 {} 条边", allEdges.size());

        // 6. 批量写入
        batchInsert(allEdges);
        log.info("写入 call_relation 表完成，总边数：{}", allEdges.size());

        long cost = System.currentTimeMillis() - start;
        long violations = allEdges.stream().filter(e -> e.getRuleViolation() != null && e.getRuleViolation() == 1).count();
        result.put("totalFiles", allTargetFiles.size());
        result.put("implFiles", implFiles.size());
        result.put("utilFiles", utilFiles.size());
        result.put("totalEdges", allEdges.size());
        result.put("violations", violations);
        result.put("costMs", cost);
        log.info("========== 全量扫描完成，耗时 {}ms ==========", cost);
        return result;

        } finally {
            scanning.set(false);
            log.info("全量扫描标志已复位，Webhook 恢复接收");
        }
    }

    @Override
    public Map<String, Object> incrementalScan(List<String> changedFiles) {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();

        // 增量扫描也要刷新缓存
        buildAllCaches();
        buildFileIndex();

        List<Path> implToRescan = new ArrayList<>();
        Set<String> changedUtilClasses = new HashSet<>();

        for (String file : changedFiles) {
            if (!file.endsWith(".java")) continue;
            Path fullPath = resolveFilePath(file);
            if (fullPath == null || !Files.exists(fullPath)) continue;

            if (isImplFile(fullPath)) {
                implToRescan.add(fullPath);
            } else {
                try {
                    String content = Files.readString(fullPath, StandardCharsets.UTF_8);
                    if (content.contains("SysUtil.getInstance") || PAT_DAO_CALL.matcher(content).find()) {
                        String className = extractClassName(fullPath);
                        changedUtilClasses.add(className);
                        buildUtilityRegistryFromFile(fullPath);
                    }
                } catch (IOException e) {
                    log.warn("读取文件失败：{}", fullPath, e);
                }
            }
        }

        if (!changedUtilClasses.isEmpty()) {
            log.info("工具类变更：{}，查找受影响的 Impl 文件", changedUtilClasses);
            List<Path> affected = findImplFilesUsingUtilities(changedUtilClasses);
            for (Path p : affected) {
                if (!implToRescan.contains(p)) implToRescan.add(p);
            }
        }

        log.info("增量扫描：需重新扫描 {} 个 Impl 文件", implToRescan.size());

        int newEdges = 0;
        for (Path implFile : implToRescan) {
            try {
                String callerId = deriveCallerId(implFile);
                NodeInfo callerInfo = allNodeMap.get(callerId);
                if (callerId == null || callerInfo == null) continue;

                callRelationMapper.delete(new QueryWrapper<CallRelation>()
                        .eq("caller_id", callerId).eq("caller_type", callerInfo.type));

                List<CallRelation> edges = scanImplFile(implFile);
                batchInsert(edges);
                newEdges += edges.size();
            } catch (Exception e) {
                log.error("增量扫描文件失败：{}", implFile, e);
            }
        }

        long cost = System.currentTimeMillis() - start;
        result.put("rescanFiles", implToRescan.size());
        result.put("newEdges", newEdges);
        result.put("changedUtilClasses", changedUtilClasses);
        result.put("costMs", cost);
        log.info("增量扫描完成，耗时 {}ms", cost);
        return result;
    }

    @Override
    public Map<String, Object> queryImpact(String id, String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("callers", buildImpactTree(id, type, new HashSet<>(), 0));
        return node;
    }

    @Override
    public Map<String, Object> queryDependency(String id, String type) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("callees", buildDependencyTree(id, type, new HashSet<>(), 0));
        return node;
    }

    @Override
    public List<Map<String, Object>> queryViolations() {
        return callRelationMapper.findViolations().stream().map(this::relationToMap).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> querySummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalEdges", callRelationMapper.selectCount(new QueryWrapper<>()));
        summary.put("violations", callRelationMapper.selectCount(new QueryWrapper<CallRelation>().eq("rule_violation", 1)));
        summary.put("crossDomainCalls", callRelationMapper.selectCount(new QueryWrapper<CallRelation>().eq("cross_domain", 1)));
        return summary;
    }

    // ============================== 核心扫描逻辑 ==============================

    private List<CallRelation> scanImplFile(Path implFile) {
        List<CallRelation> edges = new ArrayList<>();

        String content;
        try {
            content = Files.readString(implFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("读取文件失败：{}", implFile, e);
            return edges;
        }

        String callerId = deriveCallerId(implFile);
        NodeInfo callerInfo = allNodeMap.get(callerId);
        if (callerId == null || callerInfo == null) {
            log.debug("跳过文件（节点缓存中无此 ID）：{} → {}", callerId, implFile.getFileName());
            return edges;
        }

        String callerType = callerInfo.type;
        String callerLongname = callerInfo.serviceLongname;
        String callerDomain = callerInfo.domain;
        String callerServiceId = callerInfo.serviceId;
        String fromJar = deriveFromJar(implFile);

        // 1. 拆分方法块
        List<MethodBlock> methods = parseMethods(content);

        // 2. 提取变量→类名映射
        Map<String, String> varMapping = extractVarMapping(content);

        // 3. 对每个方法提取 getInstance + DAO 调用
        Map<String, List<CallInfo>> methodDirectCalls = new LinkedHashMap<>();
        for (MethodBlock m : methods) {
            List<CallInfo> calls = extractCallsFromMethodBody(m.body, varMapping);
            methodDirectCalls.put(m.name, calls);
        }

        // 4. 类内方法调用关系
        Set<String> methodNames = methods.stream().map(m -> m.name).collect(Collectors.toSet());
        Map<String, Set<String>> internalCalls = new LinkedHashMap<>();
        for (MethodBlock m : methods) {
            internalCalls.put(m.name, findInternalCalls(m.body, methodNames, m.name));
        }

        // 5. 工具类调用
        Map<String, List<CallInfo>> methodUtilCalls = new LinkedHashMap<>();
        for (MethodBlock m : methods) {
            methodUtilCalls.put(m.name, findUtilityCalls(m.body));
        }

        // 6. 冒泡归集
        for (MethodBlock m : methods) {
            if (!m.isPublic) continue;

            Set<String> visited = new HashSet<>();
            List<CallInfo> allCalls = collectTransitive(
                    m.name, methodDirectCalls, internalCalls, methodUtilCalls, visited, 0);

            Set<CallInfo> unique = new LinkedHashSet<>(allCalls);
            for (CallInfo call : unique) {
                CallRelation edge = buildEdge(
                        callerId, callerType, m.name, callerLongname, callerDomain, callerServiceId,
                        call, fromJar);
                if (edge != null) edges.add(edge);
            }
        }

        return edges;
    }

    private static final int MAX_BUBBLE_DEPTH = 5;

    private List<CallInfo> collectTransitive(
            String methodName,
            Map<String, List<CallInfo>> directCalls,
            Map<String, Set<String>> internalCalls,
            Map<String, List<CallInfo>> utilCalls,
            Set<String> visited,
            int depth) {

        if (depth >= MAX_BUBBLE_DEPTH) return List.of();
        if (!visited.add(methodName)) return List.of();

        List<CallInfo> result = new ArrayList<>();

        // 收集直接 getInstance / DAO 调用
        for (CallInfo ci : directCalls.getOrDefault(methodName, List.of())) {
            // 如果被调用类是已知的 Impl 实现类或 service/component，停止向上归集该分支
            if (implMap.containsKey(ci.calleeClass) || allNodeMap.containsKey(ci.calleeClass)) {
                result.add(ci);
            } else {
                result.add(ci);
            }
        }

        // 收集工具类间接调用
        for (CallInfo ci : utilCalls.getOrDefault(methodName, List.of())) {
            if (implMap.containsKey(ci.calleeClass) || allNodeMap.containsKey(ci.calleeClass)) {
                result.add(ci);
            } else {
                result.add(ci);
            }
        }

        // 递归展开类内调用（向上冒泡），遇到 Impl/服务/构件边界则不再深入
        for (String internal : internalCalls.getOrDefault(methodName, Set.of())) {
            List<CallInfo> bubbled = collectTransitive(
                    internal, directCalls, internalCalls, utilCalls, visited, depth + 1);
            for (CallInfo c : bubbled) {
                result.add(new CallInfo(c.calleeClass, c.calleeMethod, false, c.isDao));
            }
        }
        return result;
    }

    // ============================== 方法解析 ==============================

    private List<MethodBlock> parseMethods(String content) {
        List<MethodBlock> methods = new ArrayList<>();
        Matcher matcher = PAT_METHOD.matcher(content);
        while (matcher.find()) {
            String modifiers = matcher.group(0);
            String methodName = matcher.group(3);
            boolean isPublic = modifiers.contains("public");
            int braceStart = content.indexOf('{', matcher.start());
            if (braceStart < 0) continue;
            int endPos = findMatchingBrace(content, braceStart);
            if (endPos < 0) continue;
            int startLine = countLines(content, matcher.start());
            int endLine = countLines(content, endPos);
            String body = content.substring(braceStart + 1, endPos);
            methods.add(new MethodBlock(methodName, isPublic, startLine, endLine, body));
        }
        return methods;
    }

    private int countLines(String content, int charPos) {
        int count = 1;
        for (int i = 0; i < charPos && i < content.length(); i++) {
            if (content.charAt(i) == '\n') count++;
        }
        return count;
    }

    private int findMatchingBrace(String content, int openPos) {
        int depth = 0;
        boolean inString = false, inChar = false, inLineComment = false, inBlockComment = false;
        for (int i = openPos; i < content.length(); i++) {
            char c = content.charAt(i);
            char next = (i + 1 < content.length()) ? content.charAt(i + 1) : 0;
            if (inLineComment) { if (c == '\n') inLineComment = false; continue; }
            if (inBlockComment) { if (c == '*' && next == '/') { inBlockComment = false; i++; } continue; }
            if (c == '/' && next == '/') { inLineComment = true; i++; continue; }
            if (c == '/' && next == '*') { inBlockComment = true; i++; continue; }
            if (inString) { if (c == '\\') { i++; continue; } if (c == '"') inString = false; continue; }
            if (inChar) { if (c == '\\') { i++; continue; } if (c == '\'') inChar = false; continue; }
            if (c == '"') { inString = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '{') depth++;
            if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    // ============================== 调用提取 ==============================

    private Map<String, String> extractVarMapping(String content) {
        Map<String, String> mapping = new LinkedHashMap<>();
        Matcher m1 = PAT_VAR_DECL.matcher(content);
        while (m1.find()) mapping.put(m1.group(2), m1.group(3));
        Matcher m2 = PAT_VAR_ASSIGN.matcher(content);
        while (m2.find()) mapping.putIfAbsent(m2.group(1), m2.group(2));
        return mapping;
    }

    private List<CallInfo> extractCallsFromMethodBody(String body, Map<String, String> varMapping) {
        List<CallInfo> calls = new ArrayList<>();

        // getInstance 链式调用
        Matcher mc = PAT_CHAIN.matcher(body);
        while (mc.find()) calls.add(new CallInfo(mc.group(1), mc.group(2), true));

        // getInstance 变量方式
        for (Map.Entry<String, String> entry : varMapping.entrySet()) {
            String varName = entry.getKey();
            String className = entry.getValue();
            Pattern p = Pattern.compile("\\b" + Pattern.quote(varName) + "\\.(\\w+)\\s*\\(");
            Matcher mv = p.matcher(body);
            while (mv.find()) {
                String method = mv.group(1);
                if (!"toString".equals(method) && !"hashCode".equals(method) && !"equals".equals(method)) {
                    calls.add(new CallInfo(className, method, true));
                }
            }
        }

        // KxxxDao.method() → BCC
        Matcher md = PAT_DAO_CALL.matcher(body);
        while (md.find()) {
            calls.add(new CallInfo(md.group(1), md.group(2), true, true));
        }

        return calls;
    }

    private Set<String> findInternalCalls(String body, Set<String> allMethodNames, String selfName) {
        Set<String> calls = new LinkedHashSet<>();
        for (String name : allMethodNames) {
            if (name.equals(selfName)) continue;
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(").matcher(body).find()) {
                calls.add(name);
            }
        }
        return calls;
    }

    private List<CallInfo> findUtilityCalls(String body) {
        List<CallInfo> calls = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<CallInfo>>> classEntry : utilityRegistry.entrySet()) {
            String className = classEntry.getKey();
            // 快速预筛：如果 body 中不包含 "ClassName."，直接跳过整个类的所有方法
            if (!body.contains(className + ".")) continue;

            Map<String, List<CallInfo>> methodMap = classEntry.getValue();
            List<Map.Entry<String, List<CallInfo>>> snapshot = new ArrayList<>(methodMap.entrySet());
            for (Map.Entry<String, List<CallInfo>> methodEntry : snapshot) {
                String methodName = methodEntry.getKey();
                // 预筛：body 中不包含方法名也跳过
                if (!body.contains(methodName + "(") && !body.contains(methodName + " (")) continue;
                Pattern p = Pattern.compile("\\b" + Pattern.quote(className) + "\\." +
                        Pattern.quote(methodName) + "\\s*\\(");
                if (p.matcher(body).find()) {
                    for (CallInfo ci : methodEntry.getValue()) {
                        calls.add(new CallInfo(ci.calleeClass, ci.calleeMethod, false, ci.isDao));
                    }
                }
            }
        }
        return calls;
    }

    // ============================== 工具类注册表 ==============================

    private void buildUtilityRegistryFromFile(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String className = extractClassName(file);
            if (className == null) return;

            List<MethodBlock> methods = parseMethods(content);
            Map<String, String> varMapping = extractVarMapping(content);
            Map<String, List<CallInfo>> methodCalls = new LinkedHashMap<>();

            for (MethodBlock m : methods) {
                List<CallInfo> calls = extractCallsFromMethodBody(m.body, varMapping);
                if (!calls.isEmpty()) methodCalls.put(m.name, calls);
            }

            if (!methodCalls.isEmpty()) {
                utilityRegistry.put(className, methodCalls);
            } else {
                utilityRegistry.putIfAbsent(className, new LinkedHashMap<>());
            }
        } catch (IOException e) {
            log.warn("解析工具类文件失败：{}", file, e);
        }
    }

    private List<Path> grepBridgeFiles() {
        Set<String> registered = utilityRegistry.keySet();
        if (registered.isEmpty()) return List.of();
        // 预生成 "ClassName." 列表用于快速匹配
        List<String> dotSuffixes = registered.stream().map(cn -> cn + ".").collect(Collectors.toList());
        List<Path> result = new ArrayList<>();
        for (Map.Entry<String, Path> entry : fileIndex.entrySet()) {
            String cn = entry.getKey();
            Path p = entry.getValue();
            if (isImplFile(p)) continue;
            if (utilityRegistry.containsKey(cn)) continue;
            try {
                String c = Files.readString(p, StandardCharsets.UTF_8);
                for (String ds : dotSuffixes) {
                    if (c.contains(ds)) { result.add(p); break; }
                }
            } catch (IOException ignored) {}
        }
        return result;
    }

    private List<MethodBlock> getCachedMethods(String className) {
        return parsedMethodsCache.computeIfAbsent(className, cn -> {
            Path file = findFileByClassName(cn);
            if (file == null) return List.of();
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                return parseMethods(content);
            } catch (IOException e) {
                return List.of();
            }
        });
    }

    private void resolveUtilityTransitiveClosure() {
        long tcStart = System.currentTimeMillis();

        for (int round = 0; round < MAX_BUBBLE_DEPTH; round++) {
            long roundStart = System.currentTimeMillis();
            boolean changed = false;
            int updatedClasses = 0;

            Map<String, Map<String, List<CallInfo>>> pendingUpdates = new LinkedHashMap<>();

            for (Map.Entry<String, Map<String, List<CallInfo>>> classEntry : utilityRegistry.entrySet()) {
                String className = classEntry.getKey();

                if (implMap.containsKey(className) || allNodeMap.containsKey(className)) continue;

                List<MethodBlock> methods = getCachedMethods(className);
                if (methods.isEmpty()) continue;

                Map<String, List<CallInfo>> currentMap = classEntry.getValue();
                Map<String, List<CallInfo>> updates = new LinkedHashMap<>();

                for (MethodBlock m : methods) {
                    List<CallInfo> existing = new ArrayList<>(currentMap.getOrDefault(m.name, List.of()));
                    boolean hasNew = false;
                    for (CallInfo ci : findUtilityCalls(m.body)) {
                        if (!existing.contains(ci)) {
                            existing.add(new CallInfo(ci.calleeClass, ci.calleeMethod, false, ci.isDao));
                            hasNew = true;
                        }
                    }
                    if (hasNew) updates.put(m.name, existing);
                }

                if (!updates.isEmpty()) {
                    pendingUpdates.put(className, updates);
                    updatedClasses++;
                }
            }

            for (Map.Entry<String, Map<String, List<CallInfo>>> entry : pendingUpdates.entrySet()) {
                Map<String, List<CallInfo>> target = utilityRegistry.get(entry.getKey());
                if (target != null) {
                    target.putAll(entry.getValue());
                    changed = true;
                }
            }

            log.info("传递闭包第 {} 轮完成：更新 {} 个类，耗时 {} ms",
                    round + 1, updatedClasses, System.currentTimeMillis() - roundStart);
            if (!changed) { log.info("传递闭包在第 {} 轮收敛，总耗时 {} ms", round + 1, System.currentTimeMillis() - tcStart); break; }
        }
    }

    private void buildFileIndex() {
        fileIndex.clear();
        long t = System.currentTimeMillis();
        try (Stream<Path> walk = Files.walk(Path.of(codePath))) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("/test/"))
                .forEach(p -> {
                    String fn = p.getFileName().toString();
                    String cn = fn.substring(0, fn.length() - 5);
                    fileIndex.putIfAbsent(cn, p);
                });
        } catch (IOException e) {
            log.error("构建文件索引失败", e);
        }
        log.info("文件索引构建完成：{} 个 Java 类，耗时 {} ms", fileIndex.size(), System.currentTimeMillis() - t);
    }

    private Path findFileByClassName(String className) {
        return fileIndex.get(className);
    }

    // ============================== 文件查找 ==============================

    private List<Path> grepTargetFiles() {
        List<Path> result = new ArrayList<>();
        for (Path p : fileIndex.values()) {
            try {
                String c = Files.readString(p, StandardCharsets.UTF_8);
                if (c.contains("SysUtil.getInstance") || PAT_DAO_CALL.matcher(c).find()) {
                    result.add(p);
                }
            } catch (IOException ignored) {}
        }
        return result;
    }

    private boolean isImplFile(Path path) {
        String fileName = path.getFileName().toString().replace(".java", "");
        return IMPL_SUFFIXES.stream().anyMatch(fileName::endsWith);
    }

    private List<Path> findImplFilesUsingUtilities(Set<String> utilClassNames) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Path.of(codePath))) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(this::isImplFile)
                .filter(p -> {
                    try {
                        String c = Files.readString(p, StandardCharsets.UTF_8);
                        return utilClassNames.stream().anyMatch(cn -> c.contains(cn + "."));
                    } catch (IOException e) { return false; }
                })
                .forEach(result::add);
        } catch (IOException e) { log.error("查找受影响的 Impl 文件失败", e); }
        return result;
    }

    // ============================== 交易→服务 边 ==============================

    // ============================== 递归查询 ==============================

    private List<Map<String, Object>> buildImpactTree(String id, String type, Set<String> visited, int depth) {
        if (depth > 10 || !visited.add(id + ":" + type)) return List.of();
        List<CallRelation> callers = callRelationMapper.findCallers(id, type);
        List<Map<String, Object>> tree = new ArrayList<>();
        for (CallRelation cr : callers) {
            Map<String, Object> node = relationToMap(cr);
            node.put("upstreamCallers", buildImpactTree(cr.getCallerId(), cr.getCallerType(), visited, depth + 1));
            tree.add(node);
        }
        return tree;
    }

    private List<Map<String, Object>> buildDependencyTree(String id, String type, Set<String> visited, int depth) {
        if (depth > 10 || !visited.add(id + ":" + type)) return List.of();
        List<CallRelation> callees = callRelationMapper.findCallees(id, type);
        List<Map<String, Object>> tree = new ArrayList<>();
        for (CallRelation cr : callees) {
            Map<String, Object> node = relationToMap(cr);
            node.put("downstreamCallees", buildDependencyTree(cr.getCalleeId(), cr.getCalleeType(), visited, depth + 1));
            tree.add(node);
        }
        return tree;
    }

    // ============================== 构建边对象 ==============================

    private CallRelation buildEdge(String callerId, String callerType, String callerMethod,
                                   String callerLongname, String callerDomain, String callerServiceId,
                                   CallInfo call, String fromJar) {

        if (call.isDao) {
            // DAO 调用 → BCC
            CallRelation edge = new CallRelation();
            edge.setCallerId(callerId);
            edge.setCallerType(callerType);
            edge.setCallerMethod(callerMethod);
            edge.setCallerLongname(callerLongname);
            edge.setCallerDomain(callerDomain);
            edge.setCallerServiceId(callerServiceId);

            edge.setCalleeId(call.calleeClass);
            edge.setCalleeType("bcc");
            edge.setCalleeMethod(call.calleeMethod);
            edge.setCalleeLongname(null);
            edge.setCalleeDomain(null);
            edge.setCalleeClass(call.calleeClass);
            edge.setCalleeServiceId(null);
            edge.setCalleeServiceName(null);

            edge.setFromJar(fromJar);
            edge.setIsDirect(call.isDirect ? 1 : 0);
            edge.setCrossDomain(0);

            String violation = checkRuleViolation(callerType, "bcc", callerDomain, null);
            edge.setRuleViolation(violation != null ? 1 : 0);
            edge.setViolationDesc(violation);
            edge.setCreateTime(LocalDateTime.now());
            edge.setUpdateTime(LocalDateTime.now());
            return edge;
        }

        // getInstance 调用 → 从 allNodeMap 查类型
        NodeInfo calleeInfo = allNodeMap.get(call.calleeClass);
        if (calleeInfo == null) {
            log.debug("跳过 getInstance（缓存无此 ID）：{}.{}", call.calleeClass, call.calleeMethod);
            return null;
        }

        CallRelation edge = new CallRelation();
        edge.setCallerId(callerId);
        edge.setCallerType(callerType);
        edge.setCallerMethod(callerMethod);
        edge.setCallerLongname(callerLongname);
        edge.setCallerDomain(callerDomain);
        edge.setCallerServiceId(callerServiceId);

        edge.setCalleeId(call.calleeClass);
        edge.setCalleeType(calleeInfo.type);
        edge.setCalleeMethod(call.calleeMethod);
        edge.setCalleeLongname(calleeInfo.serviceLongname);
        edge.setCalleeDomain(calleeInfo.domain);
        edge.setCalleeClass(call.calleeClass);
        edge.setCalleeServiceId(calleeInfo.serviceId);
        edge.setCalleeServiceName(calleeInfo.serviceName);

        edge.setFromJar(fromJar);
        edge.setIsDirect(call.isDirect ? 1 : 0);

        boolean cross = callerDomain != null && calleeInfo.domain != null
                && !callerDomain.equals(calleeInfo.domain);
        edge.setCrossDomain(cross ? 1 : 0);

        String violation = checkRuleViolation(callerType, calleeInfo.type, callerDomain, calleeInfo.domain);
        edge.setRuleViolation(violation != null ? 1 : 0);
        edge.setViolationDesc(violation);
        edge.setCreateTime(LocalDateTime.now());
        edge.setUpdateTime(LocalDateTime.now());
        return edge;
    }

    // ============================== 规则校验 ==============================

    private String checkRuleViolation(String callerType, String calleeType,
                                      String callerDomain, String calleeDomain) {
        if (callerType == null || calleeType == null) return null;

        if (callerType.equals(calleeType)) {
            return "同层调用违规：" + callerType + " 不允许调用 " + calleeType;
        }
        if ("pcs".equals(callerType) && !"pbs".equals(calleeType)) {
            return "层级违规：pcs 只能调用 pbs，不允许直接调用 " + calleeType;
        }
        if ("pbs".equals(callerType) && "pcs".equals(calleeType)) {
            return "层级违规：pbs 不允许调用 pcs";
        }
        if ("pbs".equals(callerType)
                && ("pbcb".equals(calleeType) || "pbcp".equals(calleeType))
                && callerDomain != null && calleeDomain != null
                && !callerDomain.equals(calleeDomain)) {
            return "跨域违规：pbs(" + callerDomain + ") 不允许跨域调用 " + calleeType + "(" + calleeDomain + ")";
        }
        if (("pbcb".equals(callerType) || "pbcp".equals(callerType))
                && ("pbs".equals(calleeType) || "pcs".equals(calleeType)
                    || "pbcb".equals(calleeType) || "pbcp".equals(calleeType))) {
            return "层级违规：" + callerType + " 不允许调用 " + calleeType;
        }
        if ("pbcc".equals(callerType)
                && ("pbs".equals(calleeType) || "pcs".equals(calleeType)
                    || "pbcb".equals(calleeType) || "pbcp".equals(calleeType)
                    || "pbcc".equals(calleeType))) {
            return "层级违规：pbcc 不允许调用 " + calleeType;
        }
        if ("pbct".equals(callerType) && !"bcc".equals(calleeType)) {
            return "层级违规：pbct 只能调用 bcc，不允许调用 " + calleeType;
        }
        return null;
    }

    // ============================== 辅助方法 ==============================

    private String deriveCallerId(Path implFile) {
        String name = implFile.getFileName().toString().replace(".java", "");
        for (String suffix : IMPL_SUFFIXES) {
            if (name.endsWith(suffix)) return name.substring(0, name.length() - 4);
        }
        return name;
    }

    private String deriveFromJar(Path file) {
        String path = file.toString();
        int codeIdx = path.indexOf("/code/");
        if (codeIdx < 0) return null;
        String rest = path.substring(codeIdx + 6);
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash) : rest;
    }

    private String extractClassName(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".java") ? name.substring(0, name.length() - 5) : name;
    }

    private Path resolveFilePath(String relativePath) {
        Path resolved = Path.of(codePath).resolve(relativePath);
        if (Files.exists(resolved)) return resolved;
        try (Stream<Path> dirs = Files.list(Path.of(codePath))) {
            return dirs.filter(Files::isDirectory)
                    .map(d -> d.resolve(relativePath))
                    .filter(Files::exists).findFirst().orElse(null);
        } catch (IOException e) { return null; }
    }

    private Map<String, Object> relationToMap(CallRelation cr) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("callerId", cr.getCallerId());
        map.put("callerType", cr.getCallerType());
        map.put("callerMethod", cr.getCallerMethod());
        map.put("callerLongname", cr.getCallerLongname());
        map.put("callerDomain", cr.getCallerDomain());
        map.put("callerServiceId", cr.getCallerServiceId());
        map.put("calleeId", cr.getCalleeId());
        map.put("calleeType", cr.getCalleeType());
        map.put("calleeMethod", cr.getCalleeMethod());
        map.put("calleeLongname", cr.getCalleeLongname());
        map.put("calleeDomain", cr.getCalleeDomain());
        map.put("calleeClass", cr.getCalleeClass());
        map.put("calleeServiceId", cr.getCalleeServiceId());
        map.put("calleeServiceName", cr.getCalleeServiceName());
        map.put("fromJar", cr.getFromJar());
        map.put("isDirect", cr.getIsDirect());
        map.put("crossDomain", cr.getCrossDomain());
        map.put("ruleViolation", cr.getRuleViolation());
        map.put("violationDesc", cr.getViolationDesc());
        return map;
    }

    private void batchInsert(List<CallRelation> edges) {
        if (edges.isEmpty()) return;
        for (int i = 0; i < edges.size(); i += 500) {
            List<CallRelation> batch = edges.subList(i, Math.min(i + 500, edges.size()));
            for (CallRelation edge : batch) {
                try {
                    callRelationMapper.insert(edge);
                } catch (Exception e) {
                    log.debug("插入跳过（可能重复）：{}.{} → {}.{}",
                            edge.getCallerId(), edge.getCallerMethod(),
                            edge.getCalleeId(), edge.getCalleeMethod());
                }
            }
        }
    }
}
