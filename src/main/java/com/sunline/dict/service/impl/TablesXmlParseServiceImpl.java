package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.MetadataTables;
import com.sunline.dict.entity.MetadataTablesDetail;
import com.sunline.dict.entity.MetadataTablesIndexes;
import com.sunline.dict.mapper.MetadataTablesDetailMapper;
import com.sunline.dict.mapper.MetadataTablesIndexesMapper;
import com.sunline.dict.mapper.MetadataTablesMapper;
import com.sunline.dict.service.TablesXmlParseService;
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
 * 表定义 XML 解析服务实现
 *
 * 期望的 XML 结构：
 * <pre>
 * &lt;schema id="XXX" longname="XXX" package="XXX"&gt;
 *   &lt;table id="XXX" name="XXX" longname="XXX" extension="XXX"&gt;
 *     &lt;fields&gt;
     *       &lt;field id="XXX" dbname="XXX" longname="XXX" type="XXX"
 *              nullable="XXX" primarykey="XXX" ref="XXX"/&gt;
 *     &lt;/fields&gt;
 *     &lt;odbindexes&gt;
 *       &lt;index id="XXX" type="XXX" fields="XXX" operate="XXX"/&gt;
 *     &lt;/odbindexes&gt;
 *     &lt;indexes&gt;
 *       &lt;index id="XXX" type="XXX" fields="XXX"/&gt;
 *     &lt;/indexes&gt;
 *   &lt;/table&gt;
 * &lt;/schema&gt;
 * </pre>
 */
@Service
public class TablesXmlParseServiceImpl implements TablesXmlParseService {

    private static final Logger log = LoggerFactory.getLogger(TablesXmlParseServiceImpl.class);

    @Autowired
    private MetadataTablesMapper metadataTablesMapper;

    @Autowired
    private MetadataTablesDetailMapper metadataTablesDetailMapper;

    @Autowired
    private MetadataTablesIndexesMapper metadataTablesIndexesMapper;

    @Override
    public Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception {
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        return parseAndSave(is, sourceInfo);
    }

