# 交易字段明细表 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 sunline-benchmark 项目新增 `flow_field_detail` 表，把 `.flowtrans.xml` 里 `<input>` / `<output>` 下所有有 `ref` 的叶子 `<field>` 平铺入库（按 `(flow_id, io_type, field_id)` 唯一、顶层优先去重），同时挂到 `FlowXmlParseServiceImpl` 和 `JarScanServiceImpl` 让 Webhook / XmlScan / JarScan 三路自动覆盖，并提供"全量重扫 + by-flow 查询"API。

**Architecture:** 在已有 `FlowXmlParseServiceImpl.parseAndSave` 末尾增加 1 行调用新的 `FlowFieldDetailService.extractAndSave(rootEl, flowId, sourceInfo)`，`JarScanServiceImpl.parseFlowtranXml` 末尾同理。新表 `flow_field_detail` 独立 Entity / Mapper。全量重扫 `FlowFieldRescanService` 异步执行，进度用 `ConcurrentHashMap` 维护。

**Tech Stack:** Java 17 + Spring Boot 3.1.5 + MyBatis-Plus + 原生 `org.w3c.dom`（与现有 FlowXmlParseService 一致）+ JUnit 5 + OpenGauss/PostgreSQL

**Spec reference:** `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易字段明细表-设计.md`

---

## File Structure

**Backend (Java)**:
- Create: `src/main/java/com/sunline/dict/entity/FlowFieldDetail.java`
- Create: `src/main/java/com/sunline/dict/mapper/FlowFieldDetailMapper.java`
- Create: `src/main/java/com/sunline/dict/service/FlowFieldDetailService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImpl.java`
- Create: `src/main/java/com/sunline/dict/service/FlowFieldRescanService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/FlowFieldRescanServiceImpl.java`
- Create: `src/main/java/com/sunline/dict/controller/FlowFieldController.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/FlowXmlParseServiceImpl.java` — 加 1 行调用 + 注入字段
- Modify: `src/main/java/com/sunline/dict/service/impl/JarScanServiceImpl.java` — 加 1 行调用 + 注入字段

**SQL**:
- Create: `src/main/resources/sql/create_flow_field_detail.sql`

**Tests**:
- Create: `src/test/java/com/sunline/dict/testutil/FlowtransXmlFixtureBuilder.java`
- Create: `src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java`

---

## Task 1: 表结构 + Entity + Mapper（骨架）

**Files:**
- Create: `src/main/resources/sql/create_flow_field_detail.sql`
- Create: `src/main/java/com/sunline/dict/entity/FlowFieldDetail.java`
- Create: `src/main/java/com/sunline/dict/mapper/FlowFieldDetailMapper.java`

- [ ] **Step 1.1: 创建表结构 SQL**

Create `src/main/resources/sql/create_flow_field_detail.sql`:

```sql
-- flow_field_detail：.flowtrans.xml 的 input/output 字段平铺表
CREATE TABLE IF NOT EXISTS flow_field_detail (
    id              BIGSERIAL    PRIMARY KEY,
    flow_id         VARCHAR(64)  NOT NULL,
    io_type         VARCHAR(8)   NOT NULL,
    field_id        VARCHAR(128) NOT NULL,
    field_type      VARCHAR(256),
    longname        VARCHAR(512),
    ref             VARCHAR(256) NOT NULL,
    required        BOOLEAN,
    multi           BOOLEAN,
    array_flag      BOOLEAN      NOT NULL DEFAULT FALSE,
    source_info     VARCHAR(512),
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ffd_flow_io_field UNIQUE (flow_id, io_type, field_id)
);

CREATE INDEX IF NOT EXISTS idx_ffd_flow_io ON flow_field_detail (flow_id, io_type);
CREATE INDEX IF NOT EXISTS idx_ffd_ref     ON flow_field_detail (ref);

COMMENT ON TABLE  flow_field_detail            IS '交易字段明细表：.flowtrans.xml input/output 平铺';
COMMENT ON COLUMN flow_field_detail.flow_id    IS '关联 flowtran.id';
COMMENT ON COLUMN flow_field_detail.io_type    IS 'input | output';
COMMENT ON COLUMN flow_field_detail.field_id   IS '<field id="...">';
COMMENT ON COLUMN flow_field_detail.ref        IS 'MDict.X.yyy（无 ref 不入库）';
COMMENT ON COLUMN flow_field_detail.array_flag IS 'true=该 field 出现在 <fields> 容器内';
```

- [ ] **Step 1.2: 创建 Entity**

Create `src/main/java/com/sunline/dict/entity/FlowFieldDetail.java`:

```java
package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易字段明细表（flow_field_detail）
 * 来源：.flowtrans.xml 的 input/output 平铺
 */
@TableName("flow_field_detail")
public class FlowFieldDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 flowtran.id */
    private String flowId;

    /** 'input' | 'output' */
    private String ioType;

    /** XML <field id="..."> */
    private String fieldId;

    /** type 属性 */
    private String fieldType;

    /** longname 属性 */
    private String longname;

    /** MDict.X.yyy（无 ref 跳过整行） */
    private String ref;

    /** required="true"/"false" */
    private Boolean required;

    /** multi="true"/"false" */
    private Boolean multi;

    /** true = 来自 <fields> 容器内 */
    private Boolean arrayFlag;

    /** 来源信息 */
    private String sourceInfo;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }

    public String getIoType() { return ioType; }
    public void setIoType(String ioType) { this.ioType = ioType; }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public String getFieldType() { return fieldType; }
    public void setFieldType(String fieldType) { this.fieldType = fieldType; }

    public String getLongname() { return longname; }
    public void setLongname(String longname) { this.longname = longname; }

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }

    public Boolean getRequired() { return required; }
    public void setRequired(Boolean required) { this.required = required; }

    public Boolean getMulti() { return multi; }
    public void setMulti(Boolean multi) { this.multi = multi; }

    public Boolean getArrayFlag() { return arrayFlag; }
    public void setArrayFlag(Boolean arrayFlag) { this.arrayFlag = arrayFlag; }

    public String getSourceInfo() { return sourceInfo; }
    public void setSourceInfo(String sourceInfo) { this.sourceInfo = sourceInfo; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
```

- [ ] **Step 1.3: 创建 Mapper**

Create `src/main/java/com/sunline/dict/mapper/FlowFieldDetailMapper.java`:

```java
package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.FlowFieldDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * flow_field_detail Mapper
 */
@Mapper
public interface FlowFieldDetailMapper extends BaseMapper<FlowFieldDetail> {

    /** 按 flow_id 删除该交易的所有字段记录（用于先删后插的幂等策略） */
    int deleteByFlowId(@Param("flowId") String flowId);

    /** 按 flow_id + io_type 查字段清单（用于 by-flow 查询） */
    List<FlowFieldDetail> selectByFlowIdAndIoType(@Param("flowId") String flowId,
                                                   @Param("ioType") String ioType);
}
```

- [ ] **Step 1.4: 创建 Mapper XML**

