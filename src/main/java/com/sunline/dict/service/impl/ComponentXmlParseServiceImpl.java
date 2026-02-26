package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Component;
import com.sunline.dict.entity.ComponentDetail;
import com.sunline.dict.mapper.ComponentDetailMapper;
import com.sunline.dict.mapper.ComponentMapper;
import com.sunline.dict.service.ComponentXmlParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 构件 XML 解析服务实现
 *
 * 期望的 XML 结构：
 * <pre>
 * &lt;serviceType id="XXX" longname="XXX" package="XXX" kind="XXX"&gt;
 *   &lt;service id="XXX" name="XXX" longname="XXX"&gt;
 *     &lt;interface&gt;
 *       &lt;input&gt;
 *         &lt;field id="XXX" longname="XXX" type="XXX" required="XXX" multi="XXX"/&gt;
 *       &lt;/input&gt;
 *       &lt;output&gt;
 *         &lt;field id="XXX" longname="XXX" type="XXX" required="XXX" multi="XXX"/&gt;
 *       &lt;/output&gt;
 *     &lt;/interface&gt;
 *   &lt;/service&gt;
 * &lt;/serviceType&gt;
 * </pre>
 *
 * 每个 input field 对应一条明细记录（填充 interface_input_* 列，output 列为空），
 * 每个 output field 对应一条明细记录（填充 interface_output_* 列，input 列为空）。
 * 若某 service 下 input/output 均无 field，仍会插入一条仅含 service 信息的占位记录。
 */
@Service
public class ComponentXmlParseServiceImpl implements ComponentXmlParseService {

    private static final Logger log = LoggerFactory.getLogger(ComponentXmlParseServiceImpl.class);

    @Autowired
    private ComponentMapper componentMapper;

    @Autowired
    private ComponentDetailMapper componentDetailMapper;

    @Override
    public Map<String, Object> parseAndSave(String xmlContent, String sourceInfo, String componentType) throws Exception {
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        return parseAndSave(is, sourceInfo, componentType);
    }