    @Override
    public Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception {
        log.info("开始解析 .tables.xml，来源: {}", sourceInfo);

        int tablesCount = 0;
        int detailCount = 0;
        int indexesCount = 0;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(xmlContent);

        Element root = doc.getDocumentElement();
        if (!"schema".equals(root.getTagName())) {
            log.warn("根节点不是 schema，跳过解析。根节点为: {}", root.getTagName());
            return buildResult(0, 0, 0);
        }

        String schemaId = root.getAttribute("id");
        String schemaLongname = root.getAttribute("longname");
        String schemaPkg = root.getAttribute("package");

        if (schemaId == null || schemaId.isEmpty()) {
            log.warn("schema 节点缺少 id 属性，跳过解析。来源: {}", sourceInfo);
            return buildResult(0, 0, 0);
        }

        log.info("解析 schema：id={}, longname={}, package={}", schemaId, schemaLongname, schemaPkg);

        // 先删后插 metadata_tables
        metadataTablesMapper.deleteById(schemaId);

        MetadataTables metadataTables = new MetadataTables();
        metadataTables.setId(schemaId);
        metadataTables.setLongname(schemaLongname);
        metadataTables.setPackagePath(schemaPkg);
        metadataTables.setFromJar(sourceInfo);
        metadataTables.setCreateTime(LocalDateTime.now());
        metadataTables.setUpdateTime(LocalDateTime.now());
        metadataTablesMapper.insert(metadataTables);
        tablesCount++;

        // 先删除该 schema 下的所有字段明细与索引
        QueryWrapper<MetadataTablesDetail> delDetailQw = new QueryWrapper<>();
        delDetailQw.eq("metadata_tables_id", schemaId);
        metadataTablesDetailMapper.delete(delDetailQw);

        QueryWrapper<MetadataTablesIndexes> delIndexQw = new QueryWrapper<>();
        delIndexQw.eq("metadata_tables_id", schemaId);
        metadataTablesIndexesMapper.delete(delIndexQw);

        // 遍历所有 table 节点
        NodeList tableNodes = root.getElementsByTagName("table");
        for (int i = 0; i < tableNodes.getLength(); i++) {
            Element tableEl = (Element) tableNodes.item(i);
            String tableId = tableEl.getAttribute("id");
            String tableName = nullIfEmpty(tableEl.getAttribute("name"));
            String tableLongname = nullIfEmpty(tableEl.getAttribute("longname"));
            String tableExtension = nullIfEmpty(tableEl.getAttribute("extension"));

            // ——— 解析 fields/field ———
            NodeList fieldsContainers = tableEl.getElementsByTagName("fields");
            if (fieldsContainers.getLength() > 0) {
                Element fieldsEl = (Element) fieldsContainers.item(0);
                NodeList fieldNodes = fieldsEl.getElementsByTagName("field");
                for (int j = 0; j < fieldNodes.getLength(); j++) {
                    Element fieldEl = (Element) fieldNodes.item(j);

                    MetadataTablesDetail detail = new MetadataTablesDetail();
                    detail.setMetadataTablesId(schemaId);
                    detail.setTableId(tableId);
                    detail.setTableName(tableName);
                    detail.setTableLongname(tableLongname);
                    detail.setTableExtension(tableExtension);
                    detail.setFieldId(nullIfEmpty(fieldEl.getAttribute("id")));
                    detail.setFieldDbname(nullIfEmpty(fieldEl.getAttribute("dbname")));
                    detail.setFieldLongname(nullIfEmpty(fieldEl.getAttribute("longname")));
                    detail.setFieldType(nullIfEmpty(fieldEl.getAttribute("type")));
                    detail.setFieldNullable(nullIfEmpty(fieldEl.getAttribute("nullable")));
                    detail.setFieldPrimarykey(nullIfEmpty(fieldEl.getAttribute("primarykey")));
                    detail.setFieldRef(nullIfEmpty(fieldEl.getAttribute("ref")));
                    detail.setCreateTime(LocalDateTime.now());
                    detail.setUpdateTime(LocalDateTime.now());

                    metadataTablesDetailMapper.insert(detail);
                    detailCount++;
                }
            }

            // ——— 解析 odbindexes/index ———
            NodeList odbIndexesContainers = tableEl.getElementsByTagName("odbindexes");
            if (odbIndexesContainers.getLength() > 0) {
                Element odbIndexesEl = (Element) odbIndexesContainers.item(0);
                NodeList odbIndexNodes = odbIndexesEl.getElementsByTagName("index");
                for (int j = 0; j < odbIndexNodes.getLength(); j++) {
                    Element idxEl = (Element) odbIndexNodes.item(j);

                    MetadataTablesIndexes idx = new MetadataTablesIndexes();
                    idx.setMetadataTablesId(schemaId);
                    idx.setTableId(tableId);
                    idx.setOdbindexId(nullIfEmpty(idxEl.getAttribute("id")));
                    idx.setOdbindexType(nullIfEmpty(idxEl.getAttribute("type")));
                    idx.setOdbindexFields(nullIfEmpty(idxEl.getAttribute("fields")));
                    idx.setOdbindexOperate(nullIfEmpty(idxEl.getAttribute("operate")));
                    idx.setCreateTime(LocalDateTime.now());
                    idx.setUpdateTime(LocalDateTime.now());

                    metadataTablesIndexesMapper.insert(idx);
                    indexesCount++;
                }
            }

            // ——— 解析 indexes/index ———
            NodeList indexesContainers = tableEl.getElementsByTagName("indexes");
            if (indexesContainers.getLength() > 0) {
                Element indexesEl = (Element) indexesContainers.item(0);
                NodeList indexNodes = indexesEl.getElementsByTagName("index");
                for (int j = 0; j < indexNodes.getLength(); j++) {
                    Element idxEl = (Element) indexNodes.item(j);

                    MetadataTablesIndexes idx = new MetadataTablesIndexes();
                    idx.setMetadataTablesId(schemaId);
                    idx.setTableId(tableId);
                    idx.setIndexId(nullIfEmpty(idxEl.getAttribute("id")));
                    idx.setIndexType(nullIfEmpty(idxEl.getAttribute("type")));
                    idx.setIndexFields(nullIfEmpty(idxEl.getAttribute("fields")));
                    idx.setCreateTime(LocalDateTime.now());
                    idx.setUpdateTime(LocalDateTime.now());

                    metadataTablesIndexesMapper.insert(idx);
                    indexesCount++;
                }
            }
        }

        log.info("解析完成，metadata_tables={}，detail={}，indexes={}", tablesCount, detailCount, indexesCount);
        return buildResult(tablesCount, detailCount, indexesCount);
    }

    @Override
    public int[] deleteBySourceInfo(String sourceInfo) {
        QueryWrapper<MetadataTables> qw = new QueryWrapper<>();
        qw.eq("from_jar", sourceInfo);
        List<MetadataTables> list = metadataTablesMapper.selectList(qw);

        int tDeleted = 0;
        int dDeleted = 0;
        int iDeleted = 0;
        for (MetadataTables t : list) {
            QueryWrapper<MetadataTablesDetail> dQw = new QueryWrapper<>();
            dQw.eq("metadata_tables_id", t.getId());
            dDeleted += metadataTablesDetailMapper.delete(dQw);

            QueryWrapper<MetadataTablesIndexes> iQw = new QueryWrapper<>();
            iQw.eq("metadata_tables_id", t.getId());
            iDeleted += metadataTablesIndexesMapper.delete(iQw);

            tDeleted += metadataTablesMapper.deleteById(t.getId());
            log.info("已删除来源 {} 的 metadata_tables id={} 及其明细与索引", sourceInfo, t.getId());
        }
        if (list.isEmpty()) {
            log.debug("未找到来源 {} 的 metadata_tables 记录，无需删除", sourceInfo);
        }
        return new int[]{tDeleted, dDeleted, iDeleted};
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private Map<String, Object> buildResult(int tablesCount, int detailCount, int indexesCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("tablesCount", tablesCount);
        result.put("detailCount", detailCount);
        result.put("indexesCount", indexesCount);
        return result;
    }
}