Create `src/main/resources/mapper/FlowFieldDetailMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.sunline.dict.mapper.FlowFieldDetailMapper">

    <delete id="deleteByFlowId">
        DELETE FROM flow_field_detail WHERE flow_id = #{flowId}
    </delete>

    <select id="selectByFlowIdAndIoType" resultType="com.sunline.dict.entity.FlowFieldDetail">
        SELECT id, flow_id AS flowId, io_type AS ioType, field_id AS fieldId,
               field_type AS fieldType, longname, ref, required, multi,
               array_flag AS arrayFlag, source_info AS sourceInfo,
               create_time AS createTime, update_time AS updateTime
        FROM   flow_field_detail
        WHERE  flow_id = #{flowId}
          AND  io_type = #{ioType}
        ORDER BY id ASC
    </select>

</mapper>
```

- [ ] **Step 1.5: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 1.6: 提交 Task 1**

```bash
git add src/main/resources/sql/create_flow_field_detail.sql \
        src/main/java/com/sunline/dict/entity/FlowFieldDetail.java \
        src/main/java/com/sunline/dict/mapper/FlowFieldDetailMapper.java \
        src/main/resources/mapper/FlowFieldDetailMapper.xml
git commit -m "feat: flow_field_detail 表结构 + Entity + Mapper 骨架"
```

---

## Task 2: 测试 fixture（FlowtransXmlFixtureBuilder）

**Files:**
- Create: `src/test/java/com/sunline/dict/testutil/FlowtransXmlFixtureBuilder.java`

- [ ] **Step 2.1: 创建 XML 构造器**

Create `src/test/java/com/sunline/dict/testutil/FlowtransXmlFixtureBuilder.java`:

```java
package com.sunline.dict.testutil;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用 .flowtrans.xml 字符串构造器（极简版）
 * 
 * 用法：
 *   String xml = FlowtransXmlFixtureBuilder.newBuilder("TC060", "单位结算卡设置")
 *       .input()
 *           .field("unitSetlCardOprnTpCd", "MBaseEnumType.E_X",
 *                  "MDict.U.unitSetlCardOprnTpCd", "操作类型", true, false)
 *           .fieldsBegin("unitSetlCardFcnSettgInptArray", "MDict.U.array")
 *               .field("cstAcNum", "MBaseType.U_X", "MDict.O.cstAcNum", "账号", true, true)
 *           .fieldsEnd()
 *       .output()
 *           .field("tlrSeqNum", "MBaseType.U_X", "MDict.T.tlrSeqNum", "流水", false, false)
 *       .build();
 */
public class FlowtransXmlFixtureBuilder {

    private final String flowId;
    private final String longname;
    private final List<String> inputBuf = new ArrayList<>();
    private final List<String> outputBuf = new ArrayList<>();
    private List<String> currentBuf;
    private boolean insideFields = false;

    private FlowtransXmlFixtureBuilder(String flowId, String longname) {
        this.flowId = flowId;
        this.longname = longname;
    }

    public static FlowtransXmlFixtureBuilder newBuilder(String flowId, String longname) {
        return new FlowtransXmlFixtureBuilder(flowId, longname);
    }

    public FlowtransXmlFixtureBuilder input() {
        currentBuf = inputBuf;
        return this;
    }

    public FlowtransXmlFixtureBuilder output() {
        currentBuf = outputBuf;
        return this;
    }

    /** 写一个叶子 <field> 节点 */
    public FlowtransXmlFixtureBuilder field(String id, String type, String ref,
                                             String longname, boolean required, boolean multi) {
        ensureBuf();
        String indent = insideFields ? "    " : "  ";
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("<field id=\"").append(esc(id)).append("\"");
        if (type != null) sb.append(" type=\"").append(esc(type)).append("\"");
        sb.append(" required=\"").append(required).append("\"");
        sb.append(" multi=\"").append(multi).append("\"");
        sb.append(" array=\"false\"");
        if (longname != null) sb.append(" longname=\"").append(esc(longname)).append("\"");
        if (ref != null)      sb.append(" ref=\"").append(esc(ref)).append("\"");
        sb.append("/>");
        currentBuf.add(sb.toString());
        return this;
    }

    /** 写一个不带 ref 的叶子 <field>（用于测试"无 ref 跳过"） */
    public FlowtransXmlFixtureBuilder fieldNoRef(String id, String type) {
        ensureBuf();
        String indent = insideFields ? "    " : "  ";
        currentBuf.add(indent + "<field id=\"" + esc(id) + "\" type=\"" + esc(type) + "\"/>");
        return this;
    }

    /** 开始 <fields> 容器 */
    public FlowtransXmlFixtureBuilder fieldsBegin(String id, String ref) {
        ensureBuf();
        StringBuilder sb = new StringBuilder();
        sb.append("  <fields id=\"").append(esc(id)).append("\"");
        if (ref != null) sb.append(" ref=\"").append(esc(ref)).append("\"");
        sb.append(">");
        currentBuf.add(sb.toString());
        insideFields = true;
        return this;
    }

    /** 结束 <fields> 容器 */
    public FlowtransXmlFixtureBuilder fieldsEnd() {
        ensureBuf();
        currentBuf.add("  </fields>");
        insideFields = false;
        return this;
    }

    /** 写入任意原始 XML 行（用于边界场景） */
    public FlowtransXmlFixtureBuilder raw(String line) {
        ensureBuf();
        currentBuf.add(line);
        return this;
    }

    private void ensureBuf() {
        if (currentBuf == null) {
            throw new IllegalStateException("先调 input() 或 output()");
        }
    }

    public String build() {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<flowtran id=\"").append(esc(flowId)).append("\"");
        if (longname != null) sb.append(" longname=\"").append(esc(longname)).append("\"");
        sb.append(">\n");
        sb.append("  <interface id=\"").append(esc(flowId)).append("\"");
        if (longname != null) sb.append(" longname=\"").append(esc(longname)).append("\"");
        sb.append(">\n");

        if (!inputBuf.isEmpty()) {
            sb.append("  <input packMode=\"true\">\n");
            for (String line : inputBuf) sb.append(line).append("\n");
            sb.append("  </input>\n");
        }
        if (!outputBuf.isEmpty()) {
            sb.append("  <output asParm=\"true\" packMode=\"true\">\n");
            for (String line : outputBuf) sb.append(line).append("\n");
            sb.append("  </output>\n");
        }
        sb.append("  </interface>\n");
        sb.append("</flowtran>\n");
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
```

- [ ] **Step 2.2: 编译验证**

Run: `mvn -q -DskipTests test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 2.3: 提交 Task 2**

```bash
git add src/test/java/com/sunline/dict/testutil/FlowtransXmlFixtureBuilder.java
git commit -m "test: 新增 FlowtransXmlFixtureBuilder（极简 XML 字符串构造器）"
```

---

## Task 3: Service 骨架 + 第一个 TDD（parses_input_top_level_field）

**Files:**
- Create: `src/main/java/com/sunline/dict/service/FlowFieldDetailService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImpl.java`
- Create: `src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java`

- [ ] **Step 3.1: 创建 Service interface**

Create `src/main/java/com/sunline/dict/service/FlowFieldDetailService.java`:

```java
package com.sunline.dict.service;

import com.sunline.dict.entity.FlowFieldDetail;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;

/**
 * 交易字段明细服务
 */
public interface FlowFieldDetailService {

    /**
     * 解析 .flowtrans.xml 的根节点，把 input/output 下所有 <field>（含 fields 容器内）平铺入库。
     * 先 DELETE BY flowId 再 batch INSERT，幂等。
     *
     * @param flowtranRoot .flowtrans.xml 解析后的 <flowtran> 根元素（或包含 <input>/<output> 的容器）
     * @param flowId       交易 ID
     * @param sourceInfo   来源标识（与 flowtran.fromJar 同口径）
     * @return { "inputCount": N, "outputCount": M }
     */
    Map<String, Integer> extractAndSave(Element flowtranRoot, String flowId, String sourceInfo);

