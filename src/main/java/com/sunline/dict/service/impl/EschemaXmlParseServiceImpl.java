package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Eschema;
import com.sunline.dict.entity.EschemaDetail;
import com.sunline.dict.mapper.EschemaDetailMapper;
import com.sunline.dict.mapper.EschemaMapper;
import com.sunline.dict.service.EschemaXmlParseService;
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
 * 枚举类型 XML 解析服务实现
 *
 * 期望的 XML 结构：
 * <pre>
 * &lt;schema id="XXX" longname="XXX" package="XXX"&gt;
 *   &lt;restrictionType id="XXX" longname="XXX" base="XXX"&gt;
 *     &lt;enumeration id="XXX" value="XXX" longname="XXX"/&gt;
 *     ...
 *   &lt;/restrictionType&gt;
 *   ...
 * &lt;/schema&gt;
 * </pre>
 * 每个 enumeration 条目对应 eschema_detail 中的一行记录，携带所属 restrictionType 的信息。
 */
@Service
public class EschemaXmlParseServiceImpl implements EschemaXmlParseService {

    private static final Logger log = LoggerFactory.getLogger(EschemaXmlParseServiceImpl.class);

    @Autowired
    private EschemaMapper eschemaMapper;

    @Autowired
    private EschemaDetailMapper eschemaDetailMapper;

    @Override
    public Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception {
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        return parseAndSave(is, sourceInfo);
    }

    @Override
    public Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception {
        log.info("开始解析 .e_schema.xml，来源: {}", sourceInfo);

        int eschemaCount = 0;
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

        // 先删后插 eschema
        eschemaMapper.deleteById(schemaId);

        Eschema eschema = new Eschema();
        eschema.setId(schemaId);
        eschema.setLongname(schemaLongname);
        eschema.setPackagePath(schemaPkg);
        eschema.setFromJar(sourceInfo);
        eschema.setCreateTime(LocalDateTime.now());
        eschema.setUpdateTime(LocalDateTime.now());
        eschemaMapper.insert(eschema);
        eschemaCount++;

        // 先删除该 schema 下的所有明细
        QueryWrapper<EschemaDetail> delQw = new QueryWrapper<>();
        delQw.eq("eschema_id", schemaId);
        eschemaDetailMapper.delete(delQw);

        // 遍历所有 restrictionType 节点
        NodeList restrictionTypeNodes = root.getElementsByTagName("restrictionType");
        for (int i = 0; i < restrictionTypeNodes.getLength(); i++) {
            Element rtEl = (Element) restrictionTypeNodes.item(i);
            String rtId = rtEl.getAttribute("id");
            String rtLongname = nullIfEmpty(rtEl.getAttribute("longname"));
            String rtBase = nullIfEmpty(rtEl.getAttribute("base"));

            // 遍历该 restrictionType 下的所有 enumeration 节点
            NodeList enumerationNodes = rtEl.getElementsByTagName("enumeration");
            if (enumerationNodes.getLength() == 0) {
                // restrictionType 下无 enumeration 时也插入一条占位记录，保留该类型信息
                EschemaDetail detail = new EschemaDetail();
                detail.setEschemaId(schemaId);
                detail.setRestrictionTypeId(rtId);
                detail.setRestrictionTypeLongname(rtLongname);
                detail.setRestrictionTypeBase(rtBase);
                detail.setCreateTime(LocalDateTime.now());
                detail.setUpdateTime(LocalDateTime.now());
                eschemaDetailMapper.insert(detail);
                detailCount++;
            } else {
                for (int j = 0; j < enumerationNodes.getLength(); j++) {
                    Element enumEl = (Element) enumerationNodes.item(j);

                    EschemaDetail detail = new EschemaDetail();
                    detail.setEschemaId(schemaId);
                    detail.setRestrictionTypeId(rtId);
                    detail.setRestrictionTypeLongname(rtLongname);
                    detail.setRestrictionTypeBase(rtBase);
                    detail.setEnumerationId(nullIfEmpty(enumEl.getAttribute("id")));
                    detail.setEnumerationValue(nullIfEmpty(enumEl.getAttribute("value")));
                    detail.setEnumerationLongname(nullIfEmpty(enumEl.getAttribute("longname")));
                    detail.setCreateTime(LocalDateTime.now());
                    detail.setUpdateTime(LocalDateTime.now());

                    eschemaDetailMapper.insert(detail);
                    detailCount++;
                }
            }
        }

        log.info("解析完成，eschema={}, eschema_detail={}", eschemaCount, detailCount);
        return buildResult(eschemaCount, detailCount);
    }

    @Override
    public int[] deleteBySourceInfo(String sourceInfo) {
        QueryWrapper<Eschema> qw = new QueryWrapper<>();
        qw.eq("from_jar", sourceInfo);
        List<Eschema> list = eschemaMapper.selectList(qw);

        int eDeleted = 0;
        int edDeleted = 0;
        for (Eschema e : list) {
            QueryWrapper<EschemaDetail> dQw = new QueryWrapper<>();
            dQw.eq("eschema_id", e.getId());
            edDeleted += eschemaDetailMapper.delete(dQw);
            eDeleted += eschemaMapper.deleteById(e.getId());
            log.info("已删除来源 {} 的 eschema id={} 及其 eschema_detail", sourceInfo, e.getId());
        }
        if (list.isEmpty()) {
            log.debug("未找到来源 {} 的 eschema 记录，无需删除", sourceInfo);
        }
        return new int[]{eDeleted, edDeleted};
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private Map<String, Object> buildResult(int eschemaCount, int detailCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("eschemaCount", eschemaCount);
        result.put("eschemaDetailCount", detailCount);
        return result;
    }
}
