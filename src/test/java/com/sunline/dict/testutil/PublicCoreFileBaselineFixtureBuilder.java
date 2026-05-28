package com.sunline.dict.testutil;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 测试用 Excel 构造器 — 对公核心文件基线
 * 模型：第 1 行表头（A 列起），第 2 行起为数据
 *
 * 用法：
 *   MultipartFile file = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
 *       .sheet("EFT文件基线")
 *           .header("528核心全路径文件名", "528核心接收/生成文件", "528交易码", "文件编号", "文件分类")
 *           .row("/cbs/ftp/A486", "核心生成文件", "A486", "", "回盘文件")
 *           .row("/cbs/ftp/4013", "核心接收文件", "4013", "", "回盘文件")
 *       .sheet("汇总")
 *           .raw(0, 0, "汇总内容")
 *       .buildAsMultipartFile("baselineFile");
 */
public class PublicCoreFileBaselineFixtureBuilder {

    private final String fileName;
    private final List<SheetSpec> sheets = new ArrayList<>();
    private SheetSpec currentSheet;

    private PublicCoreFileBaselineFixtureBuilder(String fileName) {
        this.fileName = fileName;
    }

    public static PublicCoreFileBaselineFixtureBuilder newBuilder(String fileName) {
        return new PublicCoreFileBaselineFixtureBuilder(fileName);
    }

    public PublicCoreFileBaselineFixtureBuilder sheet(String sheetName) {
        currentSheet = new SheetSpec(sheetName);
        sheets.add(currentSheet);
        return this;
    }

    /** 表头行（A 列起）— 必须先调 sheet() */
    public PublicCoreFileBaselineFixtureBuilder header(String... cols) {
        ensureSheet();
        if (cols.length == 0) {
            throw new IllegalArgumentException("header 至少有 1 列");
        }
        currentSheet.headerCols = Arrays.asList(cols);
        return this;
    }

    /** 数据行（按 header 顺序，A 列起） */
    public PublicCoreFileBaselineFixtureBuilder row(String... values) {
        ensureSheet();
        if (currentSheet.headerCols == null) {
            throw new IllegalStateException("先调 header() 再调 row()");
        }
        currentSheet.dataRows.add(Arrays.asList(values));
        return this;
    }

    /**
     * 任意位置写值（用于"汇总"等无结构 sheet 或边界 case）。
     * 注意：若坐标与 header()/row() 已写入的单元格重合，将静默覆盖旧值。
     */
    public PublicCoreFileBaselineFixtureBuilder raw(int row, int col, String value) {
        ensureSheet();
        currentSheet.rawCells.add(new RawCell(row, col, value));
        return this;
    }

    private void ensureSheet() {
        if (currentSheet == null) {
            throw new IllegalStateException("先调 sheet()");
        }
    }

    public byte[] buildBytes() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            for (SheetSpec spec : sheets) {
                Sheet sheet = wb.createSheet(spec.name);

                // 表头行（row 0）
                if (spec.headerCols != null) {
                    Row headerRow = sheet.createRow(0);
                    for (int i = 0; i < spec.headerCols.size(); i++) {
                        headerRow.createCell(i).setCellValue(spec.headerCols.get(i));
                    }
                    // 数据行（row 1+）
                    int rowIdx = 1;
                    for (List<String> data : spec.dataRows) {
                        Row dataRow = sheet.createRow(rowIdx++);
                        for (int i = 0; i < data.size(); i++) {
                            dataRow.createCell(i).setCellValue(data.get(i));
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

    public MultipartFile buildAsMultipartFile(String paramName) throws Exception {
        return new MockMultipartFile(paramName, fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                buildBytes());
    }

    // ---------- 内部数据结构 ----------
    private static class SheetSpec {
        final String name;
        List<String> headerCols;
        final List<List<String>> dataRows = new ArrayList<>();
        final List<RawCell> rawCells = new ArrayList<>();

        SheetSpec(String name) { this.name = name; }
    }

    private static class RawCell {
        final int row, col;
        final String value;
        RawCell(int r, int c, String v) { row = r; col = c; value = v; }
    }
}
