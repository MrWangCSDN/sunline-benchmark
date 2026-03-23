package com.sunline.dict.service.impl;

import com.sunline.dict.entity.FlowStep;
import com.sunline.dict.entity.Flowtran;
import com.sunline.dict.entity.HardCodeMethodStack;
import com.sunline.dict.entity.Component;
import com.sunline.dict.entity.ComponentDetail;
import com.sunline.dict.entity.ServiceDetail;
import com.sunline.dict.entity.ServiceFile;
import com.sunline.dict.entity.ServiceImplFile;
import com.sunline.dict.mapper.ComponentDetailMapper;
import com.sunline.dict.mapper.ComponentMapper;
import com.sunline.dict.mapper.FlowStepMapper;
import com.sunline.dict.mapper.FlowtranMapper;
import com.sunline.dict.mapper.HardCodeMethodStackMapper;
import com.sunline.dict.mapper.ServiceDetailMapper;
import com.sunline.dict.mapper.ServiceFileMapper;
import com.sunline.dict.mapper.ServiceImplFileMapper;
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
    private ServiceFileMapper serviceFileMapper;

    @Autowired
    private ServiceDetailMapper serviceDetailMapper;
    
    @Autowired
    private ComponentMapper componentMapper;

    @Autowired
    private ComponentDetailMapper componentDetailMapper;

    @Autowired
    private ServiceImplFileMapper serviceImplFileMapper;
    
    @Autowired
    private HardCodeMethodStackMapper hardCodeMethodStackMapper;
    
    @Autowired
    private com.sunline.dict.service.LayerCallRuleCheckService layerCallRuleCheckService;
    
    // 缓存
    private Map<String, Flowtran> flowtranCache = new HashMap<>();
    private Map<String, List<FlowStep>> flowStepCache = new HashMap<>(); // key: flow_id
    private Map<String, ServiceFile> serviceFileCache = new HashMap<>(); // key: service.id（= serviceTypeId）
    private Map<String, List<ServiceDetail>> serviceDetailCache = new HashMap<>(); // key: service_type_id
    private Map<String, ServiceImplFile> serviceTypeImplFileCache = new HashMap<>(); // key: serviceTypeId
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
        // 支持两种格式：
        // 1. 旧格式：dept-pbf、sett-pbf、comm-pbf、loan-pbf
        // 2. 新格式：project:branch:dept-pbf/src/main/.../file.flowtrans.xml（全路径）
        for (Flowtran flowtran : flowtranCache.values()) {
            String fromJar = flowtran.getFromJar();
            String extractedDomain = extractDomainFromJar(fromJar);
            if (domain.equals(extractedDomain)) {
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
    
    /**
     * 已知领域列表
     */
    private static final Set<String> KNOWN_DOMAINS = Set.of("comm", "dept", "loan", "sett");

    /**
     * 从 from_jar 中提取领域前缀（如 dept-pbf）。
     * 支持三种格式：
     * 1. 旧格式：comm-pcs-api → comm-pcs-api
     * 2. Webhook 格式：ccbs-dept-impl:master:dept-pbf/src/... → dept-pbf
     * 3. 本地全路径：D:\xxx\ccbs-comm-api\xxx 或 /www/data/ccbs-comm-api/xxx → 从路径段提取
     */
    private String extractDomainPrefixFromJar(String fromJar) {
        if (fromJar == null || fromJar.trim().isEmpty()) return null;
        String input = fromJar.trim();

        // 格式2：含冒号的 Webhook 格式
        if (input.contains(":") && !input.matches("^[A-Za-z]:\\\\.*")) {
            String[] colonParts = input.split(":", 3);
            if (colonParts.length >= 3) {
                String seg = colonParts[2].trim();
                if (seg.contains("/")) seg = seg.split("/")[0].trim();
                return seg.isEmpty() ? null : seg;
            }
        }

        // 格式3：本地全路径（含 / 或 \）→ 扫描路径段，找 ccbs-{domain}-xxx 模式
        if (input.contains("/") || input.contains("\\")) {
            String[] pathSegments = input.replace("\\", "/").split("/");
            for (String seg : pathSegments) {
                // 匹配 ccbs-{domain}-xxx 格式（如 ccbs-comm-api、ccbs-dept-impl）
                if (seg.startsWith("ccbs-") && seg.length() > 5) {
                    String afterCcbs = seg.substring(5); // comm-api、dept-impl
                    if (afterCcbs.contains("-")) {
                        String domain = afterCcbs.split("-")[0]; // comm、dept
                        if (KNOWN_DOMAINS.contains(domain)) {
                            return domain + "-" + afterCcbs.split("-", 2)[1]; // comm-api
                        }
                    }
                }
                // 匹配 {domain}-xxx 格式（如 comm-pcs-api、dept-pbf）
                if (seg.contains("-")) {
                    String firstPart = seg.split("-")[0];
                    if (KNOWN_DOMAINS.contains(firstPart)) {
                        return seg;
                    }
                }
            }
        }

        // 格式1：直接是 comm-pcs-api 这种短格式
        return input;
    }

    /**
     * 从 from_jar 中提取领域标识（如 dept、comm、sett、loan）。
     */
    private String extractDomainFromJar(String fromJar) {
        String prefix = extractDomainPrefixFromJar(fromJar);
        if (prefix == null || !prefix.contains("-")) return prefix;
        return prefix.split("-")[0].trim();
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
        
        // 从 flow_from_jar 中提取 flow_domain 和 flow_kind（兼容新旧格式）
        String prefix = extractDomainPrefixFromJar(flowtran.getFromJar());
        if (prefix != null && prefix.contains("-")) {
            String[] parts = prefix.split("-", 2);
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
                    
                    // 查找 service_type_impl_id（从 serviceImpl 表获取）
                    ServiceImplFile implFile = serviceTypeImplFileCache.get(serviceTypeId);
                    if (implFile != null) {
                        node.put("service_type_impl_id", implFile.getId());
                    }
                    
                    // 从 service 表获取 kind（分层）和 fromJar（领域）
                    ServiceFile serviceFile = serviceFileCache.get(serviceTypeId);
                    if (serviceFile != null) {
                        node.put("service_type_kind", serviceFile.getKind());

                        // 提取 domain（从 fromJar 或 service.id 解析）
                        String fromJar = serviceFile.getFromJar();
                        if (fromJar != null) {
                            String domainPrefix = extractDomainPrefixFromJar(fromJar);
                            if (domainPrefix != null && domainPrefix.contains("-")) {
                                node.put("domain", domainPrefix.split("-")[0]);
                            }
                        }
                    }

                    // 从 service_detail 表获取 service_name（方法）
                    List<ServiceDetail> details = serviceDetailCache.get(serviceTypeId);
                    if (details != null) {
                        for (ServiceDetail sd : details) {
                            if (serviceId.equals(sd.getServiceId())) {
                                node.put("service_name", sd.getServiceName());

                                // 构建code_service_list（递归）
                                String serviceTypeImplId = implFile != null ? implFile.getId() : null;
                                String svcName = sd.getServiceName();
                                List<Map<String, Object>> codeServiceList = buildCodeServiceList(
                                    serviceTypeId, serviceTypeImplId, svcName, new HashSet<>());
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
            
            // 从 service_detail 中查找匹配的方法
            List<ServiceDetail> codeDetails = serviceDetailCache.get(codeServiceTypeId);
            if (codeDetails == null || codeDetails.isEmpty()) {
                log.debug("未找到 service_detail 记录：service_type_id={}", codeServiceTypeId);
                continue;
            }

            // 从 service 表获取 kind 和 fromJar
            ServiceFile codeServiceFile = serviceFileCache.get(codeServiceTypeId);

            // 按 code_method_type 分组
            Map<String, List<HardCodeMethodStack>> groupedByMethod = entry.getValue().stream()
                .collect(Collectors.groupingBy(stack ->
                    stack.getCodeMethodType() != null ? stack.getCodeMethodType() : ""));
            
            for (Map.Entry<String, List<HardCodeMethodStack>> methodEntry : groupedByMethod.entrySet()) {
                String codeMethodType = methodEntry.getKey();
                
                // 根据 code_method_type（方法名）匹配 service_detail.service_name
                ServiceDetail matchedDetail = null;
                for (ServiceDetail sd : codeDetails) {
                    if (codeMethodType != null && codeMethodType.equals(sd.getServiceName())) {
                        matchedDetail = sd;
                        break;
                    }
                }
                
                if (matchedDetail == null) {
                    log.debug("未找到匹配的 service_name：code_method_type={}, service_type_id={}",
                        codeMethodType, codeServiceTypeId);
                    continue;
                }
                
                Map<String, Object> codeService = new LinkedHashMap<>();
                codeService.put("code_service_type_id", codeServiceTypeId);
                
                ServiceImplFile codeImplFile = serviceTypeImplFileCache.get(codeServiceTypeId);
                String codeServiceTypeImplId = codeImplFile != null ? codeImplFile.getId() : null;
                codeService.put("code_service_type_impl_id", codeServiceTypeImplId);
                
                codeService.put("code_service_name", matchedDetail.getServiceName());
                codeService.put("code_service_long_name",
                    codeServiceFile != null ? codeServiceFile.getLongname() : "");
                codeService.put("code_service_kind",
                    codeServiceFile != null ? codeServiceFile.getKind() : "");
                
                // 提取 domain
                if (codeServiceFile != null && codeServiceFile.getFromJar() != null) {
                    String prefix = extractDomainPrefixFromJar(codeServiceFile.getFromJar());
                    if (prefix != null && prefix.contains("-")) {
                        codeService.put("code_domain", prefix.split("-")[0]);
                    }
                }
                
                List<Map<String, Object>> nestedCodeServiceList = buildCodeServiceList(
                    codeServiceTypeId, codeServiceTypeImplId, matchedDetail.getServiceName(), new HashSet<>(visited));
                codeService.put("code_service_list", nestedCodeServiceList);
                
                codeServiceList.add(codeService);
            }
        }
        
        visited.remove(key);
        return codeServiceList;
    }
    
    /**
     * 根据类名查找 service_type_id（从 serviceFileCache 和 serviceDetailCache 中匹配）
     */
    private String findServiceTypeIdByClassName(String className) {
        String simpleClassName = className;
        if (className.contains(".")) {
            simpleClassName = className.substring(className.lastIndexOf(".") + 1);
        }
        
        // 策略1：精确匹配 serviceFileCache（key = service.id = serviceTypeId）
        if (serviceFileCache.containsKey(simpleClassName)) {
            return simpleClassName;
        }
        
        // 策略2：去除常见后缀后匹配
        String[] suffixes = {"Svtp", "Impl", "Pojo", "Po", "Vo", "Dto", "Svc", "Api"};
        for (String suffix : suffixes) {
            if (simpleClassName.endsWith(suffix)) {
                String withoutSuffix = simpleClassName.substring(0, simpleClassName.length() - suffix.length());
                if (serviceFileCache.containsKey(withoutSuffix)) {
                    return withoutSuffix;
                }
            }
        }
        
        // 策略3：模糊匹配
        for (String key : serviceFileCache.keySet()) {
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
        serviceFileCache.clear();
        serviceDetailCache.clear();
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
        
        // 加载 service 表，按 service.id 索引（service.id = serviceTypeId）
        List<ServiceFile> serviceFiles = serviceFileMapper.selectList(null);
        for (ServiceFile sf : serviceFiles) {
            serviceFileCache.put(sf.getId(), sf);
        }
        log.info("加载 service 缓存：{} 条", serviceFiles.size());

        // 加载 component 表，合并到 serviceFileCache（构件和服务共用同一缓存）
        // Component 的字段（id, kind, fromJar）与 ServiceFile 一致，构造一个 ServiceFile 代理对象
        List<Component> components = componentMapper.selectList(null);
        for (Component c : components) {
            if (!serviceFileCache.containsKey(c.getId())) {
                ServiceFile proxy = new ServiceFile();
                proxy.setId(c.getId());
                proxy.setLongname(c.getLongname());
                proxy.setPackagePath(c.getPackagePath());
                proxy.setKind(c.getKind());
                proxy.setServiceType(c.getComponentType());
                proxy.setFromJar(c.getFromJar());
                serviceFileCache.put(c.getId(), proxy);
            }
        }
        log.info("加载 service+component 缓存合计：{} 条（service={}, component={}）",
            serviceFileCache.size(), serviceFiles.size(), components.size());

        // 加载 service_detail 表，按 service_type_id 分组
        List<ServiceDetail> serviceDetails = serviceDetailMapper.selectList(null);
        for (ServiceDetail sd : serviceDetails) {
            serviceDetailCache.computeIfAbsent(sd.getServiceTypeId(), k -> new ArrayList<>()).add(sd);
        }

        // 加载 component_detail 表，合并到 serviceDetailCache
        // ComponentDetail 的字段（componentId, serviceId, serviceName）与 ServiceDetail 对应
        List<ComponentDetail> componentDetails = componentDetailMapper.selectList(null);
        for (ComponentDetail cd : componentDetails) {
            if (cd.getComponentId() != null) {
                ServiceDetail proxy = new ServiceDetail();
                proxy.setServiceTypeId(cd.getComponentId());
                proxy.setServiceId(cd.getServiceId());
                proxy.setServiceName(cd.getServiceName());
                proxy.setServiceLongname(cd.getServiceLongname());
                serviceDetailCache.computeIfAbsent(cd.getComponentId(), k -> new ArrayList<>()).add(proxy);
            }
        }
        log.info("加载 service_detail+component_detail 缓存合计：涉及 {} 个 serviceTypeId（detail={}, componentDetail={}）",
            serviceDetailCache.size(), serviceDetails.size(), componentDetails.size());
        
        // 加载 serviceImpl 表，按 serviceType 字段 或 id 前缀作为 cache key
        // serviceImpl.serviceType 存储的是引用的 serviceType id（如 StCustQuery）
        // 若 serviceType 为空或存的是类型名（如 pcsImpl），则从 id 中提取（id 格式如 StCustQuery.StCustQueryPbsImpl）
        List<ServiceImplFile> serviceImplFiles = serviceImplFileMapper.selectList(null);
        for (ServiceImplFile sif : serviceImplFiles) {
            String cacheKey = resolveServiceTypeIdFromImpl(sif);
            if (cacheKey != null && !cacheKey.isEmpty()) {
                serviceTypeImplFileCache.put(cacheKey, sif);
            }
        }
        log.info("加载 serviceImpl 缓存：{} 条", serviceTypeImplFileCache.size());
        
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

    /**
     * 从 ServiceImplFile 中提取对应的 serviceTypeId 作为缓存 key
     * 优先级：
     * 1. serviceType 字段（若内容看起来像 serviceTypeId，如含大写字母且不含 Impl）
     * 2. id 字段的 "." 前半部分（id 格式如 StCustQuery.StCustQueryPbsImpl → StCustQuery）
     */
    private String resolveServiceTypeIdFromImpl(ServiceImplFile sif) {
        // 优先从 serviceType 字段获取（若它存的是真正的 serviceTypeId）
        String st = sif.getServiceType();
        if (st != null && !st.isEmpty() && !st.contains("Impl") && !st.equals("pcs") && !st.equals("pbs")
                && !st.equals("pbcb") && !st.equals("pbcp") && !st.equals("pbcc") && !st.equals("pbct")) {
            return st;
        }

        // 从 id 中提取 "." 前半部分
        String id = sif.getId();
        if (id != null && id.contains(".")) {
            return id.split("\\.", 2)[0];
        }

        return st;
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
                
                // 从 from_jar 中提取领域（兼容旧格式 dept-pbf 和新格式全路径）
                String domain = extractDomainFromJar(fromJar);
                if (domain == null) domain = "unknown";
                
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

