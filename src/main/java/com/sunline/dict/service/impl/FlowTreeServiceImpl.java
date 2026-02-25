package com.sunline.dict.service.impl;

import com.sunline.dict.entity.FlowStep;
import com.sunline.dict.entity.Flowtran;
import com.sunline.dict.entity.HardCodeMethodStack;
import com.sunline.dict.entity.ServiceTypeFile;
import com.sunline.dict.entity.ServiceTypeImplFile;
import com.sunline.dict.mapper.FlowStepMapper;
import com.sunline.dict.mapper.FlowtranMapper;
import com.sunline.dict.mapper.HardCodeMethodStackMapper;
import com.sunline.dict.mapper.ServiceTypeFileMapper;
import com.sunline.dict.mapper.ServiceTypeImplFileMapper;
import com.sunline.dict.service.FlowTreeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 交易树服务实现类
 */
@Service
public class FlowTreeServiceImpl implements FlowTreeService {
    
    private static final Logger log = LoggerFactory.getLogger(FlowTreeServiceImpl.class);
    
    @Autowired
    private FlowtranMapper flowtranMapper;
    
    @Autowired
    private FlowStepMapper flowStepMapper;
    
    @Autowired
    private ServiceTypeFileMapper serviceTypeFileMapper;
    
    @Autowired
    private ServiceTypeImplFileMapper serviceTypeImplFileMapper;
    
    @Autowired
    private HardCodeMethodStackMapper hardCodeMethodStackMapper;
    
    @Autowired
    private com.sunline.dict.service.LayerCallRuleCheckService layerCallRuleCheckService;
    
    // 缓存
    private Map<String, Flowtran> flowtranCache = new HashMap<>();
    private Map<String, List<FlowStep>> flowStepCache = new HashMap<>(); // key: flow_id
    private Map<String, List<ServiceTypeFile>> serviceTypeFileCache = new HashMap<>(); // key: service_type_id
    private Map<String, ServiceTypeImplFile> serviceTypeImplFileCache = new HashMap<>(); // key: service_type_id
    private Map<String, List<HardCodeMethodStack>> hardCodeMethodStackCache = new HashMap<>(); // key: service_type_id|service_type_impl_id|service_name
    
    @Override
    public Map<String, Object> buildFlowTree(String flowId) {
        log.info("开始构建交易树，flowId: {}", flowId);
        
        // 加载缓存
        loadCaches();
        
        // 构建树结构
        return buildTree(flowId);
    }
    
    @Override
    public List<Map<String, Object>> buildAllFlowTrees() {
        log.info("开始构建所有交易树");
        
        // 加载缓存
        loadCaches();
        
        // 获取所有flow_id
        List<String> flowIds = flowtranCache.keySet().stream()
            .sorted()
            .collect(Collectors.toList());
        
        log.info("共找到 {} 个交易", flowIds.size());
        
        // 构建所有交易树
        List<Map<String, Object>> result = new ArrayList<>();
        for (String flowId : flowIds) {
            Map<String, Object> tree = buildTree(flowId);
            if (tree != null) {
                result.add(tree);
            }
        }
        
        log.info("构建完成，共生成 {} 个交易树", result.size());
        return result;
    }
    
    @Override
    public List<Map<String, Object>> getFlowListByDomain(String domain) {
        log.info("根据领域查询交易列表：domain={}", domain);
        
        // 加载flowtran缓存（如果还没加载）
        if (flowtranCache.isEmpty()) {
            List<Flowtran> flowtrans = flowtranMapper.selectList(null);
            for (Flowtran ft : flowtrans) {
                flowtranCache.put(ft.getId(), ft);
            }
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 根据from_jar字段匹配领域
        // from_jar格式：dept-pbf、sett-pbf、comm-pbf、loan-pbf等
        String domainPrefix = domain + "-";
        
        for (Flowtran flowtran : flowtranCache.values()) {
            String fromJar = flowtran.getFromJar();
            if (fromJar != null && fromJar.startsWith(domainPrefix)) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", flowtran.getId());
                item.put("longname", flowtran.getLongname());
                item.put("fromJar", flowtran.getFromJar());
                item.put("txnMode", flowtran.getTxnMode());
                result.add(item);
            }
        }
        
        // 按id排序
        result.sort((a, b) -> {
            String idA = (String) a.get("id");
            String idB = (String) b.get("id");
            return idA != null && idB != null ? idA.compareTo(idB) : 0;
        });
        
        log.info("领域 {} 下找到 {} 个交易", domain, result.size());
        return result;
    }
    
