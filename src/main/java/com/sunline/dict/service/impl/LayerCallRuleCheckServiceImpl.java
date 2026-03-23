package com.sunline.dict.service.impl;

import com.sunline.dict.entity.*;
import com.sunline.dict.mapper.*;
import com.sunline.dict.service.LayerCallRuleCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class LayerCallRuleCheckServiceImpl implements LayerCallRuleCheckService {
    
    private static final Logger log = LoggerFactory.getLogger(LayerCallRuleCheckServiceImpl.class);
    
    @Autowired
    private FlowStepMapper flowStepMapper;
    
    @Autowired
    private FlowtranMapper flowtranMapper;
    
    @Autowired
    private ServiceFileMapper serviceFileMapper;

    @Autowired
    private ServiceDetailMapper serviceDetailMapper;

    @Autowired
    private ComponentMapper componentMapper;

    @Autowired
    private ComponentDetailMapper componentDetailMapper;
    
    @Autowired
    private HardCodeMethodStackMapper hardCodeMethodStackMapper;
    
    @Autowired
    private LayerCallRuleMapper ruleMapper;
    
    @Autowired
    private LayerCallRuleItemMapper ruleItemMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkAllFlowSteps() {
        log.info("==================== 开始检查分层调用规则 ====================");
        
        // 第一步：检查 flow_step 的调用关系（pbf层调用）
        Map<String, Object> flowStepResult = checkFlowStepCalls();
        
        // 第二步：检查 service_type_file 的硬编码调用（pbs、pcs、pbcb等层调用）
        Map<String, Object> serviceTypeResult = checkServiceTypeCalls();
        
        // 合并结果
        Map<String, Object> result = new HashMap<>();
        result.put("flowStepChecked", flowStepResult.get("totalChecked"));
        result.put("flowStepViolations", flowStepResult.get("violationCount"));
        result.put("serviceTypeChecked", serviceTypeResult.get("totalChecked"));
        result.put("serviceTypeViolations", serviceTypeResult.get("violationCount"));
        result.put("totalChecked", (Integer)flowStepResult.get("totalChecked") + (Integer)serviceTypeResult.get("totalChecked"));
        result.put("violationCount", (Integer)flowStepResult.get("violationCount") + (Integer)serviceTypeResult.get("violationCount"));
        
        log.info("==================== 检查完成 ====================");
        return result;
    }
    
    private Map<String, Object> checkFlowStepCalls() {
        log.info("检查 flow_step 调用关系（pbf层）");
        
        // 加载规则
        Map<String, List<LayerCallRuleItem>> ruleMap = loadRules();
        
        // 加载flow领域映射
        Map<String, String> flowDomainMap = loadFlowDomains();
        
        // 加载 service+component 统一映射（key = serviceTypeId|serviceId）
        Map<String, ServiceInfo> serviceTypeMap = buildUnifiedServiceIdMap();
        
        List<FlowStep> allSteps = flowStepMapper.selectList(null);
        int totalChecked = 0;
        int violationCount = 0;
        
        for (FlowStep step : allSteps) {
            totalChecked++;
            List<String> violations = checkSingleFlowStep(step, flowDomainMap, serviceTypeMap, ruleMap);
            
            if (!violations.isEmpty()) {
                violationCount++;
                step.setIncorrectCalls(String.join(",", violations));
                flowStepMapper.updateById(step);
            } else if (step.getIncorrectCalls() != null && !step.getIncorrectCalls().isEmpty()) {
                step.setIncorrectCalls(null);
                flowStepMapper.updateById(step);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalChecked", totalChecked);
        result.put("violationCount", violationCount);
        return result;
    }
    
    /**
     * 内部结构：合并 service/service_detail 和 component/component_detail 的查询视图
     */
    private static class ServiceInfo {
        String serviceTypeId;
        String kind;
        String fromJar;

        ServiceInfo(String serviceTypeId, String kind, String fromJar) {
            this.serviceTypeId = serviceTypeId;
            this.kind = kind;
            this.fromJar = fromJar;
        }
    }

    /**
     * 构建统一的 ServiceInfo Map（service + component 合并），key = serviceTypeId|serviceName
     */
    private Map<String, ServiceInfo> buildUnifiedServiceMap() {
        Map<String, ServiceInfo> map = new HashMap<>();

        // 加载 service + service_detail
        Map<String, ServiceFile> sfMap = new HashMap<>();
        for (ServiceFile sf : serviceFileMapper.selectList(null)) {
            sfMap.put(sf.getId(), sf);
        }
        for (ServiceDetail sd : serviceDetailMapper.selectList(null)) {
            ServiceFile sf = sfMap.get(sd.getServiceTypeId());
            if (sf != null && sd.getServiceName() != null) {
                map.put(sd.getServiceTypeId() + "|" + sd.getServiceName(),
                        new ServiceInfo(sf.getId(), sf.getKind(), sf.getFromJar()));
            }
        }

        // 加载 component + component_detail，合并到同一 Map
        Map<String, Component> compMap = new HashMap<>();
        for (Component c : componentMapper.selectList(null)) {
            compMap.put(c.getId(), c);
        }
        for (ComponentDetail cd : componentDetailMapper.selectList(null)) {
            Component c = compMap.get(cd.getComponentId());
            if (c != null && cd.getServiceName() != null) {
                String key = cd.getComponentId() + "|" + cd.getServiceName();
                if (!map.containsKey(key)) {
                    map.put(key, new ServiceInfo(c.getId(), c.getKind(), c.getFromJar()));
                }
            }
        }

        return map;
    }

    /**
     * 构建统一的 ServiceInfo Map，key = serviceTypeId|serviceId（用于 flow_step 查找）
     */
    private Map<String, ServiceInfo> buildUnifiedServiceIdMap() {
        Map<String, ServiceInfo> map = new HashMap<>();

        Map<String, ServiceFile> sfMap = new HashMap<>();
        for (ServiceFile sf : serviceFileMapper.selectList(null)) {
            sfMap.put(sf.getId(), sf);
        }
        for (ServiceDetail sd : serviceDetailMapper.selectList(null)) {
            ServiceFile sf = sfMap.get(sd.getServiceTypeId());
            if (sf != null && sd.getServiceId() != null) {
                map.put(sd.getServiceTypeId() + "|" + sd.getServiceId(),
                        new ServiceInfo(sf.getId(), sf.getKind(), sf.getFromJar()));
            }
        }

        Map<String, Component> compMap = new HashMap<>();
        for (Component c : componentMapper.selectList(null)) {
            compMap.put(c.getId(), c);
        }
        for (ComponentDetail cd : componentDetailMapper.selectList(null)) {
            Component c = compMap.get(cd.getComponentId());
            if (c != null && cd.getServiceId() != null) {
                String key = cd.getComponentId() + "|" + cd.getServiceId();
                if (!map.containsKey(key)) {
                    map.put(key, new ServiceInfo(c.getId(), c.getKind(), c.getFromJar()));
                }
            }
        }

        return map;
    }

    private Map<String, Object> checkServiceTypeCalls() {
        log.info("检查 service/component 硬编码调用（pbs、pcs、pbcb等层）");
        
        Map<String, List<LayerCallRuleItem>> ruleMap = loadRules();
        Map<String, ServiceInfo> serviceMap = buildUnifiedServiceMap();
        
        List<HardCodeMethodStack> allStacks = hardCodeMethodStackMapper.selectList(null);
        
        int totalChecked = 0;
        int violationCount = 0;
        
        Map<String, List<HardCodeMethodStack>> stackMap = new HashMap<>();
        for (HardCodeMethodStack stack : allStacks) {
            String key = stack.getServiceTypeId() + "|" + stack.getServiceName();
            stackMap.computeIfAbsent(key, k -> new ArrayList<>()).add(stack);
        }

        // 用于批量更新 service/component 的 incorrectCalls
        Map<String, ServiceFile> sfUpdateMap = new HashMap<>();
        for (ServiceFile sf : serviceFileMapper.selectList(null)) {
            sfUpdateMap.put(sf.getId(), sf);
        }
        
        for (Map.Entry<String, List<HardCodeMethodStack>> entry : stackMap.entrySet()) {
            String key = entry.getKey();
            ServiceInfo caller = serviceMap.get(key);
            
            if (caller == null) continue;
            
            totalChecked++;
            List<String> violations = new ArrayList<>();
            
            for (HardCodeMethodStack stack : entry.getValue()) {
                String calleeKey = stack.getCodeServiceType() + "|" + stack.getCodeMethodType();
                ServiceInfo callee = serviceMap.get(calleeKey);
                
                if (callee != null) {
                    String violation = checkServiceCall(caller, callee, ruleMap);
                    if (violation != null) {
                        violations.add(violation);
                    }
                }
            }
            
            // 写入 incorrectCalls（service 表）
            ServiceFile sf = sfUpdateMap.get(caller.serviceTypeId);
            if (sf != null) {
                if (!violations.isEmpty()) {
                    violationCount++;
                    sf.setIncorrectCalls(String.join(",", violations));
                    serviceFileMapper.updateById(sf);
                } else if (sf.getIncorrectCalls() != null && !sf.getIncorrectCalls().isEmpty()) {
                    sf.setIncorrectCalls(null);
                    serviceFileMapper.updateById(sf);
                }
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalChecked", totalChecked);
        result.put("violationCount", violationCount);
        return result;
    }
    
    private String checkServiceCall(ServiceInfo caller, ServiceInfo callee, 
                                    Map<String, List<LayerCallRuleItem>> ruleMap) {
        String callerLayer = caller.kind;
        String calleeLayer = callee.kind;
        
        String callerDomain = extractDomain(caller.fromJar);
        String calleeDomain = extractDomain(callee.fromJar);
        
        List<LayerCallRuleItem> rules = ruleMap.get(callerLayer);
        if (rules == null) return null;
        
        for (LayerCallRuleItem rule : rules) {
            if (rule.getCalleeLayer().equals(calleeLayer)) {
                if ("same_domain".equals(rule.getDomainConstraint())) {
                    if (callerDomain != null && callerDomain.equals(calleeDomain)) {
                        return null;
                    }
                } else if ("cross_domain".equals(rule.getDomainConstraint())) {
                    return null;
                }
            }
        }
        
        return callee.serviceTypeId + "(" + calleeLayer + "-" + calleeDomain + ")";
    }
    
    private List<String> checkSingleFlowStep(FlowStep step, Map<String, String> flowDomainMap, 
                                            Map<String, ServiceInfo> serviceInfoMap,
                                            Map<String, List<LayerCallRuleItem>> ruleMap) {
        List<String> violations = new ArrayList<>();
        String callerDomain = flowDomainMap.get(step.getFlowId());
        if (callerDomain == null) return violations;
        
        String calleeLayer = null;
        String calleeDomain = null;
        
        if ("method".equals(step.getNodeType())) {
            calleeLayer = "pbf方法层";
            calleeDomain = callerDomain;
        } else if ("service".equals(step.getNodeType())) {
            String nodeName = step.getNodeName();
            if (nodeName != null && nodeName.contains(".")) {
                String[] parts = nodeName.split("\\.", 2);
                String key = parts[0] + "|" + (parts.length > 1 ? parts[1] : "");
                ServiceInfo info = serviceInfoMap.get(key);
                if (info != null) {
                    calleeLayer = info.kind + "层";
                    calleeDomain = extractDomain(info.fromJar);
                }
            }
        }
        
        if (calleeLayer == null) return violations;
        
        List<LayerCallRuleItem> rules = ruleMap.get("pbf层");
        if (rules == null) return violations;
        
        boolean allowed = false;
        for (LayerCallRuleItem rule : rules) {
            if (rule.getCalleeLayer().equals(calleeLayer)) {
                if ("same_domain".equals(rule.getDomainConstraint())) {
                    if (callerDomain.equals(calleeDomain)) {
                        allowed = true;
                        break;
                    }
                } else if ("cross_domain".equals(rule.getDomainConstraint())) {
                    allowed = true;
                    break;
                }
            }
        }
        
        if (!allowed) {
            if (!callerDomain.equals(calleeDomain)) {
                violations.add(calleeDomain);
            } else {
                violations.add(calleeLayer);
            }
        }
        
        return violations;
    }
    
    private Map<String, List<LayerCallRuleItem>> loadRules() {
        Map<String, List<LayerCallRuleItem>> ruleMap = new HashMap<>();
        List<LayerCallRule> rules = ruleMapper.selectList(null);
        for (LayerCallRule rule : rules) {
            List<LayerCallRuleItem> items = ruleItemMapper.selectByRuleId(rule.getId());
            for (LayerCallRuleItem item : items) {
                ruleMap.computeIfAbsent(item.getCallerLayer(), k -> new ArrayList<>()).add(item);
            }
        }
        return ruleMap;
    }
    
    private Map<String, String> loadFlowDomains() {
        Map<String, String> flowDomainMap = new HashMap<>();
        List<Flowtran> flows = flowtranMapper.selectList(null);
        for (Flowtran flow : flows) {
            String domain = extractDomain(flow.getFromJar());
            if (domain != null) {
                flowDomainMap.put(flow.getId(), domain);
            }
        }
        return flowDomainMap;
    }
    
    private static final Set<String> KNOWN_DOMAINS = Set.of("comm", "dept", "loan", "sett");

    /**
     * 从 fromJar 中提取领域标识（comm/dept/loan/sett）
     * 支持旧格式、Webhook格式、本地全路径格式
     */
    private String extractDomain(String fromJar) {
        if (fromJar == null || fromJar.trim().isEmpty()) return null;
        String input = fromJar.trim();

        if (input.contains(":") && !input.matches("^[A-Za-z]:\\\\.*")) {
            String[] colonParts = input.split(":", 3);
            if (colonParts.length >= 3) {
                String seg = colonParts[2].trim();
                if (seg.contains("/")) seg = seg.split("/")[0].trim();
                if (seg.contains("-")) return seg.split("-")[0];
            }
        }

        if (input.contains("/") || input.contains("\\")) {
            String[] segs = input.replace("\\", "/").split("/");
            for (String seg : segs) {
                if (seg.startsWith("ccbs-") && seg.length() > 5) {
                    String after = seg.substring(5);
                    if (after.contains("-")) {
                        String d = after.split("-")[0];
                        if (KNOWN_DOMAINS.contains(d)) return d;
                    }
                }
                if (seg.contains("-")) {
                    String d = seg.split("-")[0];
                    if (KNOWN_DOMAINS.contains(d)) return d;
                }
            }
        }

        if (input.contains("-")) return input.split("-")[0];
        return null;
    }
}