    /**
     * 按 flow_id + io_type 查询字段清单
     */
    List<FlowFieldDetail> getByFlowIdAndIoType(String flowId, String ioType);
}
```

- [ ] **Step 3.2: 创建 Service Impl 骨架（throw 占位）**

Create `src/main/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImpl.java`:

```java
package com.sunline.dict.service.impl;

import com.sunline.dict.entity.FlowFieldDetail;
import com.sunline.dict.mapper.FlowFieldDetailMapper;
import com.sunline.dict.service.FlowFieldDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;

@Service
public class FlowFieldDetailServiceImpl implements FlowFieldDetailService {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldDetailServiceImpl.class);

    @Autowired
    private FlowFieldDetailMapper flowFieldDetailMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> extractAndSave(Element flowtranRoot, String flowId, String sourceInfo) {
        throw new UnsupportedOperationException("Task 3 起 TDD 实现");
    }

    @Override
    public List<FlowFieldDetail> getByFlowIdAndIoType(String flowId, String ioType) {
        return flowFieldDetailMapper.selectByFlowIdAndIoType(flowId, ioType);
    }
}
```

- [ ] **Step 3.3: 写第一个失败测试 `parses_input_top_level_field`**

Create `src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java`:

```java
package com.sunline.dict.service.impl;

import com.sunline.dict.entity.FlowFieldDetail;
import com.sunline.dict.service.FlowFieldDetailService;
import com.sunline.dict.testutil.FlowtransXmlFixtureBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FlowFieldDetailServiceImplTest {

    @Autowired FlowFieldDetailService service;

    private static final String TEST_FLOW_ID = "TC_TEST_001";

    @AfterEach
    void cleanup() {
        // 清理本次测试落的所有数据
        // 通过 service 暴露面无 delete 接口；用 mapper 直接清。
        // 这里复用 extractAndSave 的"先删后插"机制：传一个空 root 触发 delete。
        // 简化：在子类或新增 cleanup 方法（YAGNI 这里直接调 mapper）。
        // 注意：实际清理在 Step 3.7 实现后会失效，所以这里先用 try-catch 兜底。
    }

    /** 把 XML 字符串解析为 root Element */
    private Element parseXml(String xml) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return doc.getDocumentElement();
    }

    @Test
    void parses_input_top_level_field() throws Exception {
        String xml = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "测试交易")
                .input()
                    .field("unitSetlCardOprnTpCd", "MBaseEnumType.E_X",
                            "MDict.U.unitSetlCardOprnTpCd", "单位结算卡操作类型代码",
                            true, false)
                .build();

        Element root = parseXml(xml);
        Map<String, Integer> result = service.extractAndSave(root, TEST_FLOW_ID, "test-source");

        assertEquals(1, result.get("inputCount").intValue());
        assertEquals(0, result.get("outputCount").intValue());

        List<FlowFieldDetail> inputs = service.getByFlowIdAndIoType(TEST_FLOW_ID, "input");
        assertEquals(1, inputs.size());

        FlowFieldDetail f = inputs.get(0);
        assertEquals("unitSetlCardOprnTpCd", f.getFieldId());
        assertEquals("MBaseEnumType.E_X", f.getFieldType());
        assertEquals("MDict.U.unitSetlCardOprnTpCd", f.getRef());
        assertEquals("单位结算卡操作类型代码", f.getLongname());
        assertEquals(Boolean.TRUE, f.getRequired());
        assertEquals(Boolean.FALSE, f.getMulti());
        assertEquals(Boolean.FALSE, f.getArrayFlag());
        assertEquals("test-source", f.getSourceInfo());
    }
}
```

- [ ] **Step 3.4: 跑测试验证它失败**

Run: `mvn -q -DskipTests=false -Dtest=FlowFieldDetailServiceImplTest#parses_input_top_level_field test`
Expected: FAIL with `UnsupportedOperationException: Task 3 起 TDD 实现`

- [ ] **Step 3.5: 实现 `extractAndSave` 主逻辑**

