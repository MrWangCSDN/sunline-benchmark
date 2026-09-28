package com.sunline.dict.service.impl;

import com.sunline.dict.service.NewOldCoreErrorCodeCompareService;
import com.sunline.dict.testutil.ExcelAssert;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Map;

import static com.sunline.dict.testutil.ExcelAssert.cellBgColor;
import static com.sunline.dict.testutil.ExcelAssert.cellNoFill;
import static com.sunline.dict.testutil.ExcelAssert.cellValue;
import static com.sunline.dict.testutil.ExcelAssert.sheet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 新老核心错误码比对测试
 * 表头固定 A~G，唯一键 = 交易码 + 新响应码
 */
@SpringBootTest(properties = "flow-field-change.scan.enabled=false")
class NewOldCoreErrorCodeCompareServiceImplTest {

    /** 固定表头 */
    private static final String[] HEADERS = {
            "交易码", "交易名称", "分组", "新响应码", "新响应码错误描述",
            "调用方SOP格式调用防腐交易响应码", "调用方SOAP/JSON格式调用防腐交易响应码"
    };

    @Autowired
    NewOldCoreErrorCodeCompareService service;

    private File resultFile;

    @AfterEach
    void cleanup() {
        if (resultFile != null && resultFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            resultFile.delete();
        }
    }

    @Test
    void baseline_no_diff() throws Exception {
        String[] row = row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001");
        MultipartFile oldFile = errorCodeFile("old.xlsx", row);
        MultipartFile newFile = errorCodeFile("new.xlsx", row);

        Map<String, Object> result = service.compareFiles(oldFile, newFile, false);

        assertNull(result.get("needConfirm"), "单 sheet 不应要求确认");
        resultFile = service.getResultFile((String) result.get("fileName"));
        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet s = sheet(wb, "错误码");
            cellValue(s, 0, 0, "交易码");
            cellValue(s, 0, 6, "调用方SOAP/JSON格式调用防腐交易响应码");
            cellValue(s, 1, 0, "Y444");
            cellValue(s, 1, 3, "E001");

            // 无差异：不应有颜色
            for (int c = 0; c < HEADERS.length; c++) {
                cellNoFill(s, 1, c);
            }

            Sheet rev = sheet(wb, "修订记录");
            cellValue(rev, 0, 0, "交易码");
            cellValue(rev, 0, 1, "新响应码");
            cellValue(rev, 0, 2, "修订级别");
            cellValue(rev, 0, 3, "修订方式");
            cellValue(rev, 0, 4, "修订明细");
            assertTrue(rev.getRow(1) == null, "无差异时修订记录应仅含表头");
        }

