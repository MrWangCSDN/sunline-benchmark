package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.ServiceImplFile;
import com.sunline.dict.mapper.ServiceImplFileMapper;
import com.sunline.dict.service.ServiceImplXmlParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务实现 XML 解析服务实现
 *
 * 期望的 XML 结构：
 * <pre>
 * &lt;serviceImpl id="XXX" longname="XXX" package="XXX" serviceType="XXX"/&gt;
 * </pre>
 */
@Service
public class ServiceImplXmlParseServiceImpl implements ServiceImplXmlParseService {

    private static final Logger log = LoggerFactory.getLogger(ServiceImplXmlParseServiceImpl.class);

    @Autowired
    private ServiceImplFileMapper serviceImplFileMapper;

    @Override
    public Map<String, Object> parseAndSave(String xmlContent, String sourceInfo, String serviceImplType) throws Exception {
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        return parseAndSave(is, sourceInfo, serviceImplType);
    }

    @Override
    public Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo, String serviceImplType) throws Exception {
        log.info("开始解析服务实现 XML（类型={}），来源: {}", serviceImplType, sourceInfo);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlContent);

        Element root = doc.getDocumentElement();
        if (!"serviceImpl".equals(root.getTagName())) {
            log.warn("根节点不是 serviceImpl，跳过解析。根节点为: {}", root.getTagName());
            return buildResult(0);
        }

        String id = root.getAttribute("id");
        if (id == null || id.isEmpty()) {
            log.warn("serviceImpl 节点缺少 id 属性，跳过解析。来源: {}", sourceInfo);
            return buildResult(0);
        }

        String longname = nullIfEmpty(root.getAttribute("longname"));
        String packagePath = nullIfEmpty(root.getAttribute("package"));
        String serviceType = nullIfEmpty(root.getAttribute("serviceType"));

        log.info("解析 serviceImpl：id={}, longname={}, serviceType={}", id, longname, serviceType);

        // 先删后插
        serviceImplFileMapper.deleteById(id);

        ServiceImplFile entity = new ServiceImplFile();
        entity.setId(id);
        entity.setLongname(longname);
        entity.setPackagePath(packagePath);
        entity.setServiceType(serviceType);
        entity.setServiceImplType(serviceImplType);
        entity.setFromJar(sourceInfo);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        serviceImplFileMapper.insert(entity);

        log.info("服务实现解析完成，id={}, 类型={}", id, serviceImplType);
        return buildResult(1);
    }

    @Override
    public int deleteBySourceInfo(String sourceInfo) {
        QueryWrapper<ServiceImplFile> qw = new QueryWrapper<>();
        qw.eq("from_jar", sourceInfo);
        int deleted = serviceImplFileMapper.delete(qw);
        if (deleted > 0) {
            log.info("已删除来源 {} 的 serviceImpl 记录 {} 条", sourceInfo, deleted);
        } else {
            log.debug("未找到来源 {} 的 serviceImpl 记录，无需删除", sourceInfo);
        }
        return deleted;
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private Map<String, Object> buildResult(int count) {
        Map<String, Object> result = new HashMap<>();
        result.put("serviceImplCount", count);
        return result;
    }
}
