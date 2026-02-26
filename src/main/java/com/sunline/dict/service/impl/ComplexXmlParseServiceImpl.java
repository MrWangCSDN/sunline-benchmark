package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Complex;
import com.sunline.dict.entity.ComplexDetail;
import com.sunline.dict.mapper.ComplexDetailMapper;
import com.sunline.dict.mapper.ComplexMapper;
import com.sunline.dict.service.ComplexXmlParseService;
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
 * 复合类型 XML 解析服务实现
 *
 * 期望的 XML 结构：
 * <pre>
 * &lt;schema id="XXX" longname="XXX" package="XXX"&gt;
 *   &lt;complexType id="XXX" longname="XXX"&gt;
 *     &lt;element id="XXX" longname="XXX" required="true" multi="false" ref="XXX" type="XXX"/&gt;
 *     ...
 *   &lt;/complexType&gt;
 *   ...
 * &lt;/schema&gt;
 * </pre>
 */
@Service
public class ComplexXmlParseServiceImpl implements ComplexXmlParseService {

    private static final Logger log = LoggerFactory.getLogger(ComplexXmlParseServiceImpl.class);

    @Autowired
    private ComplexMapper complexMapper;

    @Autowired
    private ComplexDetailMapper complexDetailMapper;

    @Override
    public Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception {
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        return parseAndSave(is, sourceInfo);
    }

    @Override
    public Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception {
        log.info("开始解析 .c_schema.xml，来源: {}", sourceInfo);

        int complexCount = 0;
        int detailCount = 0;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlContent);

        Element root = doc.getDocumentElement();
        // 兼容根节点为 schema 的情况
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

        // ——— 先删后插 complex ———
        complexMapper.deleteById(schemaId);

        Complex complex = new Complex();
        complex.setId(schemaId);
        complex.setLongname(schemaLongname);
        complex.setPackagePath(schemaPkg);
        complex.setFromJar(sourceInfo);
        complex.setCreateTime(LocalDateTime.now());
        complex.setUpdateTime(LocalDateTime.now());
        complexMapper.insert(complex);
        complexCount++;

        // ——— 先删除该 schema 下的所有明细 ———
        QueryWrapper<ComplexDetail> delQw = new QueryWrapper<>();
        delQw.eq("complex_id", schemaId);
        complexDetailMapper.delete(delQw);

        // ——— 遍历所有 complexType 节点 ———
        NodeList complexTypeNodes = root.getElementsByTagName("complexType");
        for (int i = 0; i < complexTypeNodes.getLength(); i++) {
            Element ctEl = (Element) complexTypeNodes.item(i);
            String ctId = ctEl.getAttribute("id");
            String ctLongname = ctEl.getAttribute("longname");

            // 遍历该 complexType 下的所有 element 节点
            NodeList elementNodes = ctEl.getElementsByTagName("element");
            for (int j = 0; j < elementNodes.getLength(); j++) {
                Element elEl = (Element) elementNodes.item(j);

                ComplexDetail detail = new ComplexDetail();
                detail.setComplexId(schemaId);
                detail.setComplexPojoId(ctId);
                detail.setComplexPojoLongname(ctLongname);
                detail.setElementId(elEl.getAttribute("id"));
                detail.setElementLongname(elEl.getAttribute("longname"));
                detail.setElementRequired(elEl.getAttribute("required"));
                detail.setElementMulti(elEl.getAttribute("multi"));
                detail.setElementRef(elEl.getAttribute("ref"));
                detail.setElementType(elEl.getAttribute("type"));
                detail.setCreateTime(LocalDateTime.now());
                detail.setUpdateTime(LocalDateTime.now());

                complexDetailMapper.insert(detail);
                detailCount++;
            }
        }

        log.info("解析完成，complex={}, complex_detail={}", complexCount, detailCount);
        return buildResult(complexCount, detailCount);
    }

    @Override
    public int[] deleteBySourceInfo(String sourceInfo) {
        QueryWrapper<Complex> qw = new QueryWrapper<>();
        qw.eq("from_jar", sourceInfo);
        List<Complex> list = complexMapper.selectList(qw);

        int cDeleted = 0;
        int dDeleted = 0;
        for (Complex c : list) {
            QueryWrapper<ComplexDetail> dQw = new QueryWrapper<>();
            dQw.eq("complex_id", c.getId());
            dDeleted += complexDetailMapper.delete(dQw);
            cDeleted += complexMapper.deleteById(c.getId());
            log.info("已删除来源 {} 的 complex id={} 及其 complex_detail", sourceInfo, c.getId());
        }
        if (list.isEmpty()) {
            log.debug("未找到来源 {} 的 complex 记录，无需删除", sourceInfo);
        }
        return new int[]{cDeleted, dDeleted};
    }

    private Map<String, Object> buildResult(int complexCount, int detailCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("complexCount", complexCount);
        result.put("complexDetailCount", detailCount);
        return result;
    }
}
