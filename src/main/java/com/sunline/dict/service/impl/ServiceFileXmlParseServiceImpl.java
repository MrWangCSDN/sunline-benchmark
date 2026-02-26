package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.ServiceDetail;
import com.sunline.dict.entity.ServiceFile;
import com.sunline.dict.mapper.ServiceDetailMapper;
import com.sunline.dict.mapper.ServiceFileMapper;
import com.sunline.dict.service.ServiceFileXmlParseService;
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
 * 服务 XML 解析服务实现
 *
 * 期望的 XML 结构：
 * <pre>
 * &lt;serviceType id="XXX" longname="XXX" package="XXX" kind="XXX" outBound="XXX"&gt;
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
 * 每个 input field 对应一条明细记录（填充 interface_input_* 列），
 * 每个 output field 对应一条明细记录（填充 interface_output_* 列）。
 */
@Service
public class ServiceFileXmlParseServiceImpl implements ServiceFileXmlParseService {

    private static final Logger log = LoggerFactory.getLogger(ServiceFileXmlParseServiceImpl.class);

    @Autowired
    private ServiceFileMapper serviceFileMapper;

    @Autowired
    private ServiceDetailMapper serviceDetailMapper;

    @Override
    public Map<String, Object> parseAndSave(String xmlContent, String sourceInfo, String serviceType) throws Exception {
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        return parseAndSave(is, sourceInfo, serviceType);
    }

    @Override
    public Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo, String serviceType) throws Exception {
        log.info("开始解析服务 XML（类型={}），来源: {}", serviceType, sourceInfo);

        int serviceCount = 0;
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
        String outBound = nullIfEmpty(root.getAttribute("outBound"));

        if (serviceTypeId == null || serviceTypeId.isEmpty()) {
            log.warn("serviceType 节点缺少 id 属性，跳过解析。来源: {}", sourceInfo);
            return buildResult(0, 0);
        }

        log.info("解析 serviceType：id={}, longname={}, kind={}, outBound={}", serviceTypeId, serviceTypeLongname, serviceTypeKind, outBound);

        // 先删后插 service
        serviceFileMapper.deleteById(serviceTypeId);

        ServiceFile serviceFile = new ServiceFile();
        serviceFile.setId(serviceTypeId);
        serviceFile.setLongname(serviceTypeLongname);
        serviceFile.setPackagePath(serviceTypePkg);
        serviceFile.setKind(serviceTypeKind);
        serviceFile.setOutBound(outBound);
        serviceFile.setServiceType(serviceType);
        serviceFile.setFromJar(sourceInfo);
        serviceFile.setCreateTime(LocalDateTime.now());
        serviceFile.setUpdateTime(LocalDateTime.now());
        serviceFileMapper.insert(serviceFile);
        serviceCount++;

        // 先删除该 serviceType 下的所有明细
        QueryWrapper<ServiceDetail> delQw = new QueryWrapper<>();
        delQw.eq("service_type_id", serviceTypeId);
        serviceDetailMapper.delete(delQw);

        // 遍历 serviceType 的直接子 service 节点
        NodeList serviceNodes = root.getElementsByTagName("service");
        for (int i = 0; i < serviceNodes.getLength(); i++) {
            Element serviceEl = (Element) serviceNodes.item(i);
            if (!serviceEl.getParentNode().equals(root)) {
                continue;
            }
            String svcId = serviceEl.getAttribute("id");
            String svcName = nullIfEmpty(serviceEl.getAttribute("name"));
            String svcLongname = nullIfEmpty(serviceEl.getAttribute("longname"));

            NodeList interfaceNodes = serviceEl.getElementsByTagName("interface");
            if (interfaceNodes.getLength() == 0) {
                detailCount += insertPlaceholder(serviceTypeId, svcId, svcName, svcLongname);
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
                    ServiceDetail detail = buildBase(serviceTypeId, svcId, svcName, svcLongname);
                    detail.setInterfaceInputFieldId(nullIfEmpty(fieldEl.getAttribute("id")));
                    detail.setInterfaceInputFieldLongname(nullIfEmpty(fieldEl.getAttribute("longname")));
                    detail.setInterfaceInputFieldType(nullIfEmpty(fieldEl.getAttribute("type")));
                    detail.setInterfaceInputFieldRequired(nullIfEmpty(fieldEl.getAttribute("required")));
                    detail.setInterfaceInputFieldMulti(nullIfEmpty(fieldEl.getAttribute("multi")));
                    serviceDetailMapper.insert(detail);
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
                    ServiceDetail detail = buildBase(serviceTypeId, svcId, svcName, svcLongname);
                    detail.setInterfaceOutputFieldId(nullIfEmpty(fieldEl.getAttribute("id")));
                    detail.setInterfaceOutputFieldLongname(nullIfEmpty(fieldEl.getAttribute("longname")));
                    detail.setInterfaceOutputFieldType(nullIfEmpty(fieldEl.getAttribute("type")));
                    detail.setInterfaceOutputFieldRequired(nullIfEmpty(fieldEl.getAttribute("required")));
                    detail.setInterfaceOutputFieldMulti(nullIfEmpty(fieldEl.getAttribute("multi")));
                    serviceDetailMapper.insert(detail);
                    detailCount++;
                    outputCount++;
                }
            }

            if (inputCount == 0 && outputCount == 0) {
                detailCount += insertPlaceholder(serviceTypeId, svcId, svcName, svcLongname);
            }
        }

        log.info("解析完成，service={}，service_detail={}", serviceCount, detailCount);
        return buildResult(serviceCount, detailCount);
    }

    @Override
    public int[] deleteBySourceInfo(String sourceInfo) {
        QueryWrapper<ServiceFile> qw = new QueryWrapper<>();
        qw.eq("from_jar", sourceInfo);
        List<ServiceFile> list = serviceFileMapper.selectList(qw);

        int sDeleted = 0;
        int dDeleted = 0;
        for (ServiceFile s : list) {
            QueryWrapper<ServiceDetail> dQw = new QueryWrapper<>();
            dQw.eq("service_type_id", s.getId());
            dDeleted += serviceDetailMapper.delete(dQw);
            sDeleted += serviceFileMapper.deleteById(s.getId());
            log.info("已删除来源 {} 的 service id={} 及其 service_detail", sourceInfo, s.getId());
        }
        if (list.isEmpty()) {
            log.debug("未找到来源 {} 的 service 记录，无需删除", sourceInfo);
        }
        return new int[]{sDeleted, dDeleted};
    }

    private ServiceDetail buildBase(String serviceTypeId, String serviceId, String serviceName, String serviceLongname) {
        ServiceDetail detail = new ServiceDetail();
        detail.setServiceTypeId(serviceTypeId);
        detail.setServiceId(serviceId);
        detail.setServiceName(serviceName);
        detail.setServiceLongname(serviceLongname);
        detail.setCreateTime(LocalDateTime.now());
        detail.setUpdateTime(LocalDateTime.now());
        return detail;
    }

    private int insertPlaceholder(String serviceTypeId, String serviceId, String serviceName, String serviceLongname) {
        ServiceDetail detail = buildBase(serviceTypeId, serviceId, serviceName, serviceLongname);
        serviceDetailMapper.insert(detail);
        return 1;
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private Map<String, Object> buildResult(int serviceCount, int detailCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("serviceCount", serviceCount);
        result.put("serviceDetailCount", detailCount);
        return result;
    }
}
