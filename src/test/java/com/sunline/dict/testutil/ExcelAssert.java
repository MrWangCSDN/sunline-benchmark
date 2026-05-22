package com.sunline.dict.testutil;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Excel 文件断言工具
 */
public class ExcelAssert {

    /** 断言文件存在且能打开 */
    public static Workbook open(File file) throws Exception {
        assertTrue(file.exists(), "结果文件应存在: " + file.getAbsolutePath());
        FileInputStream fis = new FileInputStream(file);
        try {
            // XSSFWorkbook 读完会缓存数据，但不自动关闭 InputStream；这里手动关闭避免 fd 泄漏
            Workbook wb = new XSSFWorkbook(fis);
            fis.close();
            return wb;
        } catch (Exception e) {
            fis.close();
            throw e;
        }
    }

    /** 断言 sheet 存在 */
    public static Sheet sheet(Workbook wb, String name) {
        Sheet s = wb.getSheet(name);
        assertNotNull(s, "应存在 sheet: " + name);
        return s;
    }

    /** 断言 sheet 不存在 */
    public static void noSheet(Workbook wb, String name) {
        assertNull(wb.getSheet(name), "不应存在 sheet: " + name);
    }

    /** 断言单元格值 */
    public static void cellValue(Sheet sheet, int row, int col, String expected) {
        Row r = sheet.getRow(row);
        assertNotNull(r, "sheet[" + sheet.getSheetName() + "] 行" + row + " 应存在");
        Cell c = r.getCell(col);
        // 单元格不存在 + expected 非空 → 这是个 bug，明确报错（避免 null 被悄悄当成 "" 通过）
        if (c == null && !expected.isEmpty()) {
            fail("sheet[" + sheet.getSheetName() + "] (" + row + "," + col + ") 单元格不存在，但 expected=" + expected);
        }
        String actual = c == null ? "" : new DataFormatter().formatCellValue(c).trim();
        assertEquals(expected, actual,
                "sheet[" + sheet.getSheetName() + "] (" + row + "," + col + ") 单元格值不符");
    }

    /** 断言单元格背景色（IndexedColors 的 short index 比对） */
    public static void cellBgColor(Sheet sheet, int row, int col, short expectedIndex, String label) {
        Row r = sheet.getRow(row);
        assertNotNull(r, label + " — 行" + row + " 不存在");
        Cell c = r.getCell(col);
        assertNotNull(c, label + " 单元格(" + row + "," + col + ") 不存在");
        CellStyle style = c.getCellStyle();
        assertEquals(expectedIndex, style.getFillForegroundColor(),
                label + " 单元格(" + row + "," + col + ") 背景色不符");
    }

    /** 断言单元格字体含删除线 */
    public static void cellHasStrikeout(Workbook wb, Sheet sheet, int row, int col, String label) {
        Row r = sheet.getRow(row);
        assertNotNull(r, label + " — 行" + row + " 不存在");
        Cell c = r.getCell(col);
        assertNotNull(c, label + " 单元格(" + row + "," + col + ") 不存在");
        Font font = wb.getFontAt(c.getCellStyle().getFontIndex());
        assertTrue(font.getStrikeout(), label + " 单元格应有删除线");
    }

    /** 断言单元格无背景色（默认/白色） */
    public static void cellNoFill(Sheet sheet, int row, int col) {
        Cell c = sheet.getRow(row).getCell(col);
        if (c == null) return;  // null cell 自然无填充
        short fillIdx = c.getCellStyle().getFillForegroundColor();
        // 0 = AUTO / 64 = AUTO 也算无填充
        assertTrue(fillIdx == IndexedColors.AUTOMATIC.getIndex() || fillIdx == 64,
                "(" + row + "," + col + ") 应无填充，实际 " + fillIdx);
    }

    /** 断言"修订记录"sheet 的某一行内容（A=交易码, B=级别, C=方式, D=明细） */
    public static void revisionRow(Sheet revSheet, int row, String txnCode, String level, String way, String detail) {
        cellValue(revSheet, row, 0, txnCode);
        cellValue(revSheet, row, 1, level);
        cellValue(revSheet, row, 2, way);
        cellValue(revSheet, row, 3, detail);
    }

    /** 列出 sheet 名列表 */
    public static List<String> sheetNames(Workbook wb) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            names.add(wb.getSheetName(i));
        }
        return names;
    }
}