Replace the `extractAndSave` method body in `src/main/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImpl.java`:

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> extractAndSave(Element flowtranRoot, String flowId, String sourceInfo) {
        // 找 <input> 和 <output>（用 getElementsByTagName 兼容 flowtran 直接子节点 或 interface 子节点两种结构）
        Element inputEl  = firstByTag(flowtranRoot, "input");
        Element outputEl = firstByTag(flowtranRoot, "output");

        Map<String, FieldRow> inputs  = new LinkedHashMap<>();
        Map<String, FieldRow> outputs = new LinkedHashMap<>();

        if (inputEl  != null) walk(inputEl,  inputs,  false);
        if (outputEl != null) walk(outputEl, outputs, false);

        // 先删后插：清掉该 flow 全部记录
        flowFieldDetailMapper.deleteByFlowId(flowId);

        LocalDateTime now = LocalDateTime.now();
        int inputCount = 0;
        int outputCount = 0;

        for (FieldRow row : inputs.values()) {
            flowFieldDetailMapper.insert(toEntity(row, flowId, "input", sourceInfo, now));
            inputCount++;
        }
        for (FieldRow row : outputs.values()) {
            flowFieldDetailMapper.insert(toEntity(row, flowId, "output", sourceInfo, now));
            outputCount++;
        }

        log.info("flow_field_detail 入库 flowId={} input={} output={}", flowId, inputCount, outputCount);

        Map<String, Integer> ret = new HashMap<>();
        ret.put("inputCount", inputCount);
        ret.put("outputCount", outputCount);
        return ret;
    }

    /** 查 parent 下第一个 tagName 匹配的元素（递归全文档） */
    private Element firstByTag(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    /** 递归遍历子树，收集 <field>，inArray 透传 array_flag */
    private void walk(Element node, Map<String, FieldRow> bag, boolean inArray) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            Element child = (Element) n;
            String name = child.getTagName();

            if ("field".equals(name)) {
                String ref     = child.getAttribute("ref");
                String fieldId = child.getAttribute("id");
                if (ref == null || ref.isEmpty()) continue;          // 无 ref 跳过
                if (fieldId == null || fieldId.isEmpty()) {
                    log.warn("field 缺少 id 属性，跳过");
                    continue;
                }

                FieldRow existing = bag.get(fieldId);
                // 去重决策：
                //   - 已存在为顶层（arrayFlag=false）       → 保留（不覆盖）
                //   - 已存在为嵌套 + 当前也是嵌套           → 保留首次
                //   - 已存在为嵌套 + 当前是顶层             → 顶层升级覆盖
                if (existing != null) {
                    if (!existing.arrayFlag) continue;   // 已存在顶层 → 保留
                    if (inArray)            continue;    // 嵌套覆盖嵌套 → 保留首次
                    // 嵌套 → 顶层升级，落到下面覆盖
                }

                bag.put(fieldId, new FieldRow(
                        fieldId,
                        nullIfEmpty(child.getAttribute("type")),
                        nullIfEmpty(child.getAttribute("longname")),
                        ref,
                        parseBool(child.getAttribute("required")),
                        parseBool(child.getAttribute("multi")),
                        inArray
                ));

            } else if ("fields".equals(name)) {
                walk(child, bag, true);             // 容器不入表，递归且标记 inArray
            } else {
                walk(child, bag, inArray);          // 其他标签继续递归
            }
        }
    }

    private FlowFieldDetail toEntity(FieldRow row, String flowId, String ioType,
                                      String sourceInfo, LocalDateTime now) {
        FlowFieldDetail e = new FlowFieldDetail();
        e.setFlowId(flowId);
        e.setIoType(ioType);
        e.setFieldId(row.fieldId);
        e.setFieldType(row.type);
        e.setLongname(row.longname);
        e.setRef(row.ref);
        e.setRequired(row.required);
        e.setMulti(row.multi);
        e.setArrayFlag(row.arrayFlag);
        e.setSourceInfo(sourceInfo);
        e.setCreateTime(now);
        e.setUpdateTime(now);
        return e;
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static Boolean parseBool(String s) {
        if (s == null || s.isEmpty()) return Boolean.FALSE;
        return "true".equalsIgnoreCase(s.trim());
    }

    /** 解析中间结构 */
    private static class FieldRow {
        final String fieldId;
        final String type;
        final String longname;
        final String ref;
        final Boolean required;
        final Boolean multi;
        final boolean arrayFlag;

        FieldRow(String fieldId, String type, String longname, String ref,
                  Boolean required, Boolean multi, boolean arrayFlag) {
            this.fieldId = fieldId;
            this.type = type;
            this.longname = longname;
            this.ref = ref;
            this.required = required;
            this.multi = multi;
            this.arrayFlag = arrayFlag;
        }
    }
```

Also add these imports at the top of the file:

```java
import com.sunline.dict.entity.FlowFieldDetail;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
```

- [ ] **Step 3.6: 给测试类加 cleanup 用的 mapper 注入**

Edit `src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java`:

- 在 `@Autowired FlowFieldDetailService service;` 下方新增：

```java
    @Autowired com.sunline.dict.mapper.FlowFieldDetailMapper mapper;
```

- 替换 `@AfterEach cleanup` 方法体为：

```java
    @AfterEach
    void cleanup() {
        mapper.deleteByFlowId(TEST_FLOW_ID);
    }
```

- [ ] **Step 3.7: 跑测试验证它通过**

Run: `mvn -q -DskipTests=false -Dtest=FlowFieldDetailServiceImplTest#parses_input_top_level_field test`
Expected: PASS

- [ ] **Step 3.8: 提交 Task 3**

```bash
git add src/main/java/com/sunline/dict/service/FlowFieldDetailService.java \
        src/main/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java
git commit -m "feat: FlowFieldDetailService 主流程 + parses_input_top_level_field 通过"
```

---

## Task 4: TDD — `parses_output_field` + `flattens_fields_container`

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java`

- [ ] **Step 4.1: 追加 2 个测试**

Append in the test class after `parses_input_top_level_field`:

```java
    @Test
    void parses_output_field() throws Exception {
        String xml = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "测试交易")
                .output()
                    .field("tlrSeqNum", "MBaseType.U_X",
                            "MDict.T.tlrSeqNum", "柜员流水号",
                            false, false)
                .build();

        Element root = parseXml(xml);
        Map<String, Integer> result = service.extractAndSave(root, TEST_FLOW_ID, "src");

        assertEquals(0, result.get("inputCount").intValue());
        assertEquals(1, result.get("outputCount").intValue());

        List<FlowFieldDetail> outputs = service.getByFlowIdAndIoType(TEST_FLOW_ID, "output");
        assertEquals(1, outputs.size());
        assertEquals("tlrSeqNum", outputs.get(0).getFieldId());
        assertEquals("MDict.T.tlrSeqNum", outputs.get(0).getRef());
    }

    @Test
    void flattens_fields_container() throws Exception {
        // <fields> 容器内的 <field> 应被平铺入表，容器本身不入表
        String xml = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "测试交易")
                .input()
                    .field("topField", "T1", "MDict.X.topField", "顶层字段", true, false)
                    .fieldsBegin("arrayContainer", "MDict.X.arrayContainer")
                        .field("nestedA", "T2", "MDict.X.nestedA", "嵌套A", false, false)
                        .field("nestedB", "T3", "MDict.X.nestedB", "嵌套B", false, false)
                    .fieldsEnd()
                .build();

        Element root = parseXml(xml);
        Map<String, Integer> result = service.extractAndSave(root, TEST_FLOW_ID, "src");

        // 顶层 + 2 个嵌套 = 3 条，容器自己不算
        assertEquals(3, result.get("inputCount").intValue());

        List<FlowFieldDetail> inputs = service.getByFlowIdAndIoType(TEST_FLOW_ID, "input");
        assertEquals(3, inputs.size());

        // 确认 arrayContainer 容器自己不入表
        assertTrue(inputs.stream().noneMatch(f -> "arrayContainer".equals(f.getFieldId())),
                "<fields> 容器自身不应入表");

        // 顶层 + 嵌套各自 array_flag 正确
        FlowFieldDetail top = inputs.stream().filter(f -> "topField".equals(f.getFieldId())).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, top.getArrayFlag());

        FlowFieldDetail nestedA = inputs.stream().filter(f -> "nestedA".equals(f.getFieldId())).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, nestedA.getArrayFlag());
    }
```

- [ ] **Step 4.2: 跑测试**

Run: `mvn -q -DskipTests=false -Dtest=FlowFieldDetailServiceImplTest test`
Expected: PASS — 3 tests pass

- [ ] **Step 4.3: 提交 Task 4**

```bash
git add src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java
git commit -m "test: output 字段 + fields 容器平铺"
```

---

## Task 5: TDD — 去重两个方向（顶层先 / 嵌套先）

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java`

- [ ] **Step 5.1: 追加 2 个测试**

```java
    @Test
    void top_level_wins_when_top_appears_first() throws Exception {
        // 顶层先出现，嵌套后出现同 id → 保留顶层
        String xml = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "T")
                .input()
                    .field("dupId", "TOP", "MDict.X.top", "顶层版", true, false)
                    .fieldsBegin("container", "MDict.X.container")
                        .field("dupId", "NESTED", "MDict.X.nested", "嵌套版", false, true)
                    .fieldsEnd()
                .build();

        Element root = parseXml(xml);
        service.extractAndSave(root, TEST_FLOW_ID, "src");

        List<FlowFieldDetail> inputs = service.getByFlowIdAndIoType(TEST_FLOW_ID, "input");
        // 应去重为 1 条
        long dupCount = inputs.stream().filter(f -> "dupId".equals(f.getFieldId())).count();
        assertEquals(1L, dupCount);

        FlowFieldDetail kept = inputs.stream().filter(f -> "dupId".equals(f.getFieldId())).findFirst().orElseThrow();
        // 保留顶层版
        assertEquals("TOP", kept.getFieldType());
        assertEquals("MDict.X.top", kept.getRef());
        assertEquals(Boolean.FALSE, kept.getArrayFlag());
    }

    @Test
    void top_level_upgrades_when_nested_appears_first() throws Exception {
        // 嵌套先出现，顶层后出现同 id → 顶层升级覆盖嵌套
        String xml = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "T")
                .input()
                    .fieldsBegin("container", "MDict.X.container")
                        .field("dupId", "NESTED", "MDict.X.nested", "嵌套版", false, true)
                    .fieldsEnd()
                    .field("dupId", "TOP", "MDict.X.top", "顶层版", true, false)
                .build();

        Element root = parseXml(xml);
        service.extractAndSave(root, TEST_FLOW_ID, "src");

        List<FlowFieldDetail> inputs = service.getByFlowIdAndIoType(TEST_FLOW_ID, "input");
        long dupCount = inputs.stream().filter(f -> "dupId".equals(f.getFieldId())).count();
        assertEquals(1L, dupCount);

        FlowFieldDetail kept = inputs.stream().filter(f -> "dupId".equals(f.getFieldId())).findFirst().orElseThrow();
        // 顶层升级
        assertEquals("TOP", kept.getFieldType());
        assertEquals("MDict.X.top", kept.getRef());
        assertEquals(Boolean.FALSE, kept.getArrayFlag());
    }
```

