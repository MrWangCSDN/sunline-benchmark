package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.FlowStep;
import com.sunline.dict.entity.Flowtran;
import com.sunline.dict.mapper.FlowStepMapper;
import com.sunline.dict.mapper.FlowtranMapper;
import com.sunline.dict.service.FlowXmlParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FlowTrans XML解析服务实现
 * 复用JarScanServiceImpl的XML解析逻辑
 */
@Service
public class FlowXmlParseServiceImpl implements FlowXmlParseService {
    
    private static final Logger log = LoggerFactory.getLogger(FlowXmlParseServiceImpl.class);
    
    @Autowired
    private FlowtranMapper flowtranMapper;
    
    @Autowired
    private FlowStepMapper flowStepMapper;
    
    @Override
    public Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception {
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        return parseAndSave(is, sourceInfo);
    }
    
    @Override
    public Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception {
        log.info("开始解析flowtrans.xml文件，来源: {}", sourceInfo);
        
        Map<String, Object> result = new HashMap<>();
        int flowtranCount = 0;
        int flowStepCount = 0;
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlContent);
        
        Element root = doc.getDocumentElement();
        
        // 解析flowtran节点
        String id = root.getAttribute("id");
        String longname = root.getAttribute("longname");
        String packagePath = root.getAttribute("package");
        String txnMode = root.getAttribute("txnMode");
        
        log.info("解析XML根节点：id={}, longname={}, package={}, txnMode={}", id, longname, packagePath, txnMode);
        
        if (id != null && !id.isEmpty()) {
            // 保存或更新flowtran信息
            Flowtran flowtran = new Flowtran();
            flowtran.setId(id);
            flowtran.setLongname(longname);
            flowtran.setPackagePath(packagePath);
            // 只有当txnMode属性存在且不为空时才设置
            if (txnMode != null && !txnMode.isEmpty()) {
                flowtran.setTxnMode(txnMode);
            }
            flowtran.setFromJar(sourceInfo); // 使用sourceInfo作为来源标识
            flowtran.setCreateTime(LocalDateTime.now());
            flowtran.setUpdateTime(LocalDateTime.now());
            
            // 先删除已存在的记录
            flowtranMapper.deleteById(id);
            // 插入新记录
            flowtranMapper.insert(flowtran);
            flowtranCount++;
            log.info("成功保存交易：id={}, longname={}", id, longname);
            
            // 删除该交易的所有步骤
            QueryWrapper<FlowStep> deleteWrapper = new QueryWrapper<>();
            deleteWrapper.eq("flow_id", id);
            flowStepMapper.delete(deleteWrapper);
            
            // 解析flow节点（递归扫描所有嵌套的service和method）
            NodeList flowNodes = root.getElementsByTagName("flow");
            if (flowNodes.getLength() > 0) {
                Element flowElement = (Element) flowNodes.item(0);
                
                // 使用递归方式扫描所有嵌套的service和method节点
                List<Element> stepElements = new ArrayList<>();
                collectServiceAndMethodNodes(flowElement, stepElements);
                
                log.info("交易ID: {}, 找到 {} 个流程步骤节点（包括嵌套的）", id, stepElements.size());
                
                int step = 1;
                for (Element element : stepElements) {
                    String tagName = element.getTagName();
                    
                    FlowStep flowStep = new FlowStep();
                    flowStep.setFlowId(id);
                    flowStep.setNodeType(tagName);
                    flowStep.setStep(step++);
                    flowStep.setCreateTime(LocalDateTime.now());
                    flowStep.setUpdateTime(LocalDateTime.now());
                    
                    if ("service".equals(tagName)) {
                        // service节点：node_name记录serviceName属性
                        String serviceName = element.getAttribute("serviceName");
                        String nodeLongname = element.getAttribute("longname");
                        
                        if (serviceName == null || serviceName.trim().isEmpty()) {
                            log.warn("service节点缺少serviceName属性，flowId: {}, step: {}", id, step - 1);
                            serviceName = ""; // 设置为空字符串，避免null
                        }
                        
                        flowStep.setNodeName(serviceName);
                        flowStep.setNodeLongname(nodeLongname);
                    } else if ("method".equals(tagName)) {
                        // method节点：node_name记录method属性
                        String methodName = element.getAttribute("method");
                        String nodeLongname = element.getAttribute("longname");
                        
                        if (methodName == null || methodName.trim().isEmpty()) {
                            log.warn("method节点缺少method属性，flowId: {}, step: {}", id, step - 1);
                            methodName = ""; // 设置为空字符串，避免null
                        }
                        
                        flowStep.setNodeName(methodName);
                        flowStep.setNodeLongname(nodeLongname);
                    }
                    
                    flowStepMapper.insert(flowStep);
                    flowStepCount++;
                }
            }
        }
        
        result.put("flowtranCount", flowtranCount);
        result.put("flowStepCount", flowStepCount);
        
        log.info("解析完成，交易数：{}, 步骤数：{}", flowtranCount, flowStepCount);
        
        return result;
    }
    
    /**
     * 递归收集所有service和method节点
     */
    private void collectServiceAndMethodNodes(Element parentElement, List<Element> resultList) {
        NodeList children = parentElement.getChildNodes();
        
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String tagName = element.getTagName();
                
                // 如果是service或method节点，直接添加到结果列表
                if ("service".equals(tagName) || "method".equals(tagName)) {
                    resultList.add(element);
                }
                // 其他节点继续递归（确保不遗漏任何嵌套的service/method）
                else {
                    collectServiceAndMethodNodes(element, resultList);
                }
            }
        }
    }
}
