package com.sunline.dict.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纯 Java 字段提取器（无 Spring 依赖，可在任意环境单元测试）。
 *
 * 输入：.flowtrans.xml 解析后的根元素（包含或直接是 <input>/<output> 的容器）
 * 输出：input + output 字段平铺列表（按 (io_type, field_id) 联合唯一去重，顶层优先）
 *
 * 规则：
 *  - 只处理叶子 <field>，<fields> 容器自身不入结果但递归子树
 *  - 字段必须有 ref 属性，无 ref 跳过
 *  - field 缺 id 跳过 + log.warn
 *  - 同 field_id 去重：顶层优先，嵌套之间保留首次
 */
public class FlowFieldExtractor {

    private static final Logger log = LoggerFactory.getLogger(FlowFieldExtractor.class);

    /** 提取结果 */
    public static class ExtractResult {
        public final List<FieldRow> inputs;
        public final List<FieldRow> outputs;

        ExtractResult(List<FieldRow> inputs, List<FieldRow> outputs) {
            this.inputs = inputs;
            this.outputs = outputs;
        }
    }

    /** 一条字段记录 */
    public static class FieldRow {
        public final String fieldId;
        public final String type;       // 可为 null
        public final String longname;   // 可为 null
        public final String ref;        // 必填（无 ref 已过滤）
        public final Boolean required;  // 缺省 false
        public final Boolean multi;     // 缺省 false
        public final boolean arrayFlag; // true=出现在 <fields> 容器内

        public FieldRow(String fieldId, String type, String longname, String ref,
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

    /**
     * 从 .flowtrans.xml 根元素提取 input/output 字段
     */
    public ExtractResult extract(Element root) {
        Map<String, FieldRow> inputBag  = new LinkedHashMap<>();
        Map<String, FieldRow> outputBag = new LinkedHashMap<>();

        Element inputEl  = firstByTag(root, "input");
        Element outputEl = firstByTag(root, "output");

        if (inputEl  != null) walk(inputEl,  inputBag,  false);
        if (outputEl != null) walk(outputEl, outputBag, false);

        return new ExtractResult(new ArrayList<>(inputBag.values()),
                                  new ArrayList<>(outputBag.values()));
    }

    /** 找根下第一个 tagName 匹配的元素（递归） */
    private Element firstByTag(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    /** 递归遍历，收集 <field>，去重 */
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
                if (ref == null || ref.isEmpty()) continue;
                if (fieldId == null || fieldId.isEmpty()) {
                    log.warn("<field> 缺 id 属性，跳过");
                    continue;
                }

                FieldRow existing = bag.get(fieldId);
                // 去重决策：
                //   - 已存在为顶层（arrayFlag=false）       → 保留（不覆盖）
                //   - 已存在为嵌套 + 当前也是嵌套           → 保留首次
                //   - 已存在为嵌套 + 当前是顶层             → 顶层升级覆盖
                if (existing != null) {
                    if (!existing.arrayFlag) continue;
                    if (inArray)            continue;
                    // fallthrough → 顶层升级
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
                walk(child, bag, true);
            } else {
                walk(child, bag, inArray);
            }
        }
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static Boolean parseBool(String s) {
        if (s == null || s.isEmpty()) return Boolean.FALSE;
        return "true".equalsIgnoreCase(s.trim());
    }
}