- [ ] **Step 5.2: 跑测试**

Run: `mvn -q -DskipTests=false -Dtest=FlowFieldDetailServiceImplTest test`
Expected: PASS — 5 tests

- [ ] **Step 5.3: 提交 Task 5**

```bash
git add src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java
git commit -m "test: 去重规则（顶层优先 + 嵌套升级）"
```

---

## Task 6: TDD — 跳过场景（无 ref + fields 容器自己）

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java`

- [ ] **Step 6.1: 追加 2 个测试**

```java
    @Test
    void skips_field_without_ref() throws Exception {
        // 无 ref 的 field 应跳过
        String xml = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "T")
                .input()
                    .field("hasRef", "T1", "MDict.X.hasRef", "有 ref", false, false)
                    .fieldNoRef("noRef", "T2")
                .build();

        Element root = parseXml(xml);
        Map<String, Integer> result = service.extractAndSave(root, TEST_FLOW_ID, "src");

        assertEquals(1, result.get("inputCount").intValue());
        List<FlowFieldDetail> inputs = service.getByFlowIdAndIoType(TEST_FLOW_ID, "input");
        assertTrue(inputs.stream().noneMatch(f -> "noRef".equals(f.getFieldId())),
                "无 ref 的 field 应跳过");
    }

    @Test
    void fields_container_itself_not_persisted() throws Exception {
        // <fields id="X" ref="..."> 容器节点自己不应入表，即使它有 ref
        String xml = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "T")
                .input()
                    .fieldsBegin("containerWithRef", "MDict.X.containerWithRef")
                        .field("inner", "T1", "MDict.X.inner", "内层", false, false)
                    .fieldsEnd()
                .build();

        Element root = parseXml(xml);
        service.extractAndSave(root, TEST_FLOW_ID, "src");

        List<FlowFieldDetail> inputs = service.getByFlowIdAndIoType(TEST_FLOW_ID, "input");
        assertEquals(1, inputs.size());
        assertEquals("inner", inputs.get(0).getFieldId());
        assertTrue(inputs.stream().noneMatch(f -> "containerWithRef".equals(f.getFieldId())));
    }
```

- [ ] **Step 6.2: 跑测试**

Run: `mvn -q -DskipTests=false -Dtest=FlowFieldDetailServiceImplTest test`
Expected: PASS — 7 tests

- [ ] **Step 6.3: 提交 Task 6**

```bash
git add src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java
git commit -m "test: 无 ref 跳过 + fields 容器自身不入表"
```

---

## Task 7: TDD — 幂等 + 空 input/output

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java`

- [ ] **Step 7.1: 追加 2 个测试**

```java
    @Test
    void delete_then_insert_idempotent_on_rerun() throws Exception {
        // 第一次入库 2 条字段
        String xml1 = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "T")
                .input()
                    .field("a", "T", "MDict.X.a", "A", true, false)
                    .field("b", "T", "MDict.X.b", "B", false, false)
                .build();
        service.extractAndSave(parseXml(xml1), TEST_FLOW_ID, "src");
        assertEquals(2, service.getByFlowIdAndIoType(TEST_FLOW_ID, "input").size());

        // 第二次：XML 改成只剩 b，再加新字段 c
        String xml2 = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "T")
                .input()
                    .field("b", "T", "MDict.X.b", "B", false, false)
                    .field("c", "T", "MDict.X.c", "C", false, false)
                .build();
        service.extractAndSave(parseXml(xml2), TEST_FLOW_ID, "src");

        List<FlowFieldDetail> inputs = service.getByFlowIdAndIoType(TEST_FLOW_ID, "input");
        assertEquals(2, inputs.size());
        // a 应被清除
        assertTrue(inputs.stream().noneMatch(f -> "a".equals(f.getFieldId())),
                "第一次的 a 字段应被先删后插清除");
        assertTrue(inputs.stream().anyMatch(f -> "b".equals(f.getFieldId())));
        assertTrue(inputs.stream().anyMatch(f -> "c".equals(f.getFieldId())));
    }

    @Test
    void handles_empty_input_output() throws Exception {
        // 既没 input 也没 output → 0 条入库，不抛错
        String xml = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "T").build();

        Element root = parseXml(xml);
        Map<String, Integer> result = service.extractAndSave(root, TEST_FLOW_ID, "src");

        assertEquals(0, result.get("inputCount").intValue());
        assertEquals(0, result.get("outputCount").intValue());
        assertEquals(0, service.getByFlowIdAndIoType(TEST_FLOW_ID, "input").size());
        assertEquals(0, service.getByFlowIdAndIoType(TEST_FLOW_ID, "output").size());
    }
```

- [ ] **Step 7.2: 跑测试**

Run: `mvn -q -DskipTests=false -Dtest=FlowFieldDetailServiceImplTest test`
Expected: PASS — 9 tests

- [ ] **Step 7.3: 提交 Task 7**

```bash
git add src/test/java/com/sunline/dict/service/impl/FlowFieldDetailServiceImplTest.java
git commit -m "test: 先删后插幂等 + 空 input/output"
```

---

## Task 8: 接入点 1 — FlowXmlParseServiceImpl

**Files:**
- Modify: `src/main/java/com/sunline/dict/service/impl/FlowXmlParseServiceImpl.java`

- [ ] **Step 8.1: 注入 FlowFieldDetailService**

Open `src/main/java/com/sunline/dict/service/impl/FlowXmlParseServiceImpl.java`. After the existing `@Autowired private FlowStepMapper flowStepMapper;` (around line 42), add:

```java
    @Autowired
    private com.sunline.dict.service.FlowFieldDetailService flowFieldDetailService;
```

- [ ] **Step 8.2: 在 flow_step 解析完成后调 extractAndSave**

In the same file, find the closing brace of the `if (flowNodes.getLength() > 0) { ... }` block (around line 149). After that closing brace, BEFORE the closing brace of the outer `if (id != null && !id.isEmpty())` block (around line 150), insert:

```java
            // 新增：字段平铺入 flow_field_detail
            try {
                Map<String, Integer> fieldResult = flowFieldDetailService.extractAndSave(root, id, sourceInfo);
                result.put("inputFieldCount",  fieldResult.get("inputCount"));
                result.put("outputFieldCount", fieldResult.get("outputCount"));
            } catch (Exception e) {
                log.warn("flow_field_detail 入库失败 flowId={}, 不影响 flowtran/flow_step 主流程", id, e);
                result.put("inputFieldCount",  0);
                result.put("outputFieldCount", 0);
            }
```