    @Override
    public Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo, String componentType) throws Exception {
        log.info("开始解析构件 XML（类型={}），来源: {}", componentType, sourceInfo);

        int componentCount = 0;
        int detailCount = 0;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlContent);

        Element root = doc.getDocumentElement();
        if (!"serviceType".equals(root.getTagName())) {
            log.warn("根节点不是 serviceType，跳过解析。根节点为: {}", root.getTagName());
            return buildResult(0, 0);
        }

        String serviceTypeId = root.getAttribute("id");
        String serviceTypeLongname = root.getAttribute("longname");
        String serviceTypePkg = root.getAttribute("package");
        String serviceTypeKind = nullIfEmpty(root.getAttribute("kind"));

        if (serviceTypeId == null || serviceTypeId.isEmpty()) {
            log.warn("serviceType 节点缺少 id 属性，跳过解析。来源: {}", sourceInfo);
            return buildResult(0, 0);
        }

        log.info("解析 serviceType：id={}, longname={}, kind={}", serviceTypeId, serviceTypeLongname, serviceTypeKind);

        // 先删后插 component
        componentMapper.deleteById(serviceTypeId);

        Component component = new Component();
        component.setId(serviceTypeId);
        component.setLongname(serviceTypeLongname);
        component.setPackagePath(serviceTypePkg);
        component.setKind(serviceTypeKind);
        component.setComponentType(componentType);
        component.setFromJar(sourceInfo);
        component.setCreateTime(LocalDateTime.now());
        component.setUpdateTime(LocalDateTime.now());
        componentMapper.insert(component);
        componentCount++;

        // 先删除该 component 下的所有明细
        QueryWrapper<ComponentDetail> delQw = new QueryWrapper<>();
        delQw.eq("component_id", serviceTypeId);
        componentDetailMapper.delete(delQw);

        // 遍历所有 service 节点
        NodeList serviceNodes = root.getElementsByTagName("service");
        for (int i = 0; i < serviceNodes.getLength(); i++) {
            Element serviceEl = (Element) serviceNodes.item(i);
            // 只处理 serviceType 的直接子 service，跳过嵌套节点
            if (!serviceEl.getParentNode().equals(root)) {
                continue;
            }
            String serviceId = serviceEl.getAttribute("id");
            String serviceName = nullIfEmpty(serviceEl.getAttribute("name"));
            String serviceLongname = nullIfEmpty(serviceEl.getAttribute("longname"));

            // 找 interface 节点
            NodeList interfaceNodes = serviceEl.getElementsByTagName("interface");
            if (interfaceNodes.getLength() == 0) {
                // 无 interface，插入一条仅含 service 信息的占位记录
                detailCount += insertServicePlaceholder(serviceTypeId, serviceId, serviceName, serviceLongname);
                continue;
            }
            Element interfaceEl = (Element) interfaceNodes.item(0);

            // ——— 解析 input/field ———
            int inputCount = 0;
            NodeList inputContainers = interfaceEl.getElementsByTagName("input");
            if (inputContainers.getLength() > 0) {
                Element inputEl = (Element) inputContainers.item(0);
                NodeList fieldNodes = inputEl.getElementsByTagName("field");
                for (int j = 0; j < fieldNodes.getLength(); j++) {
                    Element fieldEl = (Element) fieldNodes.item(j);
                    ComponentDetail detail = buildBase(serviceTypeId, serviceId, serviceName, serviceLongname);
                    detail.setInterfaceInputFieldId(nullIfEmpty(fieldEl.getAttribute("id")));
                    detail.setInterfaceInputFieldLongname(nullIfEmpty(fieldEl.getAttribute("longname")));
                    detail.setInterfaceInputFieldType(nullIfEmpty(fieldEl.getAttribute("type")));
                    detail.setInterfaceInputFieldRequired(nullIfEmpty(fieldEl.getAttribute("required")));
                    detail.setInterfaceInputFieldMulti(nullIfEmpty(fieldEl.getAttribute("multi")));
                    componentDetailMapper.insert(detail);
                    detailCount++;
                    inputCount++;
                }
            }

            // ——— 解析 output/field ———
            int outputCount = 0;
            NodeList outputContainers = interfaceEl.getElementsByTagName("output");
            if (outputContainers.getLength() > 0) {
                Element outputEl = (Element) outputContainers.item(0);
                NodeList fieldNodes = outputEl.getElementsByTagName("field");
                for (int j = 0; j < fieldNodes.getLength(); j++) {
                    Element fieldEl = (Element) fieldNodes.item(j);
                    ComponentDetail detail = buildBase(serviceTypeId, serviceId, serviceName, serviceLongname);
                    detail.setInterfaceOutputFieldId(nullIfEmpty(fieldEl.getAttribute("id")));
                    detail.setInterfaceOutputFieldLongname(nullIfEmpty(fieldEl.getAttribute("longname")));
                    detail.setInterfaceOutputFieldType(nullIfEmpty(fieldEl.getAttribute("type")));
                    detail.setInterfaceOutputFieldRequired(nullIfEmpty(fieldEl.getAttribute("required")));
                    detail.setInterfaceOutputFieldMulti(nullIfEmpty(fieldEl.getAttribute("multi")));
                    componentDetailMapper.insert(detail);
                    detailCount++;
                    outputCount++;
                }
            }

            // input 和 output 均无 field 时插入占位记录
            if (inputCount == 0 && outputCount == 0) {
                detailCount += insertServicePlaceholder(serviceTypeId, serviceId, serviceName, serviceLongname);
            }
        }

        log.info("解析完成，component={}，component_detail={}", componentCount, detailCount);
        return buildResult(componentCount, detailCount);
    }

    @Override
    public int[] deleteBySourceInfo(String sourceInfo) {
        QueryWrapper<Component> qw = new QueryWrapper<>();
        qw.eq("from_jar", sourceInfo);
        List<Component> list = componentMapper.selectList(qw);

        int cDeleted = 0;
        int dDeleted = 0;
        for (Component c : list) {
            QueryWrapper<ComponentDetail> dQw = new QueryWrapper<>();
            dQw.eq("component_id", c.getId());
            dDeleted += componentDetailMapper.delete(dQw);
            cDeleted += componentMapper.deleteById(c.getId());
            log.info("已删除来源 {} 的 component id={} 及其 component_detail", sourceInfo, c.getId());
        }
        if (list.isEmpty()) {
            log.debug("未找到来源 {} 的 component 记录，无需删除", sourceInfo);
        }
        return new int[]{cDeleted, dDeleted};
    }

    private ComponentDetail buildBase(String componentId, String serviceId, String serviceName, String serviceLongname) {
        ComponentDetail detail = new ComponentDetail();
        detail.setComponentId(componentId);
        detail.setServiceId(serviceId);
        detail.setServiceName(serviceName);
        detail.setServiceLongname(serviceLongname);
        detail.setCreateTime(LocalDateTime.now());
        detail.setUpdateTime(LocalDateTime.now());
        return detail;
    }

    private int insertServicePlaceholder(String componentId, String serviceId, String serviceName, String serviceLongname) {
        ComponentDetail detail = buildBase(componentId, serviceId, serviceName, serviceLongname);
        componentDetailMapper.insert(detail);
        return 1;
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private Map<String, Object> buildResult(int componentCount, int detailCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("componentCount", componentCount);
        result.put("componentDetailCount", detailCount);
        return result;
    }
}
