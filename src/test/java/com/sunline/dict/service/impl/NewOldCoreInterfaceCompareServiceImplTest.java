package com.sunline.dict.service.impl;

import com.sunline.dict.service.NewOldCoreInterfaceCompareService;
import com.sunline.dict.testutil.ExcelAssert;
import com.sunline.dict.testutil.ExcelFixtureBuilder;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

import static com.sunline.dict.testutil.ExcelAssert.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NewOldCoreInterfaceCompareServiceImplTest {

    @Autowired NewOldCoreInterfaceCompareService service;

    private File resultFile;  // 每个测试用例的结果文件，AfterEach 清理

    @AfterEach
    void cleanup() {
        if (resultFile != null && resultFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            resultFile.delete();
        }
    }

    @Test
    void baseline_no_diff() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("985501")
                    .meta("交易码", "9855")
                    .meta("接口名称", "理财业务销账销户处理")
                    .headerCols("列中文名", "列顺序", "列数据类型", "列最大长度", "是否非空")
                    .field("产品编号", "1", "string", "10", "Y")
                    .field("交易种类", "2", "string", "1", "Y")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("985501")
                    .meta("交易码", "9855")
                    .meta("接口名称", "理财业务销账销户处理")
                    .headerCols("列中文名", "列顺序", "列数据类型", "列最大长度", "是否非空")
                    .field("产品编号", "1", "string", "10", "Y")
                    .field("交易种类", "2", "string", "1", "Y")
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "说明,修订记录,索引");
        String fileName = (String) result.get("fileName");
        assertNotNull(fileName, "结果文件名应有");

        resultFile = service.getResultFile(fileName);
        try (Workbook wb = ExcelAssert.open(resultFile)) {
            // sheet "985501" 应存在，内容跟 newFile 一致
            Sheet s = sheet(wb, "985501");
            cellValue(s, 1, 9, "交易码");
            cellValue(s, 1, 10, "9855");
            cellValue(s, 2, 9, "接口名称");

            // 不应有任何颜色标记
            cellNoFill(s, 1, 10);
            cellNoFill(s, 2, 10);

            // 修订记录 sheet 应存在但只有表头
            Sheet rev = sheet(wb, "修订记录");
            // 第 0 行（表头）：交易码 / 修订级别 / 修订方式 / 修订明细
            cellValue(rev, 0, 0, "交易码");
            cellValue(rev, 0, 1, "修订级别");
            cellValue(rev, 0, 2, "修订方式");
            cellValue(rev, 0, 3, "修订明细");
            // 第 1 行应不存在或全空
            assertTrue(rev.getRow(1) == null || rev.getRow(1).getCell(0) == null
                    || rev.getRow(1).getCell(0).toString().isEmpty(),
                    "无差异时修订记录应仅含表头");
        }

        // result map 验证
        assertEquals(0, ((Number) result.get("totalChanges")).intValue(), "差异数应为 0");
    }

    @Test
    void field_added_modified_deleted() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .headerCols("列中文名", "列顺序", "列数据类型", "列最大长度", "是否非空")
                    .field("产品编号", "1", "string", "10", "Y")
                    .field("交易种类", "2", "string", "1", "Y")
                    .field("废弃字段", "3", "string", "5", "N")  // 旧独有
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .headerCols("列中文名", "列顺序", "列数据类型", "列最大长度", "是否非空")
                    .field("产品编号", "1", "string", "10", "Y")
                    .field("交易种类", "2", "decimal(24,2)", "24", "Y")  // 数据类型+长度修改
                    .field("新字段", "4", "string", "8", "Y")  // 新独有
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "说明,修订记录,索引");
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet s = sheet(wb, "985501");
            // 数据行：表头在行 2（0-based）—— 1 行元信息 + 表头
            // 字段从行 3 起：产品编号、交易种类、新字段
            // 验证"交易种类"的列数据类型(L列)、列最大长度(M列)被标黄
            cellValue(s, 4, 9, "交易种类");
            cellBgColor(s, 4, 11, IndexedColors.YELLOW.getIndex(), "列数据类型修改");
            cellBgColor(s, 4, 12, IndexedColors.YELLOW.getIndex(), "列最大长度修改");
            // 其他列(列顺序、是否非空)不变 → 无填充
            cellNoFill(s, 4, 10);  // 列顺序
            cellNoFill(s, 4, 13);  // 是否非空

            // "新字段" 整行标绿
            cellValue(s, 5, 9, "新字段");
            cellBgColor(s, 5, 9, IndexedColors.LIGHT_GREEN.getIndex(), "新字段 J");
            cellBgColor(s, 5, 10, IndexedColors.LIGHT_GREEN.getIndex(), "新字段 K");
            cellBgColor(s, 5, 11, IndexedColors.LIGHT_GREEN.getIndex(), "新字段 L");

            // "废弃字段" 不在新版本里，结果文件没这一行
            // 但应在修订记录里
            Sheet rev = sheet(wb, "修订记录");
            int foundDeleted = 0, foundAdded = 0, foundModified = 0;
            for (int r = 1; r <= rev.getLastRowNum(); r++) {
                String detail = rev.getRow(r).getCell(3).getStringCellValue();
                if (detail.contains("删除字段") && detail.contains("废弃字段")) foundDeleted++;
                if (detail.contains("新增字段") && detail.contains("新字段")) foundAdded++;
                if (detail.contains("字段[交易种类]") && detail.contains("string") && detail.contains("decimal")) foundModified++;
            }
            assertEquals(1, foundDeleted);
            assertEquals(1, foundAdded);
            assertEquals(1, foundModified);
        }
    }

    @Test
    void sheet_added_and_deleted() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .meta("接口名称", "理财业务销账销户处理")
                    .headerCols("列中文名", "列顺序", "列数据类型")
                    .field("产品编号", "1", "string")
                .sheet("OLD_ONLY")  // 旧独有 → 接口删除
                    .meta("交易码", "OLD001")
                    .meta("接口名称", "老接口")
                    .headerCols("列中文名", "列顺序", "列数据类型")
                    .field("旧字段", "1", "string")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .meta("接口名称", "理财业务销账销户处理")
                    .headerCols("列中文名", "列顺序", "列数据类型")
                    .field("产品编号", "1", "string")
                .sheet("NEW_ONLY")  // 新独有 → 接口新增
                    .meta("交易码", "NEW001")
                    .meta("接口名称", "新接口")
                    .headerCols("列中文名", "列顺序", "列数据类型")
                    .field("新字段", "1", "string")
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "说明,修订记录,索引");
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            // 985501 不变 / NEW_ONLY 在结果里 / OLD_ONLY 不在结果里
            sheet(wb, "985501");
            sheet(wb, "NEW_ONLY");
            noSheet(wb, "OLD_ONLY");

            // NEW_ONLY 整体标绿（J 列起所有非空单元格）
            Sheet newOnly = wb.getSheet("NEW_ONLY");
            cellBgColor(newOnly, 1, 9, IndexedColors.LIGHT_GREEN.getIndex(), "NEW_ONLY 交易码 J");
            cellBgColor(newOnly, 1, 10, IndexedColors.LIGHT_GREEN.getIndex(), "NEW_ONLY 交易码 K");

            // 修订记录
            Sheet rev = sheet(wb, "修订记录");
            int foundAdded = 0, foundDeleted = 0;
            for (int r = 1; r <= rev.getLastRowNum(); r++) {
                String detail = rev.getRow(r).getCell(3).getStringCellValue();
                if (detail.contains("新增接口") && detail.contains("新接口")) foundAdded++;
                if (detail.contains("删除接口") && detail.contains("老接口")) foundDeleted++;
            }
            assertEquals(1, foundAdded);
            assertEquals(1, foundDeleted);
        }
    }

    @Test
    void metainfo_modified() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("985501")
                    .meta("交易码", "9855")
                    .meta("接口名称", "理财业务销账销户处理")
                    .meta("文件名", "Lerx_old.txt")
                    .headerCols("列中文名", "列顺序", "列数据类型")
                    .field("产品编号", "1", "string")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")                              // 修改
                    .meta("接口名称", "理财业务销账销户处理")           // 不变
                    // "文件名" 删除
                    .meta("传出目录", "ccbs/output")                      // 新增
                    .headerCols("列中文名", "列顺序", "列数据类型")
                    .field("产品编号", "1", "string")
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "说明,修订记录,索引");
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet s = sheet(wb, "985501");

            // 交易码"修改" → K 列黄色
            cellValue(s, 1, 10, "UD49");
            cellBgColor(s, 1, 10, IndexedColors.YELLOW.getIndex(), "交易码 K 列");

            // 接口名称"不变" → 无填充
            cellNoFill(s, 2, 10);

            // 传出目录"新增" → J + K 绿色
            // 新增项在新版本里位置是第 3 行（0-based 行 3）
            cellValue(s, 3, 9, "传出目录");
            cellBgColor(s, 3, 9, IndexedColors.LIGHT_GREEN.getIndex(), "传出目录 J 列");
            cellBgColor(s, 3, 10, IndexedColors.LIGHT_GREEN.getIndex(), "传出目录 K 列");

            // 修订记录：3 条（交易码 修改 / 传出目录 新增 / 文件名 删除）
            Sheet rev = sheet(wb, "修订记录");
            // 验证条目数（不验证排序，留 Task 11）
            int found = 0;
            for (int r = 1; r <= rev.getLastRowNum(); r++) {
                String detail = rev.getRow(r).getCell(3).getStringCellValue();
                if (detail.contains("交易码") && detail.contains("9855") && detail.contains("UD49")) found++;
                if (detail.contains("新增项") && detail.contains("传出目录")) found++;
                if (detail.contains("删除项") && detail.contains("文件名")) found++;
            }
            assertEquals(3, found, "应有 3 条修订记录（修改/新增/删除）");
        }

        assertEquals(3, ((Number) result.get("totalChanges")).intValue());
    }
}