(`Map` and `Integer` already imported via existing `java.util.*` style imports; check if explicit import needed.)

- [ ] **Step 8.3: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 8.4: 写集成测试 `parseAndSave_also_persists_flow_field_detail`**

Create or append to `src/test/java/com/sunline/dict/service/impl/FlowXmlParseServiceImplFieldDetailIntegrationTest.java`:

```java
package com.sunline.dict.service.impl;

import com.sunline.dict.entity.FlowFieldDetail;
import com.sunline.dict.mapper.FlowFieldDetailMapper;
import com.sunline.dict.mapper.FlowStepMapper;
import com.sunline.dict.mapper.FlowtranMapper;
import com.sunline.dict.service.FlowFieldDetailService;
import com.sunline.dict.service.FlowXmlParseService;
import com.sunline.dict.testutil.FlowtransXmlFixtureBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FlowXmlParseServiceImplFieldDetailIntegrationTest {

    @Autowired FlowXmlParseService flowXmlParseService;
    @Autowired FlowFieldDetailService flowFieldDetailService;

    @Autowired FlowtranMapper flowtranMapper;
    @Autowired FlowStepMapper flowStepMapper;
    @Autowired FlowFieldDetailMapper flowFieldDetailMapper;

    private static final String TEST_FLOW_ID = "TC_INT_FFD_001";

    @AfterEach
    void cleanup() {
        flowFieldDetailMapper.deleteByFlowId(TEST_FLOW_ID);
        flowtranMapper.deleteById(TEST_FLOW_ID);
        // flow_step 由 parseAndSave 主流程的 deleteByFlowId 在下次跑时清理；测试单次无需手动
    }

    @Test
    void parseAndSave_also_persists_flow_field_detail() throws Exception {
        String xml = FlowtransXmlFixtureBuilder.newBuilder(TEST_FLOW_ID, "集成测试")
                .input()
                    .field("a", "T1", "MDict.X.a", "字段A", true, false)
                .output()
                    .field("b", "T2", "MDict.X.b", "字段B", false, false)
                .build();

        Map<String, Object> result = flowXmlParseService.parseAndSave(xml, "test-source");

        // flowtran 主表 + flow_field_detail 同时落
        assertEquals(1, ((Number) result.get("flowtranCount")).intValue());
        assertEquals(1, ((Number) result.get("inputFieldCount")).intValue());
        assertEquals(1, ((Number) result.get("outputFieldCount")).intValue());

        List<FlowFieldDetail> inputs  = flowFieldDetailService.getByFlowIdAndIoType(TEST_FLOW_ID, "input");
        List<FlowFieldDetail> outputs = flowFieldDetailService.getByFlowIdAndIoType(TEST_FLOW_ID, "output");
        assertEquals(1, inputs.size());
        assertEquals(1, outputs.size());
        assertEquals("a", inputs.get(0).getFieldId());
        assertEquals("b", outputs.get(0).getFieldId());
    }
}
```

- [ ] **Step 8.5: 跑集成测试**

Run: `mvn -q -DskipTests=false -Dtest=FlowXmlParseServiceImplFieldDetailIntegrationTest test`
Expected: PASS

- [ ] **Step 8.6: 提交 Task 8**

```bash
git add src/main/java/com/sunline/dict/service/impl/FlowXmlParseServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/FlowXmlParseServiceImplFieldDetailIntegrationTest.java
git commit -m "feat: FlowXmlParseServiceImpl 末尾接入 flow_field_detail 落库"
```

---

## Task 9: 接入点 2 — JarScanServiceImpl

**Files:**
- Modify: `src/main/java/com/sunline/dict/service/impl/JarScanServiceImpl.java`

- [ ] **Step 9.1: 注入 FlowFieldDetailService**

Open `src/main/java/com/sunline/dict/service/impl/JarScanServiceImpl.java`. Find the existing field injection block (around `private FlowtranMapper flowtranMapper`). Add below it:

```java
    @Autowired
    private com.sunline.dict.service.FlowFieldDetailService flowFieldDetailService;
```

- [ ] **Step 9.2: 在 parseFlowtranXml 末尾调 extractAndSave**

In the same file, find the `parseFlowtranXml` method. After the closing `}` of the `if (flowNodes.getLength() > 0) { ... }` block (which writes flow_step), BEFORE the closing `}` of the outer `if (id != null && !id.isEmpty())` block, add:

```java
            // 新增：字段平铺入 flow_field_detail
            try {
                flowFieldDetailService.extractAndSave(root, id, fromJar);
            } catch (Exception e) {
                log.warn("JarScan flow_field_detail 入库失败 flowId={}", id, e);
            }
```

- [ ] **Step 9.3: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 9.4: 跑全量 FlowFieldDetailServiceImplTest 确认无回归**

Run: `mvn -q -DskipTests=false -Dtest='FlowFieldDetailServiceImplTest,FlowXmlParseServiceImplFieldDetailIntegrationTest' test`
Expected: PASS — 10 tests

- [ ] **Step 9.5: 提交 Task 9**

```bash
git add src/main/java/com/sunline/dict/service/impl/JarScanServiceImpl.java
git commit -m "feat: JarScanServiceImpl 接入 flow_field_detail 落库"
```

---

## Task 10: FlowFieldRescanService（异步全量重扫 + 进度）

**Files:**
- Create: `src/main/java/com/sunline/dict/service/FlowFieldRescanService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/FlowFieldRescanServiceImpl.java`

- [ ] **Step 10.1: 创建 RescanService interface**

Create `src/main/java/com/sunline/dict/service/FlowFieldRescanService.java`:

```java
package com.sunline.dict.service;

import java.util.Map;

/**
 * 字段明细全量重扫服务（异步 + 进度）
 */
public interface FlowFieldRescanService {

    /**
     * 启动一次异步全量重扫。同一时间只允许一个 RUNNING。
     *
     * @param sourcePath 本地源码目录绝对路径
     * @return { "operationId": uuid, "totalFiles": N, "status": "RUNNING" }
     */
    Map<String, Object> startRescan(String sourcePath) throws Exception;

    /**
     * 重扫单个 flow（同步）。
     * 实际从 sourcePath 下找名为 {flowId}.flowtrans.xml 的文件。
     *
     * @return { "inputCount": N, "outputCount": M }
     */
    Map<String, Integer> rescanOne(String flowId, String sourcePath) throws Exception;

    /**
     * 查询进度
     * @return { "operationId", "total", "processed", "current", "status", "errors", "startTime", "endTime" }
     *         operationId 不存在 → 返回 status="UNKNOWN"
     */
    Map<String, Object> getProgress(String operationId);
}
```

- [ ] **Step 10.2: 创建 Impl**

Create `src/main/java/com/sunline/dict/service/impl/FlowFieldRescanServiceImpl.java`:

```java
package com.sunline.dict.service.impl;

import com.sunline.dict.service.FlowFieldRescanService;
import com.sunline.dict.service.FlowXmlParseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Service
public class FlowFieldRescanServiceImpl implements FlowFieldRescanService {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldRescanServiceImpl.class);

    @Autowired
    private FlowXmlParseService flowXmlParseService;

    /** 进度对象内存表，仅保留最近 10 个 operationId */
    private final Map<String, Progress> progressMap = new ConcurrentHashMap<>();

    /** 同时只允许一个 RUNNING */
    private volatile String runningOperationId = null;

    @Override
    public synchronized Map<String, Object> startRescan(String sourcePath) throws Exception {
        if (sourcePath == null || sourcePath.isEmpty()) {
            throw new IllegalArgumentException("sourcePath 不能为空");
        }
        Path root = Paths.get(sourcePath);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("sourcePath 不是有效目录: " + sourcePath);
        }
        if (runningOperationId != null) {
            Progress running = progressMap.get(runningOperationId);
            if (running != null && "RUNNING".equals(running.status)) {
                throw new IllegalStateException("已有重扫任务运行中: operationId=" + runningOperationId);
            }
        }

        // 列出全部 .flowtrans.xml
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".flowtrans.xml"))
                        .toList();
        }

        String operationId = UUID.randomUUID().toString();
        Progress p = new Progress();
        p.operationId = operationId;
        p.total = files.size();
        p.processed = new AtomicInteger(0);
        p.status = "RUNNING";
        p.startTime = LocalDateTime.now();
        p.errors = Collections.synchronizedList(new ArrayList<>());

        progressMap.put(operationId, p);
        runningOperationId = operationId;
        trimProgressMap();

        // 异步执行
        runAsync(operationId, files);

        Map<String, Object> ret = new HashMap<>();
        ret.put("operationId", operationId);
        ret.put("totalFiles", files.size());
        ret.put("status", "RUNNING");
        return ret;
    }

    @Async
    public void runAsync(String operationId, List<Path> files) {
        Progress p = progressMap.get(operationId);
        try {
            for (Path file : files) {
                p.current = file.toString();
                try (FileInputStream fis = new FileInputStream(file.toFile())) {
                    flowXmlParseService.parseAndSave(fis, file.toString());
                } catch (Exception e) {
                    String msg = file.getFileName() + ": " + e.getMessage();
                    log.warn("rescan 解析失败 {}", msg);
                    if (p.errors.size() < 100) p.errors.add(msg);
                }
                p.processed.incrementAndGet();
            }
            p.status = "COMPLETED";
        } catch (Exception e) {
            log.error("rescan 异常", e);
            p.status = "FAILED";
            p.errors.add("FATAL: " + e.getMessage());
        } finally {
            p.endTime = LocalDateTime.now();
            if (operationId.equals(runningOperationId)) runningOperationId = null;
        }
    }

    @Override
    public Map<String, Integer> rescanOne(String flowId, String sourcePath) throws Exception {
        Path root = Paths.get(sourcePath);
        Path target;
        try (Stream<Path> walk = Files.walk(root)) {
            target = walk.filter(Files::isRegularFile)
                         .filter(p -> p.getFileName().toString().equals(flowId + ".flowtrans.xml"))
                         .findFirst()
                         .orElseThrow(() -> new IllegalArgumentException(
                                 "未找到 " + flowId + ".flowtrans.xml in " + sourcePath));
        }
        try (FileInputStream fis = new FileInputStream(target.toFile())) {
            Map<String, Object> result = flowXmlParseService.parseAndSave(fis, target.toString());
            Map<String, Integer> ret = new HashMap<>();
            ret.put("inputCount",  toInt(result.get("inputFieldCount")));
            ret.put("outputCount", toInt(result.get("outputFieldCount")));
            return ret;
        }
    }

    @Override
    public Map<String, Object> getProgress(String operationId) {
        Progress p = progressMap.get(operationId);
        if (p == null) {
            Map<String, Object> ret = new HashMap<>();
            ret.put("operationId", operationId);
            ret.put("status", "UNKNOWN");
            return ret;
        }
        Map<String, Object> ret = new HashMap<>();
        ret.put("operationId", p.operationId);
        ret.put("total", p.total);
        ret.put("processed", p.processed.get());
        ret.put("current", p.current);
        ret.put("status", p.status);
        ret.put("errors", new ArrayList<>(p.errors));
        ret.put("startTime", p.startTime);
        ret.put("endTime", p.endTime);
        return ret;
    }

    private void trimProgressMap() {
        if (progressMap.size() <= 10) return;
        // 简单 LRU：按 startTime 升序，删最早的，留下 10 个
        progressMap.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getValue().startTime))
                .limit(progressMap.size() - 10)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(progressMap::remove);
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }

    private static class Progress {
        String operationId;
        int total;
        AtomicInteger processed;
        String current;
        String status;          // RUNNING | COMPLETED | FAILED
        List<String> errors;
        LocalDateTime startTime;
        LocalDateTime endTime;
    }
}
```

- [ ] **Step 10.3: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 10.4: 提交 Task 10**

```bash
git add src/main/java/com/sunline/dict/service/FlowFieldRescanService.java \
        src/main/java/com/sunline/dict/service/impl/FlowFieldRescanServiceImpl.java
git commit -m "feat: FlowFieldRescanService 异步全量重扫 + 进度"
```

---

## Task 11: FlowFieldController（4 个端点）

**Files:**
- Create: `src/main/java/com/sunline/dict/controller/FlowFieldController.java`

- [ ] **Step 11.1: 创建 Controller**

Create `src/main/java/com/sunline/dict/controller/FlowFieldController.java`:

```java
package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.entity.FlowFieldDetail;
import com.sunline.dict.service.FlowFieldDetailService;
import com.sunline.dict.service.FlowFieldRescanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交易字段明细控制器
 */
@RestController
@RequestMapping("/api/flow-field")
public class FlowFieldController {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldController.class);

    @Autowired
    private FlowFieldDetailService flowFieldDetailService;

    @Autowired
    private FlowFieldRescanService flowFieldRescanService;

    /**
     * 缺省源码目录配置（与 XmlScan 共用一份配置）。
     * 若 application.yml 未配置，缺省为空字符串，请求需显式传 sourcePath。
     */
    @Value("${xml-scan.default-source-path:}")
    private String defaultSourcePath;

    /** 按 flow_id 查字段清单（同时返回 input + output） */
    @GetMapping("/by-flow/{flowId}")
    public Result<Map<String, List<FlowFieldDetail>>> byFlow(@PathVariable String flowId) {
        Map<String, List<FlowFieldDetail>> ret = new HashMap<>();
        ret.put("input",  flowFieldDetailService.getByFlowIdAndIoType(flowId, "input"));
        ret.put("output", flowFieldDetailService.getByFlowIdAndIoType(flowId, "output"));
        return Result.success(ret);
    }

    /** 全量重扫（异步） */
    @PostMapping("/rescan-all")
    public Result<Map<String, Object>> rescanAll(@RequestBody(required = false) Map<String, String> body) {
        try {
            String sourcePath = body == null ? null : body.get("sourcePath");
            if (sourcePath == null || sourcePath.isEmpty()) {
                sourcePath = defaultSourcePath;
            }
            if (sourcePath == null || sourcePath.isEmpty()) {
                return Result.error("sourcePath 未传且 xml-scan.default-source-path 未配置");
            }
            log.info("触发字段明细全量重扫 sourcePath={}", sourcePath);
            return Result.success(flowFieldRescanService.startRescan(sourcePath));
        } catch (Exception e) {
            log.error("rescan-all 失败", e);
            return Result.error("启动失败：" + e.getMessage());
        }
    }

    /** 重扫单个 flow（同步） */
    @PostMapping("/rescan/{flowId}")
    public Result<Map<String, Integer>> rescanOne(@PathVariable String flowId,
                                                    @RequestBody(required = false) Map<String, String> body) {
        try {
            String sourcePath = body == null ? null : body.get("sourcePath");
            if (sourcePath == null || sourcePath.isEmpty()) {
                sourcePath = defaultSourcePath;
            }
            if (sourcePath == null || sourcePath.isEmpty()) {
                return Result.error("sourcePath 未传且 xml-scan.default-source-path 未配置");
            }
            return Result.success(flowFieldRescanService.rescanOne(flowId, sourcePath));
        } catch (Exception e) {
            log.error("rescan {} 失败", flowId, e);
            return Result.error("重扫失败：" + e.getMessage());
        }
    }

    /** 查询进度 */
    @GetMapping("/rescan/progress")
    public Result<Map<String, Object>> progress(@RequestParam String operationId) {
        return Result.success(flowFieldRescanService.getProgress(operationId));
    }
}
```

