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
}