    @Override
    public String saveFlowTreeToFile(String domain, String flowId, Map<String, Object> flowTree) {
        log.info("保存交易树到文件：domain={}, flowId={}", domain, flowId);
        
        try {
            // 创建保存目录（项目根目录下的flow-trees文件夹）
            String projectRoot = System.getProperty("user.dir");
            String saveDir = projectRoot + File.separator + "flow-trees";
            
            File dir = new File(saveDir);
            if (!dir.exists()) {
                dir.mkdirs();
                log.info("创建目录：{}", saveDir);
            }
            
            // 构建文件名：领域-交易id.json
            String fileName = domain + "-" + flowId + ".json";
            String filePath = saveDir + File.separator + fileName;
            
            // 将JSON数据写入文件
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), flowTree);
            
            log.info("交易树已保存到文件：{}", filePath);
            return filePath;
            
        } catch (IOException e) {
            log.error("保存交易树到文件失败：domain={}, flowId={}", domain, flowId, e);
            throw new RuntimeException("保存交易树到文件失败：" + e.getMessage(), e);
        }
    }
    
    /**
     * 构建单个交易树
     */
    private Map<String, Object> buildTree(String flowId) {
        // 获取flowtran信息
        Flowtran flowtran = flowtranCache.get(flowId);
        if (flowtran == null) {
            log.warn("未找到交易：{}", flowId);
            return null;
        }
        
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("flow_id", flowtran.getId());
        tree.put("flow_long_name", flowtran.getLongname());
        tree.put("flow_txn_mode", flowtran.getTxnMode());
        tree.put("flow_from_jar", flowtran.getFromJar());
        
        // 从 flow_from_jar 中提取 flow_domain 和 flow_kind
        // 例如："loan-pbf" -> flow_domain="loan", flow_kind="pbf"
        String fromJar = flowtran.getFromJar();
        if (fromJar != null && fromJar.contains("-")) {
            String[] parts = fromJar.split("-", 2);
            if (parts.length == 2) {
                tree.put("flow_domain", parts[0]); // 前半部分：loan
                tree.put("flow_kind", parts[1]);   // 后半部分：pbf
            }
        }
        
        // 获取flow_step列表，按step排序
        List<FlowStep> flowSteps = flowStepCache.getOrDefault(flowId, new ArrayList<>());
        flowSteps.sort(Comparator.comparing(FlowStep::getStep));
        
        // 构建flowList
        List<Map<String, Object>> flowList = new ArrayList<>();
        for (FlowStep flowStep : flowSteps) {
            Map<String, Object> node = buildFlowNode(flowStep);
            if (node != null) {
                flowList.add(node);
            }
        }
        
        tree.put("flowList", flowList);
        
        return tree;
    }
    
    /**
     * 构建flow节点
     */
    private Map<String, Object> buildFlowNode(FlowStep flowStep) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("node_name", flowStep.getNodeName());
        node.put("node_type", flowStep.getNodeType());
        node.put("step", flowStep.getStep());
        node.put("node_long_name", flowStep.getNodeLongname());
        
        // 如果是service节点，需要补充额外信息
        if ("service".equals(flowStep.getNodeType())) {
            String nodeName = flowStep.getNodeName();
            if (nodeName != null && nodeName.contains(".")) {
                // 解析 xxxxx.xxx 格式
                String[] parts = nodeName.split("\\.", 2);
                if (parts.length == 2) {
                    String serviceTypeId = parts[0]; // xxxxx
                    String serviceId = parts[1]; // xxx
                    
                    // 查找service_type_impl_id
                    ServiceTypeImplFile implFile = serviceTypeImplFileCache.get(serviceTypeId);
                    if (implFile != null) {
                        node.put("service_type_impl_id", implFile.getServiceTypeImplId());
                    }
                    
                    // 查找service_name, service_type_kind, domain
                    List<ServiceTypeFile> serviceTypeFiles = serviceTypeFileCache.get(serviceTypeId);
                    if (serviceTypeFiles != null) {
                        for (ServiceTypeFile stf : serviceTypeFiles) {
                            if (serviceId.equals(stf.getServiceId())) {
                                node.put("service_name", stf.getServiceName());
                                node.put("service_type_kind", stf.getServiceTypeKind());
                                
                                // 提取domain
                                String fromJar = stf.getServiceTypeFromJar();
                                if (fromJar != null && fromJar.contains("-")) {
                                    String domain = fromJar.substring(0, fromJar.indexOf("-"));
                                    node.put("domain", domain);
                                }
                                
                                // 构建code_service_list（递归）
                                String serviceTypeImplId = implFile != null ? implFile.getServiceTypeImplId() : null;
                                List<Map<String, Object>> codeServiceList = buildCodeServiceList(
                                    serviceTypeId, serviceTypeImplId, stf.getServiceName(), new HashSet<>());
                                node.put("code_service_list", codeServiceList);
                                
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        return node;
    }
    
    /**
     * 递归构建code_service_list
     * @param serviceTypeId 当前service的service_type_id
     * @param serviceTypeImplId 当前service的service_type_impl_id
     * @param serviceName 当前service的service_name
     * @param visited 已访问的节点集合（防止循环引用）
     */
    private List<Map<String, Object>> buildCodeServiceList(String serviceTypeId, String serviceTypeImplId, 
                                                          String serviceName, Set<String> visited) {
        // 防止循环引用
        String key = serviceTypeId + "|" + serviceTypeImplId + "|" + serviceName;
        if (visited.contains(key)) {
            log.debug("检测到循环引用，跳过：{}", key);
            return new ArrayList<>();
        }
        visited.add(key);
        
        List<Map<String, Object>> codeServiceList = new ArrayList<>();
        
        // 从缓存中查找hard_code_method_stack记录
        // key格式：service_type_id|service_type_impl_id|service_name
        List<HardCodeMethodStack> stacks = hardCodeMethodStackCache.get(key);
        if (stacks == null || stacks.isEmpty()) {
            log.debug("未找到hard_code_method_stack记录：{}", key);
            visited.remove(key);
            return codeServiceList;
        }
        
        log.debug("找到 {} 条hard_code_method_stack记录：{}", stacks.size(), key);
        
        // 按code_service_type和code_method_type分组
        // 同一个code_service_type的不同方法调用，应该合并为一个节点
        Map<String, List<HardCodeMethodStack>> groupedByCodeServiceType = stacks.stream()
            .collect(Collectors.groupingBy(HardCodeMethodStack::getCodeServiceType));
        
        for (Map.Entry<String, List<HardCodeMethodStack>> entry : groupedByCodeServiceType.entrySet()) {
            String codeServiceType = entry.getKey(); // 被调用的service的类名，如：DpCbProdCvrsnBcsSvtp
            
            log.debug("处理code_service_type: {}", codeServiceType);
            
            // 从code_service_type中提取service_type_id（匹配service_type_file表）
            String codeServiceTypeId = findServiceTypeIdByClassName(codeServiceType);
            if (codeServiceTypeId == null) {
                log.debug("未找到匹配的service_type_id：{}", codeServiceType);
                continue;
            }
            
            // 从service_type_file中查找匹配的service
            List<ServiceTypeFile> codeServiceTypeFiles = serviceTypeFileCache.get(codeServiceTypeId);
            if (codeServiceTypeFiles == null || codeServiceTypeFiles.isEmpty()) {
                log.debug("未找到service_type_file记录：service_type_id={}", codeServiceTypeId);
                continue;
            }
            
            // 按code_method_type分组，每个方法对应一个service
            Map<String, List<HardCodeMethodStack>> groupedByMethod = entry.getValue().stream()
                .collect(Collectors.groupingBy(stack -> 
                    stack.getCodeMethodType() != null ? stack.getCodeMethodType() : ""));
            
            // 对于每个方法，查找对应的service
            for (Map.Entry<String, List<HardCodeMethodStack>> methodEntry : groupedByMethod.entrySet()) {
                String codeMethodType = methodEntry.getKey(); // 被调用的方法名
                
                // 根据code_method_type（方法名）匹配service_name
                ServiceTypeFile matchedServiceTypeFile = null;
                for (ServiceTypeFile stf : codeServiceTypeFiles) {
                    if (codeMethodType != null && codeMethodType.equals(stf.getServiceName())) {
                        matchedServiceTypeFile = stf;
                        break;
                    }
                }
                
                // 如果找不到精确匹配，跳过（不创建节点）
                if (matchedServiceTypeFile == null) {
                    log.debug("未找到匹配的service_name：code_method_type={}, service_type_id={}", 
                        codeMethodType, codeServiceTypeId);
                    continue;
                }
                
                // 创建code_service节点
                Map<String, Object> codeService = new LinkedHashMap<>();
                codeService.put("code_service_type_id", codeServiceTypeId);
                
                // 查找对应的service_type_impl_id
                ServiceTypeImplFile codeImplFile = serviceTypeImplFileCache.get(codeServiceTypeId);
                String codeServiceTypeImplId = codeImplFile != null ? codeImplFile.getServiceTypeImplId() : null;
                codeService.put("code_service_type_impl_id", codeServiceTypeImplId);
                
                codeService.put("code_service_name", matchedServiceTypeFile.getServiceName());
                codeService.put("code_service_long_name", matchedServiceTypeFile.getServiceTypeLongName());
                codeService.put("code_service_kind", matchedServiceTypeFile.getServiceTypeKind());
                
                // 提取domain
                String fromJar = matchedServiceTypeFile.getServiceTypeFromJar();
                if (fromJar != null && fromJar.contains("-")) {
                    String domain = fromJar.substring(0, fromJar.indexOf("-"));
                    codeService.put("code_domain", domain);
                }
                
                // 递归查找code_service_list
                List<Map<String, Object>> nestedCodeServiceList = buildCodeServiceList(
                    codeServiceTypeId, codeServiceTypeImplId, matchedServiceTypeFile.getServiceName(), new HashSet<>(visited));
                codeService.put("code_service_list", nestedCodeServiceList);
                
                codeServiceList.add(codeService);
            }
        }
        
        visited.remove(key);
        return codeServiceList;
    }
    
    /**
     * 根据类名查找service_type_id
     * 支持多种匹配策略：精确匹配、去除后缀匹配等
     */
    private String findServiceTypeIdByClassName(String className) {
        // 如果className是完整路径，提取简单类名
        String simpleClassName = className;
        if (className.contains(".")) {
            simpleClassName = className.substring(className.lastIndexOf(".") + 1);
        }
        
        // 策略1：精确匹配
        if (serviceTypeFileCache.containsKey(simpleClassName)) {
            return simpleClassName;
        }
        
        // 策略2：去除常见后缀后匹配
        String[] suffixes = {"Svtp", "Impl", "Pojo", "Po", "Vo", "Dto", "Svc", "Api"};
        for (String suffix : suffixes) {
            if (simpleClassName.endsWith(suffix)) {
                String withoutSuffix = simpleClassName.substring(0, simpleClassName.length() - suffix.length());
                if (serviceTypeFileCache.containsKey(withoutSuffix)) {
                    return withoutSuffix;
                }
            }
        }
        
        // 策略3：在缓存中查找包含该类名的key
        for (String key : serviceTypeFileCache.keySet()) {
            if (key.equals(simpleClassName) || 
                simpleClassName.contains(key) || 
                key.contains(simpleClassName)) {
                return key;
            }
        }
        
        return null;
    }
    
    /**
     * 加载所有缓存
     */
    private void loadCaches() {
        log.info("开始加载缓存数据...");
        
        // 清空缓存
        flowtranCache.clear();
        flowStepCache.clear();
        serviceTypeFileCache.clear();
        serviceTypeImplFileCache.clear();
        hardCodeMethodStackCache.clear();
        
        // 加载flowtran
        List<Flowtran> flowtrans = flowtranMapper.selectList(null);
        for (Flowtran ft : flowtrans) {
            flowtranCache.put(ft.getId(), ft);
        }
        log.info("加载flowtran缓存：{} 条", flowtranCache.size());
        
        // 加载flow_step，按flow_id分组
        List<FlowStep> flowSteps = flowStepMapper.selectList(null);
        for (FlowStep fs : flowSteps) {
            flowStepCache.computeIfAbsent(fs.getFlowId(), k -> new ArrayList<>()).add(fs);
        }
        log.info("加载flow_step缓存：{} 条，涉及 {} 个交易", flowSteps.size(), flowStepCache.size());
        
        // 加载service_type_file，按service_type_id分组
        List<ServiceTypeFile> serviceTypeFiles = serviceTypeFileMapper.selectList(null);
        for (ServiceTypeFile stf : serviceTypeFiles) {
            serviceTypeFileCache.computeIfAbsent(stf.getServiceTypeId(), k -> new ArrayList<>()).add(stf);
        }
        log.info("加载service_type_file缓存：{} 条，涉及 {} 个service_type_id", 
            serviceTypeFiles.size(), serviceTypeFileCache.size());
        
        // 加载service_type_impl_file，按service_type_id索引（一个service_type_id对应一个impl）
        List<ServiceTypeImplFile> serviceTypeImplFiles = serviceTypeImplFileMapper.selectList(null);
        for (ServiceTypeImplFile stif : serviceTypeImplFiles) {
            serviceTypeImplFileCache.put(stif.getServiceTypeId(), stif);
        }
        log.info("加载service_type_impl_file缓存：{} 条", serviceTypeImplFileCache.size());
        
        // 加载hard_code_method_stack，按service_type_id|service_type_impl_id|service_name分组
        List<HardCodeMethodStack> stacks = hardCodeMethodStackMapper.selectList(null);
        for (HardCodeMethodStack stack : stacks) {
            String key = stack.getServiceTypeId() + "|" + 
                        stack.getServiceTypeImplId() + "|" + 
                        stack.getServiceName();
            hardCodeMethodStackCache.computeIfAbsent(key, k -> new ArrayList<>()).add(stack);
        }
        log.info("加载hard_code_method_stack缓存：{} 条，涉及 {} 个组合", 
            stacks.size(), hardCodeMethodStackCache.size());
        
        log.info("缓存加载完成");
    }
    
    @Override
    public boolean checkFileExists(String flowId) {
        try {
            String projectRoot = System.getProperty("user.dir");
            String saveDir = projectRoot + File.separator + "flow-trees";
            File dir = new File(saveDir);
            
            if (!dir.exists()) {
                return false;
            }
            
            // 查找所有可能的文件名（因为不知道具体领域）
            File[] files = dir.listFiles((d, name) -> name.endsWith("-" + flowId + ".json"));
            return files != null && files.length > 0;
        } catch (Exception e) {
            log.error("检查文件存在失败：flowId={}", flowId, e);
            return false;
        }
    }
    
    @Override
    public Map<String, Object> loadFlowTreeFromFile(String flowId) {
        try {
            String projectRoot = System.getProperty("user.dir");
            String saveDir = projectRoot + File.separator + "flow-trees";
            File dir = new File(saveDir);
            
            if (!dir.exists()) {
                return null;
            }
            
            // 查找文件（可能有多个领域的同一个交易，取第一个）
            File[] files = dir.listFiles((d, name) -> name.endsWith("-" + flowId + ".json"));
            if (files == null || files.length == 0) {
                return null;
            }
            
            // 读取JSON文件
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(files[0], Map.class);
            
        } catch (IOException e) {
            log.error("从文件加载交易树失败：flowId={}", flowId, e);
            throw new RuntimeException("加载交易树失败：" + e.getMessage(), e);
        }
    }
    
    @Override
    public int generateAllTreesToFile() {
        log.info("==================== 开始全量生成所有交易树 ====================");
        
        // 加载缓存
        loadCaches();
        
        // 获取所有交易
        List<Flowtran> allFlows = flowtranMapper.selectList(null);
        log.info("共找到 {} 个交易", allFlows.size());
        
        int successCount = 0;
        
        for (Flowtran flow : allFlows) {
            try {
                String flowId = flow.getId();
                String fromJar = flow.getFromJar();
                
                // 从 from_jar 中提取领域（例如：loan-pbf -> loan）
                String domain = "unknown";
                if (fromJar != null && fromJar.contains("-")) {
                    domain = fromJar.split("-")[0];
                }
                
                // 构建交易树
                Map<String, Object> tree = buildTree(flowId);
                
                if (tree != null) {
                    // 保存到文件
                    saveFlowTreeToFile(domain, flowId, tree);
                    successCount++;
                    log.info("已生成：{} - {}", flowId, flow.getLongname());
                } else {
                    log.warn("构建交易树失败：{}", flowId);
                }
            } catch (Exception e) {
                log.error("生成交易树失败：{} - {}", flow.getId(), flow.getLongname(), e);
            }
        }
        
        log.info("==================== 全量生成完成 ====================");
        log.info("成功生成 {}/{} 个交易树文件", successCount, allFlows.size());
        
        return successCount;
    }
    
    @Override
    public Map<String, Object> generateAllWithRuleCheck() {
        log.info("==================== 全量生成+规则检查 ====================");
        
        // 第一步：生成所有交易树JSON文件
        int treeCount = generateAllTreesToFile();
        
        // 第二步：执行分层调用规则检查
        Map<String, Object> checkResult = layerCallRuleCheckService.checkAllFlowSteps();
        
        // 合并结果
        Map<String, Object> result = new HashMap<>();
        result.put("treeCount", treeCount);
        result.put("checkResult", checkResult);
        result.put("totalChecked", checkResult.get("totalChecked"));
        result.put("violationCount", checkResult.get("violationCount"));
        result.put("passCount", checkResult.get("passCount"));
        
        log.info("==================== 全量生成+规则检查完成 ====================");
        return result;
    }
}

