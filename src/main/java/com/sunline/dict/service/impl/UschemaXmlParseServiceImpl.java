package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Uschema;
import com.sunline.dict.entity.UschemaDetail;
import com.sunline.dict.mapper.UschemaDetailMapper;
import com.sunline.dict.mapper.UschemaMapper;
import com.sunline.dict.service.UschemaXmlParseService;
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
 * 自定义类型 XML 解析服务实现
 *
 * 期望的 XML 结构：
 * <pre>
 * &lt;schema id="XXX" longname="XXX" package="XXX"&gt;
 *   &lt;restrictionType id="XXX" longname="XXX" base="XXX" minLength="XXX"
 *                   maxLength="XXX" fractionDigits="XXX" dbLength="XXX"/&gt;
 *   ...
 * &lt;/schema&gt;
 * </pre>
 */
@Service
public class UschemaXmlParseServiceImpl implements UschemaXmlParseService {

    private static final Logger log = LoggerFactory.getLogger(UschemaXmlParseServiceImpl.class);

    @Autowired
    private UschemaMapper uschemaMapper;

    @Autowired
    private UschemaDetailMapper uschemaDetailMapper;

    @Override
    public Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception {
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        return parseAndSave(is, sourceInfo);
    }

    @Override
    public Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception {
        log.info("开始解析 .u_schema.xml，来源: {}", sourceInfo);

        int uschemaCount = 0;
        int detailCount = 0;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlContent);

        Element root = doc.getDocumentElement();
        if (!"schema".equals(root.getTagName())) {
            log.warn("根节点不是 schema，跳过解析。根节点为: {}", root.getTagName());
            return buildResult(0, 0);
        }

        String schemaId = root.getAttribute("id");
        String schemaLongname = root.getAttribute("longname");
        String schemaPkg = root.getAttribute("package");

        if (schemaId == null || schemaId.isEmpty()) {
            log.warn("schema 节点缺少 id 属性，跳过解析。来源: {}", sourceInfo);
            return buildResult(0, 0);
        }

        log.info("解析 schema：id={}, longname={}, package={}", schemaId, schemaLongname, schemaPkg);

        // 先删后插 uschema
        uschemaMapper.deleteById(schemaId);

        Uschema uschema = new Uschema();
        uschema.setId(schemaId);
        uschema.setLongname(schemaLongname);
        uschema.setPackagePath(schemaPkg);
        uschema.setFromJar(sourceInfo);
        uschema.setCreateTime(LocalDateTime.now());
        uschema.setUpdateTime(LocalDateTime.now());
        uschemaMapper.insert(uschema);
        uschemaCount++;

        // 先删除该 schema 下的所有明细
        QueryWrapper<UschemaDetail> delQw = new QueryWrapper<>();
        delQw.eq("uschema_id", schemaId);
        uschemaDetailMapper.delete(delQw);

        // 遍历所有 restrictionType 节点
        NodeList restrictionTypeNodes = root.getElementsByTagName("restrictionType");
        for (int i = 0; i < restrictionTypeNodes.getLength(); i++) {
            Element rtEl = (Element) restrictionTypeNodes.item(i);

            UschemaDetail detail = new UschemaDetail();
            detail.setUschemaId(schemaId);
            detail.setRestrictionTypeId(rtEl.getAttribute("id"));
            detail.setRestrictionTypeLongname(rtEl.getAttribute("longname"));

            detail.setRestrictionTypeBase(nullIfEmpty(rtEl.getAttribute("base")));
            detail.setRestrictionTypeMinLength(nullIfEmpty(rtEl.getAttribute("minLength")));
            detail.setRestrictionTypeMaxLength(nullIfEmpty(rtEl.getAttribute("maxLength")));
            detail.setRestrictionTypeFractionDigits(nullIfEmpty(rtEl.getAttribute("fractionDigits")));
            detail.setRestrictionTypeDbLength(nullIfEmpty(rtEl.getAttribute("dbLength")));

            detail.setCreateTime(LocalDateTime.now());
            detail.setUpdateTime(LocalDateTime.now());

            uschemaDetailMapper.insert(detail);
            detailCount++;
        }

        log.info("解析完成，uschema={}, uschema_detail={}", uschemaCount, detailCount);
        return buildResult(uschemaCount, detailCount);
    }

    @Override
    public int[] deleteBySourceInfo(String sourceInfo) {
        QueryWrapper<Uschema> qw = new QueryWrapper<>();
        qw.eq("from_jar", sourceInfo);
        List<Uschema> list = uschemaMapper.selectList(qw);

        int uDeleted = 0;
        int udDeleted = 0;
        for (Uschema u : list) {
            QueryWrapper<UschemaDetail> dQw = new QueryWrapper<>();
            dQw.eq("uschema_id", u.getId());
            udDeleted += uschemaDetailMapper.delete(dQw);
            uDeleted += uschemaMapper.deleteById(u.getId());
            log.info("已删除来源 {} 的 uschema id={} 及其 uschema_detail", sourceInfo, u.getId());
        }
        if (list.isEmpty()) {
            log.debug("未找到来源 {} 的 uschema 记录，无需删除", sourceInfo);
        }
        return new int[]{uDeleted, udDeleted};
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private Map<String, Object> buildResult(int uschemaCount, int detailCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("uschemaCount", uschemaCount);
        result.put("uschemaDetailCount", detailCount);
        return result;
    }
}