- [ ] **Step 11.2: 在主启动类加 @EnableAsync（若未加）**

Open `src/main/java/com/sunline/dict/DictManagerApplication.java`. Check if class has `@EnableAsync`. If not, add the annotation and import.

Run to check first:

```bash
grep -l "EnableAsync" /Users/java/sunline-benchmark/src/main/java/com/sunline/dict/DictManagerApplication.java /Users/java/sunline-benchmark/src/main/java/com/sunline/dict/config/*.java 2>/dev/null
```

If grep returns no result, edit the main class:

```java
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync   // 新增
public class DictManagerApplication {
    ...
}
```

If grep already shows `@EnableAsync` somewhere, skip this step.

- [ ] **Step 11.3: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 11.4: 提交 Task 11**

```bash
git add src/main/java/com/sunline/dict/controller/FlowFieldController.java \
        src/main/java/com/sunline/dict/DictManagerApplication.java
git commit -m "feat: FlowFieldController 4 个端点 + @EnableAsync"
```

(If `DictManagerApplication.java` was not changed, just omit it from the git add.)

---

## Task 12: 数据库表初始化（脚本执行说明）

**Files:** (无代码改动，仅记录数据库初始化步骤)

- [ ] **Step 12.1: 在 SQL 文件顶部追加执行说明**

Open `src/main/resources/sql/create_flow_field_detail.sql` and prepend (above the CREATE TABLE):

```sql
-- ============================================================
-- 执行说明：
--   在每个环境（dev/sit/uat/prod）的 OpenGauss/PostgreSQL 数据库手动执行：
--     psql -h <host> -U <user> -d <db> -f create_flow_field_detail.sql
--   或 IDE 工具（DataGrip/Navicat）打开本文件直接执行。
--
--   表与索引带 IF NOT EXISTS，重复执行幂等。
-- ============================================================

```

- [ ] **Step 12.2: 提交 Task 12**

```bash
git add src/main/resources/sql/create_flow_field_detail.sql
git commit -m "docs: flow_field_detail SQL 执行说明"
```

---

## Task 13: 全量测试 + 启动手动验证

**Files:** (无代码改动)

- [ ] **Step 13.1: 全量测试**

Run: `mvn -DskipTests=false test 2>&1 | grep -E "(Tests run:|BUILD)" | tail -10`
Expected: BUILD SUCCESS, 所有测试 0 failure 0 error。新增 9 个 FlowFieldDetailServiceImplTest + 1 个 FlowXmlParseServiceImplFieldDetailIntegrationTest = 10 个新测试通过；既有测试无回归。

- [ ] **Step 13.2: 启动应用 + 接口连通性手动测试（人工，记录结论）**

Pre-req（用户本地操作）：
1. 启动本地 OpenGauss/PostgreSQL
2. 在目标数据库执行 `src/main/resources/sql/create_flow_field_detail.sql`
3. `./start.sh` 启动应用

验证项（curl 或 Postman）：

```bash
# (a) 用一个已知存在的 flowId 查询字段（应返回 input/output 数组，可能为空）
curl http://localhost:8080/api/flow-field/by-flow/TC060

# (b) 全量重扫（指定本地源码目录）
curl -X POST http://localhost:8080/api/flow-field/rescan-all \
     -H 'Content-Type: application/json' \
     -d '{"sourcePath": "/path/to/local/source"}'
# → 返回 { operationId, totalFiles, status: "RUNNING" }

# (c) 查询进度
curl http://localhost:8080/api/flow-field/rescan/progress?operationId=<uuid>
# → 返回 { processed, total, status, errors[] }

# (d) 再次 by-flow 查询，应能看到字段
curl http://localhost:8080/api/flow-field/by-flow/<某 flowId>
```

如任一步失败，回报问题。

- [ ] **Step 13.3: 不需要 commit（仅验证）**

---

## Task 14: 同步 Obsidian 状态

**Files:**
- Modify: `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易字段明细表-设计.md`

- [ ] **Step 14.1: 把 spec 文档 status 改为 implemented**

Edit `/Users/java/obsidian/01 Engineering/sunline-benchmark/交易字段明细表-设计.md`:

- frontmatter `status: designed` → `status: implemented`
- 文末追加 `*实现完成：2026-06-04（feature 分支：feature/flow-field-detail）*`

不进 git。

---

## Self-Review

**1. Spec coverage:**

| Spec § | 内容 | 落实任务 |
|---|---|---|
| §1.3-1 | webhook/全量任一路径自动入库 | Task 8 + Task 9 + Task 10 |
| §1.3-2 | UNIQUE 联合键 + 顶层优先去重 | Task 1 + Task 5 |
| §1.3-3 | 无 ref 跳过 | Task 6 |
| §1.3-4 | fields 容器不入表 | Task 6 |
| §1.3-5 | 4 个 API 端点 | Task 11 |
| §1.3-6 | 全量数据源=本地源码 | Task 10 + Task 11 |
| §4.1 | 表结构 | Task 1 |
| §5.1 伪代码 | walk + 去重 | Task 3 |
| §5.2 去重表 4 个场景 | 顶层先/嵌套先/两嵌套/两顶层 | Task 5 + Task 3 实现已涵盖剩两个 |
| §5.4 先删后插 | DELETE BY flow_id | Task 7（幂等测试） |
| §6.1-6.4 接入点 | 加 1 行 + 不动 webhook/xmlscan | Task 8 + Task 9 |
| §7 API | 4 端点 + Result 风格 | Task 11 |
| §7.3 进度机制 | ConcurrentHashMap + LRU 10 | Task 10 |
| §8 错误处理 | 并发拒绝 / 文件失败继续 | Task 10 |
| §9 测试 | 9-10 个单测 + 集成 | Task 3-9 |

**2. Placeholder scan:** 无 TBD / TODO / "implement later"。

**3. Type consistency:**
- Service 方法名一致：`extractAndSave(Element, String, String)` 全文一致
- Map 返回 key 一致：`"inputCount"` / `"outputCount"` / `"inputFieldCount"` / `"outputFieldCount"`（注意：Service 层用前者，FlowXmlParseService.parseAndSave 把它转成 `"inputFieldCount"` 写入 result map）
- API 路径一致：`/api/flow-field/*`
- 表名一致：`flow_field_detail`
- 字段名一致：`flow_id` / `io_type` / `field_id` / `array_flag`

无类型/命名漂移。

---

*Plan finalized: 2026-06-04*
