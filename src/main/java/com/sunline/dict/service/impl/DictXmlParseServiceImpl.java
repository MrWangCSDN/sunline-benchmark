package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.Dict;
import com.sunline.dict.entity.DictDetail;
import com.sunline.dict.mapper.DictDetailMapper;
import com.sunline.dict.mapper.DictMapper;
import com.sunline.dict.service.DictXmlParseService;
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
 * 字典类型 XML 解析服务实现
 *
 * 期望的 XML 结构：
 * <pre>
 * &lt;schema id="XXX" longname="XXX" package="XXX"&gt;
 *   &lt;complexType id="XXX" longname="XXX"&gt;
 *     &lt;element id="XXX" longname="XXX" dbname="XXX" desc="XXX" versionType="XXX" type="XXX"/&gt;
 *     ...
 *   &lt;/complexType&gt;
 *   ...
 * &lt;/schema&gt;
 * </pre>
 */
@Service
public class DictXmlParseServiceImpl implements DictXmlParseService {

    private static final Logger log = LoggerFactory.getLogger(DictXmlParseServiceImpl.class);

    @Autowired
    private DictMapper dictMapper;

    @Autowired
    private DictDetailMapper dictDetailMapper;

    @Override
    public Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception {
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        return parseAndSave(is, sourceInfo);
    }

    @Override
    public Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception {
        log.info("开始解析 .d_schema.xml，来源: {}", sourceInfo);

        int dictCount = 0;
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

        // 先删后插 dict
        dictMapper.deleteById(schemaId);

        Dict dict = new Dict();
        dict.setId(schemaId);
        dict.setLongname(schemaLongname);
        dict.setPackagePath(schemaPkg);
        dict.setFromJar(sourceInfo);
        dict.setCreateTime(LocalDateTime.now());
        dict.setUpdateTime(LocalDateTime.now());
        dictMapper.insert(dict);
        dictCount++;

        // 先删除该 schema 下的所有明细
        QueryWrapper<DictDetail> delQw = new QueryWrapper<>();
        delQw.eq("dict_id", schemaId);
        dictDetailMapper.delete(delQw);

        // 遍历所有 complexType 节点
        NodeList complexTypeNodes = root.getElementsByTagName("complexType");
        for (int i = 0; i < complexTypeNodes.getLength(); i++) {
            Element ctEl = (Element) complexTypeNodes.item(i);
            String ctId = ctEl.getAttribute("id");
            String ctLongname = ctEl.getAttribute("longname");

            // 遍历该 complexType 下的所有 element 节点
            NodeList elementNodes = ctEl.getElementsByTagName("element");
            for (int j = 0; j < elementNodes.getLength(); j++) {
                Element elEl = (Element) elementNodes.item(j);

                DictDetail detail = new DictDetail();
                detail.setDictId(schemaId);
                detail.setDictComplexTypeId(ctId);
                detail.setDictComplexTypeLongname(ctLongname);
                detail.setElementId(elEl.getAttribute("id"));
                detail.setElementLongname(elEl.getAttribute("longname"));

                String dbname = elEl.getAttribute("dbname");
                detail.setElementDbname(dbname.isEmpty() ? null : dbname);

                String desc = elEl.getAttribute("desc");
                detail.setElementDesc(desc.isEmpty() ? null : desc);

                String versionType = elEl.getAttribute("versionType");
                detail.setElementVersionType(versionType.isEmpty() ? null : versionType);

                detail.setElementType(elEl.getAttribute("type"));
                detail.setCreateTime(LocalDateTime.now());
                detail.setUpdateTime(LocalDateTime.now());

                dictDetailMapper.insert(detail);
                detailCount++;
            }
        }

        log.info("解析完成，dict={}, dict_detail={}", dictCount, detailCount);
        return buildResult(dictCount, detailCount);
    }

    @Override
    public int[] deleteBySourceInfo(String sourceInfo) {
        QueryWrapper<Dict> qw = new QueryWrapper<>();
        qw.eq("from_jar", sourceInfo);
        List<Dict> list = dictMapper.selectList(qw);

        int dDeleted = 0;
        int ddDeleted = 0;
        for (Dict d : list) {
            QueryWrapper<DictDetail> dQw = new QueryWrapper<>();
            dQw.eq("dict_id", d.getId());
            ddDeleted += dictDetailMapper.delete(dQw);
            dDeleted += dictMapper.deleteById(d.getId());
            log.info("已删除来源 {} 的 dict id={} 及其 dict_detail", sourceInfo, d.getId());
        }
        if (list.isEmpty()) {
            log.debug("未找到来源 {} 的 dict 记录，无需删除", sourceInfo);
        }
        return new int[]{dDeleted, ddDeleted};
    }

    private Map<String, Object> buildResult(int dictCount, int detailCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("dictCount", dictCount);
        result.put("dictDetailCount", detailCount);
        return result;
    }
}
