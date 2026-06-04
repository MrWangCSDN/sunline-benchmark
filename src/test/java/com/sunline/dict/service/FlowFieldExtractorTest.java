package com.sunline.dict.service;

import com.sunline.dict.service.FlowFieldExtractor.ExtractResult;
import com.sunline.dict.service.FlowFieldExtractor.FieldRow;
import com.sunline.dict.testutil.FlowtransXmlFixtureBuilder;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯 JUnit 单元测试（无 Spring 依赖），验证 FlowFieldExtractor 的解析与去重规则。
 */
class FlowFieldExtractorTest {

    private final FlowFieldExtractor extractor = new FlowFieldExtractor();

    /** 把 XML 字符串解析为 root Element */
    private Element parseXml(String xml) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }

    @Test
    void parses_input_top_level_field() throws Exception {
        String xml = FlowtransXmlFixtureBuilder.newBuilder("TC_TEST", "测试交易")
                .input()
                    .field("unitSetlCardOprnTpCd", "MBaseEnumType.E_X",
                            "MDict.U.unitSetlCardOprnTpCd", "单位结算卡操作类型代码",
                            true, false)
                .build();

        Element root = parseXml(xml);
        ExtractResult result = extractor.extract(root);

        assertEquals(1, result.inputs.size());
        assertEquals(0, result.outputs.size());

        FieldRow f = result.inputs.get(0);
        assertEquals("unitSetlCardOprnTpCd", f.fieldId);
        assertEquals("MBaseEnumType.E_X", f.type);
        assertEquals("MDict.U.unitSetlCardOprnTpCd", f.ref);
        assertEquals("单位结算卡操作类型代码", f.longname);
        assertEquals(Boolean.TRUE, f.required);
        assertEquals(Boolean.FALSE, f.multi);
        assertFalse(f.arrayFlag);
    }

    // ── Task 4 ──────────────────────────────────────────────────────────────

    @Test
    void parses_output_field() throws Exception {
        String xml = FlowtransXmlFixtureBuilder.newBuilder("TC_TEST", "测试交易")
                .output()
                    .field("tlrSeqNum", "MBaseType.U_X",
                            "MDict.T.tlrSeqNum", "柜员流水号",
                            false, false)
                .build();

        Element root = parseXml(xml);
        ExtractResult result = extractor.extract(root);

        assertEquals(0, result.inputs.size());
        assertEquals(1, result.outputs.size());

        FieldRow f = result.outputs.get(0);
        assertEquals("tlrSeqNum", f.fieldId);
        assertEquals("MDict.T.tlrSeqNum", f.ref);
        assertFalse(f.arrayFlag);
    }

    @Test
    void flattens_fields_container() throws Exception {
        // <fields> 容器内的 <field> 应被平铺，容器本身不入结果
        String xml = FlowtransXmlFixtureBuilder.newBuilder("TC_TEST", "测试交易")
                .input()
                    .field("topField", "T1", "MDict.X.topField", "顶层字段", true, false)
                    .fieldsBegin("arrayContainer", "MDict.X.arrayContainer")
                        .field("nestedA", "T2", "MDict.X.nestedA", "嵌套A", false, false)
                        .field("nestedB", "T3", "MDict.X.nestedB", "嵌套B", false, false)
                    .fieldsEnd()
                .build();

        Element root = parseXml(xml);
        ExtractResult result = extractor.extract(root);

        // 顶层 + 2 个嵌套 = 3 条，容器自己不算
        assertEquals(3, result.inputs.size());

        // 容器 id 不应出现
        assertTrue(result.inputs.stream().noneMatch(f -> "arrayContainer".equals(f.fieldId)),
                "<fields> 容器自身不应入结果");

        // 顶层 array_flag=false，嵌套 array_flag=true
        FieldRow top = result.inputs.stream()
                .filter(f -> "topField".equals(f.fieldId)).findFirst().orElseThrow();
        assertFalse(top.arrayFlag);

        FieldRow nestedA = result.inputs.stream()
                .filter(f -> "nestedA".equals(f.fieldId)).findFirst().orElseThrow();
        assertTrue(nestedA.arrayFlag);
    }

    // ── Task 5 ──────────────────────────────────────────────────────────────

    @Test
    void top_level_wins_when_top_appears_first() throws Exception {
        // 顶层先出现，嵌套后出现同 id → 保留顶层
        String xml = FlowtransXmlFixtureBuilder.newBuilder("TC_TEST", "T")
                .input()
                    .field("dupId", "TOP", "MDict.X.top", "顶层版", true, false)
                    .fieldsBegin("container", "MDict.X.container")
                        .field("dupId", "NESTED", "MDict.X.nested", "嵌套版", false, true)
                    .fieldsEnd()
                .build();

        Element root = parseXml(xml);
        ExtractResult result = extractor.extract(root);

        long dupCount = result.inputs.stream().filter(f -> "dupId".equals(f.fieldId)).count();
        assertEquals(1L, dupCount);

        FieldRow kept = result.inputs.stream()
                .filter(f -> "dupId".equals(f.fieldId)).findFirst().orElseThrow();
        assertEquals("TOP", kept.type);
        assertEquals("MDict.X.top", kept.ref);
        assertFalse(kept.arrayFlag);
    }

    @Test
    void top_level_upgrades_when_nested_appears_first() throws Exception {
        // 嵌套先出现，顶层后出现同 id → 顶层升级覆盖嵌套
        String xml = FlowtransXmlFixtureBuilder.newBuilder("TC_TEST", "T")
                .input()
                    .fieldsBegin("container", "MDict.X.container")
                        .field("dupId", "NESTED", "MDict.X.nested", "嵌套版", false, true)
                    .fieldsEnd()
                    .field("dupId", "TOP", "MDict.X.top", "顶层版", true, false)
                .build();

        Element root = parseXml(xml);
        ExtractResult result = extractor.extract(root);

        long dupCount = result.inputs.stream().filter(f -> "dupId".equals(f.fieldId)).count();
        assertEquals(1L, dupCount);

        FieldRow kept = result.inputs.stream()
                .filter(f -> "dupId".equals(f.fieldId)).findFirst().orElseThrow();
        assertEquals("TOP", kept.type);
        assertEquals("MDict.X.top", kept.ref);
        assertFalse(kept.arrayFlag);
    }

    // ── Task 6 ──────────────────────────────────────────────────────────────

    @Test
    void skips_field_without_ref() throws Exception {
        String xml = FlowtransXmlFixtureBuilder.newBuilder("TC_TEST", "T")
                .input()
                    .field("hasRef", "T1", "MDict.X.hasRef", "有 ref", false, false)
                    .fieldNoRef("noRef", "T2")
                .build();

        Element root = parseXml(xml);
        ExtractResult result = extractor.extract(root);

        assertEquals(1, result.inputs.size());
        assertTrue(result.inputs.stream().noneMatch(f -> "noRef".equals(f.fieldId)),
                "无 ref 的 field 应跳过");
    }

    @Test
    void fields_container_itself_not_persisted() throws Exception {
        // <fields id="X" ref="..."> 容器节点自己不应入结果，即使它有 ref
        String xml = FlowtransXmlFixtureBuilder.newBuilder("TC_TEST", "T")
                .input()
                    .fieldsBegin("containerWithRef", "MDict.X.containerWithRef")
                        .field("inner", "T1", "MDict.X.inner", "内层", false, false)
                    .fieldsEnd()
                .build();

        Element root = parseXml(xml);
        ExtractResult result = extractor.extract(root);

        assertEquals(1, result.inputs.size());
        assertEquals("inner", result.inputs.get(0).fieldId);
        assertTrue(result.inputs.stream().noneMatch(f -> "containerWithRef".equals(f.fieldId)));
    }

    // ── Task 7 ──────────────────────────────────────────────────────────────

    @Test
    void handles_empty_input_output() throws Exception {
        // 既没 input 也没 output → 0 条，不抛错
        String xml = FlowtransXmlFixtureBuilder.newBuilder("TC_TEST", "T").build();

        Element root = parseXml(xml);
        ExtractResult result = extractor.extract(root);

        assertEquals(0, result.inputs.size());
        assertEquals(0, result.outputs.size());
    }
}