        assertEquals(1, ((Number) result.get("totalRows")).intValue());
        assertEquals(0, ((Number) result.get("totalChanges")).intValue());
        assertEquals(0, ((Number) result.get("invalidRows")).intValue());
    }

    @Test
    void modified_columns_are_marked_and_listed() throws Exception {
        MultipartFile oldFile = errorCodeFile("old.xlsx",
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"));
        MultipartFile newFile = errorCodeFile("new.xlsx",
                // B 交易名称、C 分组、E 描述、F SOP、G SOAP 全部变化
                row("Y444", "客户信息查询(新)", "存款", "E001", "客户信息不存在", "61002", "62002"));

        Map<String, Object> result = service.compareFiles(oldFile, newFile, false);

        resultFile = service.getResultFile((String) result.get("fileName"));
        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet s = sheet(wb, "错误码");
            // 5 个比较列都标黄
            for (int c : new int[]{1, 2, 4, 5, 6}) {
                cellBgColor(s, 1, c, IndexedColors.YELLOW.getIndex(), "比较列 " + c + " 应标黄");
            }
            // 键列不比较，不染色
            cellNoFill(s, 1, 0);
            cellNoFill(s, 1, 3);
            // 结果 sheet 显示的是新值
            cellValue(s, 1, 1, "客户信息查询(新)");
            cellValue(s, 1, 2, "存款");

            Sheet rev = sheet(wb, "修订记录");
            assertEquals("修改", cellText(rev, 1, 3));
            assertEquals("Y444", cellText(rev, 1, 0));
            assertEquals("E001", cellText(rev, 1, 1));
            assertEquals("错误码", cellText(rev, 1, 2));
            String detail = cellText(rev, 1, 4);
            assertTrue(detail.contains("交易名称: 客户信息查询 → 客户信息查询(新)"), detail);
            assertTrue(detail.contains("分组: 公共 → 存款"), detail);
            assertTrue(detail.contains("新响应码错误描述: 客户不存在 → 客户信息不存在"), detail);
            assertTrue(detail.contains("调用方SOP格式调用防腐交易响应码: 61001 → 61002"), detail);
            assertTrue(detail.contains("调用方SOAP/JSON格式调用防腐交易响应码: 62001 → 62002"), detail);
        }

        assertEquals(1, ((Number) result.get("totalChanges")).intValue());
    }

    @Test
    void added_row_is_green_and_deleted_row_is_logged_only() throws Exception {
        MultipartFile oldFile = errorCodeFile("old.xlsx",
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"),
                row("Z999", "贷款查询", "贷款", "N001", "贷款不存在", "63001", "64001"));
        MultipartFile newFile = errorCodeFile("new.xlsx",
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"),
                row("X222", "卡信息查询", "卡业务", "E009", "卡不存在", "65001", "66001"));

        Map<String, Object> result = service.compareFiles(oldFile, newFile, false);

        resultFile = service.getResultFile((String) result.get("fileName"));
        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet s = sheet(wb, "错误码");
            // 结果以新版本为底本：表头 + Y444 + X222（旧独有 Z999 不追加）
            assertEquals(2, s.getLastRowNum(), "删除行不应追加到结果 sheet");
            cellValue(s, 1, 0, "Y444");
            cellValue(s, 2, 0, "X222");
            for (int r = 1; r <= 2; r++) {
                assertFalse(containsText(s, r, "Z999"), "结果 sheet 不应包含被删除的交易码 Z999");
            }

            // 新增行整行标绿
            for (int c = 0; c < HEADERS.length; c++) {
                cellBgColor(s, 2, c, IndexedColors.LIGHT_GREEN.getIndex(), "新增行第 " + c + " 列应标绿");
            }
            // 未变化的行不染色
            cellNoFill(s, 1, 4);

            Sheet rev = sheet(wb, "修订记录");
            // 排序：新增 < 删除
            assertEquals("新增", cellText(rev, 1, 3));
            assertEquals("X222", cellText(rev, 1, 0));
            assertEquals("E009", cellText(rev, 1, 1));
            assertTrue(cellText(rev, 1, 4).contains("新增错误码"), cellText(rev, 1, 4));

            assertEquals("删除", cellText(rev, 2, 3));
            assertEquals("Z999", cellText(rev, 2, 0));
            assertTrue(cellText(rev, 2, 4).contains("删除错误码"), cellText(rev, 2, 4));
        }

        assertEquals(2, ((Number) result.get("totalChanges")).intValue());
    }

    @Test
    void duplicate_key_last_row_wins() throws Exception {
        MultipartFile oldFile = errorCodeFile("old.xlsx",
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"));
        MultipartFile newFile = errorCodeFile("new.xlsx",
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"),
                // 重复键：后行覆盖前行
                row("Y444", "客户信息查询", "公共", "E001", "客户信息不存在", "61002", "62002"));

        Map<String, Object> result = service.compareFiles(oldFile, newFile, false);

        resultFile = service.getResultFile((String) result.get("fileName"));
        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet s = sheet(wb, "错误码");
            assertEquals(1, ((Number) result.get("totalRows")).intValue(), "重复键应只保留一行");
            // 后行（第 3 行）的值生效：结果里该键的值取自后行
            Sheet rev = sheet(wb, "修订记录");
            String detail = cellText(rev, 1, 4);
            assertTrue(detail.contains("客户不存在 → 客户信息不存在"), detail);
            assertNotNull(s);
        }
    }

    @Test
    void multi_sheet_needs_confirm_then_compares_first_sheet() throws Exception {
        MultipartFile oldFile = multiSheetFile("old.xlsx",
                new String[]{"错误码", "说明"},
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"));
        MultipartFile newFile = multiSheetFile("new.xlsx",
                new String[]{"错误码", "说明"},
                row("Y444", "客户信息查询", "公共", "E001", "客户信息不存在", "61001", "62001"));

        // 未确认 → 返回确认信息，不产出文件
        Map<String, Object> confirm = service.compareFiles(oldFile, newFile, false);
        assertEquals(Boolean.TRUE, confirm.get("needConfirm"));
        assertNull(confirm.get("fileName"));
        assertEquals(2, ((Number) confirm.get("oldSheetCount")).intValue());
        assertTrue(confirm.get("oldSheetNames").toString().contains("错误码"));

        // 确认后 → 只比对第一个 sheet
        Map<String, Object> result = service.compareFiles(oldFile, newFile, true);
        assertNull(result.get("needConfirm"));
        resultFile = service.getResultFile((String) result.get("fileName"));
        try (Workbook wb = ExcelAssert.open(resultFile)) {
            assertNotNull(wb.getSheet("错误码"));
            Sheet rev = sheet(wb, "修订记录");
            assertEquals("修改", cellText(rev, 1, 3));
        }
        assertEquals(1, ((Number) result.get("totalChanges")).intValue());
    }

    @Test
    void throws_when_header_is_wrong() throws Exception {
        String[] wrongHeader = HEADERS.clone();
        wrongHeader[0] = "错误列名";
        MultipartFile oldFile = errorCodeFile("old.xlsx", "错误码", wrongHeader,
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"));
        MultipartFile newFile = errorCodeFile("new.xlsx",
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.compareFiles(oldFile, newFile, false));
        assertTrue(ex.getMessage().contains("表头不正确"), ex.getMessage());
        assertTrue(ex.getMessage().contains("交易码"), ex.getMessage());
    }

    @Test
    void throws_when_header_row_missing() throws Exception {
        MultipartFile emptyOld = errorCodeFileWithoutHeader("old.xlsx");
        MultipartFile newFile = errorCodeFile("new.xlsx",
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.compareFiles(emptyOld, newFile, false));
        assertTrue(ex.getMessage().contains("缺少表头行"), ex.getMessage());
    }

    @Test
    void rows_without_key_are_skipped_and_counted() throws Exception {
        MultipartFile oldFile = errorCodeFile("old.xlsx",
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"));
        MultipartFile newFile = errorCodeFile("new.xlsx",
                row("Y444", "客户信息查询", "公共", "E001", "客户不存在", "61001", "62001"),
                row("", "缺少交易码", "公共", "E002", "描述", "61002", "62002"),
                row("X222", "缺少响应码", "卡业务", "", "描述", "65001", "66001"),
                new String[]{null, null, null, null, null, null, null});

        Map<String, Object> result = service.compareFiles(oldFile, newFile, false);

        assertEquals(1, ((Number) result.get("totalRows")).intValue(), "只有完整键的行参与比对");
        assertEquals(2, ((Number) result.get("invalidRows")).intValue(), "缺列行应被计入跳过");
        assertEquals(0, ((Number) result.get("totalChanges")).intValue());

        resultFile = service.getResultFile((String) result.get("fileName"));
    }

    // ==================== 辅助 ====================

    /** 7 列数据行 */
    private static String[] row(String tranCode, String name, String group, String respCode,
                                String desc, String sop, String soap) {
        return new String[]{tranCode, name, group, respCode, desc, sop, soap};
    }

    private MultipartFile errorCodeFile(String fileName, String[]... rows) throws Exception {
        return errorCodeFile(fileName, "错误码", HEADERS, rows);
    }

    private MultipartFile errorCodeFile(String fileName, String sheetName, String[] header, String[]... rows)
            throws Exception {
        return buildFile(fileName, new String[]{sheetName}, new String[][]{header}, new String[][][]{rows});
    }

    /** 只有 sheet 没有表头的文件 */
    private MultipartFile errorCodeFileWithoutHeader(String fileName) throws Exception {
        return buildFile(fileName, new String[]{"错误码"}, new String[][]{null}, new String[][][]{new String[0][0]});
    }

    /** 多 sheet 文件（每个 sheet 都用固定表头 + 同一批数据） */
    private MultipartFile multiSheetFile(String fileName, String[] sheetNames, String[]... rows) throws Exception {
        String[][] headers = new String[sheetNames.length][];
        String[][][] data = new String[sheetNames.length][][];
        for (int i = 0; i < sheetNames.length; i++) {
            headers[i] = HEADERS;
            data[i] = rows;
        }
        return buildFile(fileName, sheetNames, headers, data);
    }

    private MultipartFile buildFile(String fileName, String[] sheetNames, String[][] headers, String[][][] data)
            throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int s = 0; s < sheetNames.length; s++) {
                Sheet sheet = wb.createSheet(sheetNames[s]);
                if (headers[s] != null) {
                    Row headerRow = sheet.createRow(0);
                    for (int c = 0; c < headers[s].length; c++) {
                        headerRow.createCell(c).setCellValue(headers[s][c]);
                    }
                }
                String[][] rows = data[s];
                for (int r = 0; r < rows.length; r++) {
                    Row row = sheet.createRow(r + 1);
                    for (int c = 0; c < rows[r].length; c++) {
                        if (rows[r][c] != null) {
                            row.createCell(c).setCellValue(rows[r][c]);
                        }
                    }
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", fileName,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private static String cellText(Sheet sheet, int rowIdx, int col) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(cell);
    }

    private static boolean containsText(Sheet sheet, int rowIdx, String text) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) return false;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && new org.apache.poi.ss.usermodel.DataFormatter().formatCellValue(cell).contains(text)) {
                return true;
            }
        }
        return false;
    }
}
