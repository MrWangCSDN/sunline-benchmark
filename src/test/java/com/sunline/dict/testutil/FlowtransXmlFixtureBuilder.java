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
