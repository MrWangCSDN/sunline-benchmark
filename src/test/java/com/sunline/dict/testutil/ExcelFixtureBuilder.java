package com.sunline.dict.testutil;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * 测试用 Excel 构造器
 * 构造一个核心接口 Excel：左半边 A-I（不参与比对，可填占位） + 右半边 J 起（参与比对）
 *
 * 用法：
 *   MultipartFile file = ExcelFixtureBuilder.newBuilder("old.xlsx")
 *       .sheet("985501")
 *           .meta("交易码", "9855")
 *           .meta("接口名称", "理财业务销账销户处理")
 *           .headerCols("列中文名", "列顺序", "列数据类型", "列最大长度", "是否非空", "列描述", "校验值", "备注")
 *           .field("产品编号", "1", "string", "10", "Y", "", "", "")
 *           .field("交易种类", "2", "string", "1", "Y", "", "", "")
 *       .sheet("说明")
 *           .raw(0, 0, "本文档为...")
 *       .buildAsMultipartFile("oldFile");
 */
public class ExcelFixtureBuilder {

    public static final int COL_J = 9;       // J 列 0-based 索引
    public static final int COL_K = 10;      // K 列
    private static final int META_START_ROW = 1;   // 元信息从第 2 行起（0-based = 1）

    private final String fileName;
    private final List<SheetSpec> sheets = new ArrayList<>();
    private SheetSpec currentSheet;

    private ExcelFixtureBuilder(String fileName) {
        this.fileName = fileName;
    }

    public static ExcelFixtureBuilder newBuilder(String fileName) {
        return new ExcelFixtureBuilder(fileName);
    }

    public ExcelFixtureBuilder sheet(String sheetName) {
        currentSheet = new SheetSpec(sheetName);
        sheets.add(currentSheet);
        return this;
    }

    /** 元信息行：J 列 = label, K 列 = value */
    public ExcelFixtureBuilder meta(String label, String value) {
        if (currentSheet == null) {
            throw new IllegalStateException("先调 sheet() 再调 meta()");
        }
        currentSheet.metas.add(new MetaSpec(label, value));
        return this;
    }

    /** 字段表头行（出现"列中文名"标识分界点）—— 第一个元素必须是"列中文名" */
    public ExcelFixtureBuilder headerCols(String... cols) {
        if (currentSheet == null) {
            throw new IllegalStateException("先调 sheet() 再调 headerCols()");
        }
        if (cols.length == 0 || !"列中文名".equals(cols[0])) {
            throw new IllegalArgumentException("headerCols 第一个必须是'列中文名'");
        }
        currentSheet.headerCols = Arrays.asList(cols);
        return this;
    }

    /** 字段明细行（按 headerCols 顺序传值） */
    public ExcelFixtureBuilder field(String... values) {
        if (currentSheet == null) {
            throw new IllegalStateException("先调 sheet() 再调 field()");
        }
        if (currentSheet.headerCols == null) {
            throw new IllegalStateException("先调 headerCols 再调 field");
        }
        if (values.length != currentSheet.headerCols.size()) {
            throw new IllegalArgumentException(
                "field 列数 (" + values.length + ") 与 headerCols ("
                    + currentSheet.headerCols.size() + ") 不一致");
        }
        currentSheet.fields.add(Arrays.asList(values));
        return this;
    }

    /** 空 sheet（什么都不调，构造完返回） */
    public ExcelFixtureBuilder emptySheet(String sheetName) {
        sheet(sheetName);
        return this;
    }

    /** 在某个 sheet 的任意位置写一个字符串（用于排除 sheet 内容 / 异常 case 等） */
    public ExcelFixtureBuilder raw(int row, int col, String value) {
        if (currentSheet == null) {
            throw new IllegalStateException("先调 sheet() 再调 raw()");
        }
        currentSheet.rawCells.add(new RawCell(row, col, value));
        return this;
    }

    public byte[] buildBytes() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            for (SheetSpec spec : sheets) {
                Sheet sheet = wb.createSheet(spec.name);

                // 元信息行：J + K
                int rowIdx = META_START_ROW;
                for (MetaSpec m : spec.metas) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(COL_J).setCellValue(m.label);
                    row.createCell(COL_K).setCellValue(m.value);
                }

                // 字段表头行
                if (spec.headerCols != null) {
                    Row headerRow = sheet.createRow(rowIdx++);
                    for (int i = 0; i < spec.headerCols.size(); i++) {
                        headerRow.createCell(COL_J + i).setCellValue(spec.headerCols.get(i));
                    }
                    // 字段明细行
                    for (List<String> field : spec.fields) {
                        Row fieldRow = sheet.createRow(rowIdx++);
                        for (int i = 0; i < field.size(); i++) {
                            fieldRow.createCell(COL_J + i).setCellValue(field.get(i));
                        }
                    }
                }

                // 任意位置写值（raw）
                for (RawCell rc : spec.rawCells) {
                    Row row = sheet.getRow(rc.row);
                    if (row == null) row = sheet.createRow(rc.row);
                    row.createCell(rc.col).setCellValue(rc.value);
                }
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        }
    }

    /** 构造为 MockMultipartFile，参数 paramName 用于 Controller 的 @RequestParam 名 */
    public MultipartFile buildAsMultipartFile(String paramName) throws Exception {
        return new MockMultipartFile(paramName, fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildBytes());
    }

    // ---------- 内部数据结构 ----------
    private static class SheetSpec {
        final String name;
        final List<MetaSpec> metas = new ArrayList<>();
        List<String> headerCols;
        final List<List<String>> fields = new ArrayList<>();
        final List<RawCell> rawCells = new ArrayList<>();

        SheetSpec(String name) { this.name = name; }
    }

    private static class MetaSpec {
        final String label, value;
        MetaSpec(String l, String v) { label = l; value = v; }
    }

    private static class RawCell {
        final int row, col;
        final String value;
        RawCell(int r, int c, String v) { row = r; col = c; value = v; }
    }
}
