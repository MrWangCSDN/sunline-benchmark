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
    private ServiceTypeFileMapper serviceTypeFileMapper;
    
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
        
        // 加载service_type映射
        Map<String, ServiceTypeFile> serviceTypeMap = loadServiceTypeMap();
        
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
    
    private Map<String, Object> checkServiceTypeCalls() {
        log.info("检查 service_type_file 硬编码调用（pbs、pcs、pbcb等层）");
        
        // 加载规则
        Map<String, List<LayerCallRuleItem>> ruleMap = loadRules();
        
        // 加载所有service_type_file
        List<ServiceTypeFile> allServices = serviceTypeFileMapper.selectList(null);
        Map<String, ServiceTypeFile> serviceMap = new HashMap<>();
        for (ServiceTypeFile stf : allServices) {
            String key = stf.getServiceTypeId() + "|" + stf.getServiceName();
            serviceMap.put(key, stf);
        }
        
        // 加载所有hard_code_method_stack
        List<HardCodeMethodStack> allStacks = hardCodeMethodStackMapper.selectList(null);
        
        int totalChecked = 0;
        int violationCount = 0;
        
        // 按 service_type_id|service_name 分组
        Map<String, List<HardCodeMethodStack>> stackMap = new HashMap<>();
        for (HardCodeMethodStack stack : allStacks) {
            String key = stack.getServiceTypeId() + "|" + stack.getServiceName();
            stackMap.computeIfAbsent(key, k -> new ArrayList<>()).add(stack);
        }
        
        // 检查每个service的硬编码调用
        for (Map.Entry<String, List<HardCodeMethodStack>> entry : stackMap.entrySet()) {
            String key = entry.getKey();
            ServiceTypeFile caller = serviceMap.get(key);
            
            if (caller == null) continue;
            
            totalChecked++;
            List<String> violations = new ArrayList<>();
            
            for (HardCodeMethodStack stack : entry.getValue()) {
                String codeServiceType = stack.getCodeServiceType();
                String codeMethodType = stack.getCodeMethodType();
                
                // 在 service_type_file 表中查找被调用方
                String calleeKey = codeServiceType + "|" + codeMethodType;
                ServiceTypeFile callee = serviceMap.get(calleeKey);
                
                if (callee != null) {
                    // 检查调用是否合规
                    String violation = checkServiceCall(caller, callee, ruleMap);
                    if (violation != null) {
                        violations.add(violation);
                    }
                }
            }
            
            if (!violations.isEmpty()) {
                violationCount++;
                caller.setIncorrectCalls(String.join(",", violations));
                serviceTypeFileMapper.updateById(caller);
            } else if (caller.getIncorrectCalls() != null && !caller.getIncorrectCalls().isEmpty()) {
                caller.setIncorrectCalls(null);
                serviceTypeFileMapper.updateById(caller);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalChecked", totalChecked);
        result.put("violationCount", violationCount);
        return result;
    }
    
    private String checkServiceCall(ServiceTypeFile caller, ServiceTypeFile callee, 
                                    Map<String, List<LayerCallRuleItem>> ruleMap) {
        String callerLayer = caller.getServiceTypeKind();
        String calleeLayer = callee.getServiceTypeKind();
        
        String callerDomain = extractDomain(caller.getServiceTypeFromJar());
        String calleeDomain = extractDomain(callee.getServiceTypeFromJar());
        
        // 检查规则
        List<LayerCallRuleItem> rules = ruleMap.get(callerLayer);
        if (rules == null) return null;
        
        for (LayerCallRuleItem rule : rules) {
            if (rule.getCalleeLayer().equals(calleeLayer)) {
                if ("same_domain".equals(rule.getDomainConstraint())) {
                    if (callerDomain != null && callerDomain.equals(calleeDomain)) {
                        return null; // 合规
                    }
                } else if ("cross_domain".equals(rule.getDomainConstraint())) {
                    return null; // 合规
                }
            }
        }
        
        // 违规：返回格式 接口ID(分层-领域)
        return callee.getServiceTypeId() + "(" + calleeLayer + "-" + calleeDomain + ")";
    }
    
    private List<String> checkSingleFlowStep(FlowStep step, Map<String, String> flowDomainMap, 
                                            Map<String, ServiceTypeFile> serviceTypeMap,
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
                ServiceTypeFile stf = serviceTypeMap.get(key);
                if (stf != null) {
                    calleeLayer = stf.getServiceTypeKind()+"层";
                    calleeDomain = extractDomain(stf.getServiceTypeFromJar());
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
    
    private Map<String, ServiceTypeFile> loadServiceTypeMap() {
        Map<String, ServiceTypeFile> map = new HashMap<>();
        List<ServiceTypeFile> files = serviceTypeFileMapper.selectList(null);
        for (ServiceTypeFile stf : files) {
            String key = stf.getServiceTypeId() + "|" + stf.getServiceId();
            map.put(key, stf);
        }
        return map;
    }
    
    private String extractDomain(String fromJar) {
        if (fromJar != null && fromJar.contains("-")) {
            return fromJar.split("-")[0];
        }
        return null;
    }
}
