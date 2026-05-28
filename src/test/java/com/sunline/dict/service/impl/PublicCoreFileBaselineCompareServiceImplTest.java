package com.sunline.dict.service.impl;

import com.sunline.dict.service.PublicCoreFileBaselineCompareService;
import com.sunline.dict.testutil.ExcelAssert;
import com.sunline.dict.testutil.PublicCoreFileBaselineFixtureBuilder;
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
class PublicCoreFileBaselineCompareServiceImplTest {

    @Autowired PublicCoreFileBaselineCompareService service;

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
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528核心接收/生成文件", "528交易码", "文件编号", "文件分类")
                    .row("/cbs/ftp/A486", "核心生成文件", "A486", "", "回盘文件")
                    .row("/cbs/ftp/4013", "核心接收文件", "4013", "", "回盘文件")
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528核心接收/生成文件", "528交易码", "文件编号", "文件分类")
                    .row("/cbs/ftp/A486", "核心生成文件", "A486", "", "回盘文件")
                    .row("/cbs/ftp/4013", "核心接收文件", "4013", "", "回盘文件")
                .buildAsMultipartFile("compareFile");

        Map<String, Object> result = service.compareFiles(baseline, compare);
        String fileName = (String) result.get("fileName");
        assertNotNull(fileName, "应有结果文件名");
        resultFile = service.getResultFile(fileName);

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet eft = sheet(wb, "EFT文件基线");
            // 表头与数据
            cellValue(eft, 0, 0, "528核心全路径文件名");
            cellValue(eft, 1, 0, "/cbs/ftp/A486");
            cellValue(eft, 2, 0, "/cbs/ftp/4013");
            // 不应有任何颜色
            cellNoFill(eft, 1, 0);
            cellNoFill(eft, 2, 0);
            cellNoFill(eft, 1, 4);

            // 修订记录 sheet 应存在但仅表头
            Sheet rev = sheet(wb, "修订记录");
            cellValue(rev, 0, 0, "交易码");
            cellValue(rev, 0, 1, "修订级别");
            cellValue(rev, 0, 2, "修订方式");
            cellValue(rev, 0, 3, "修订明细");
            assertTrue(rev.getRow(1) == null
                            || rev.getRow(1).getCell(0) == null
                            || rev.getRow(1).getCell(0).toString().isEmpty(),
                    "无差异时修订记录应仅含表头");
        }
        assertEquals(0, ((Number) result.get("totalChanges")).intValue());
        assertEquals(2, ((Number) result.get("totalRows")).intValue());
    }

    @Test
    void row_added_marked_green() throws Exception {
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528交易码", "文件分类")
                    .row("/cbs/ftp/A486", "A486", "回盘文件")
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528交易码", "文件分类")
                    .row("/cbs/ftp/A486", "A486", "回盘文件")
                    .row("/cbs/ftp/NEW",  "NEW0", "来盘文件")    // 新增
                .buildAsMultipartFile("compareFile");

        Map<String, Object> result = service.compareFiles(baseline, compare);
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet eft = sheet(wb, "EFT文件基线");
            // 第 0 行 = 表头；第 1 行 = A486（无变化）；第 2 行 = NEW（新增）
            cellNoFill(eft, 1, 0);   // 原有行无填充
            cellBgColor(eft, 2, 0, IndexedColors.LIGHT_GREEN.getIndex(), "新增行 A 列");
            cellBgColor(eft, 2, 1, IndexedColors.LIGHT_GREEN.getIndex(), "新增行 B 列");
            cellBgColor(eft, 2, 2, IndexedColors.LIGHT_GREEN.getIndex(), "新增行 C 列");

            // 修订记录
            Sheet rev = sheet(wb, "修订记录");
            cellValue(rev, 1, 0, "EFT文件基线");
            cellValue(rev, 1, 1, "文件基线");
            cellValue(rev, 1, 2, "新增");
            String detail = rev.getRow(1).getCell(3).getStringCellValue();
            assertTrue(detail.contains("新增基线行") && detail.contains("/cbs/ftp/NEW"),
                    "修订明细应含'新增基线行：/cbs/ftp/NEW'，实际：" + detail);
            assertNotNull(rev.getRow(1).getCell(3).getHyperlink(),
                    "新增行的修订记录 D 列应有正向超链接");
        }
        assertEquals(1, ((Number) result.get("totalChanges")).intValue());
    }

    @Test
    void row_modified_only_diff_cols_yellow() throws Exception {
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528交易码", "文件编号", "文件分类")
                    .row("/cbs/ftp/A486", "A486", "436201", "回盘文件")
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528交易码", "文件编号", "文件分类")
                    .row("/cbs/ftp/A486", "A486", "466201", "来盘文件")  // 文件编号 + 文件分类两列修改
                .buildAsMultipartFile("compareFile");

        Map<String, Object> result = service.compareFiles(baseline, compare);
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet eft = sheet(wb, "EFT文件基线");
            // 列 0 / 1 不变 → 无填充；列 2 / 3 改 → 标黄
            cellNoFill(eft, 1, 0);
            cellNoFill(eft, 1, 1);
            cellBgColor(eft, 1, 2, IndexedColors.YELLOW.getIndex(), "文件编号修改");
            cellBgColor(eft, 1, 3, IndexedColors.YELLOW.getIndex(), "文件分类修改");

            // 修订记录
            Sheet rev = sheet(wb, "修订记录");
            cellValue(rev, 1, 2, "修改");
            String detail = rev.getRow(1).getCell(3).getStringCellValue();
            assertTrue(detail.contains("文件编号: 436201 → 466201"), "应包含文件编号 diff，实际：" + detail);
            assertTrue(detail.contains("文件分类: 回盘文件 → 来盘文件"), "应包含文件分类 diff，实际：" + detail);
        }
        assertEquals(1, ((Number) result.get("totalChanges")).intValue());
    }

    @Test
    void row_deleted_in_revision_only() throws Exception {
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528交易码")
                    .row("/cbs/ftp/A486", "A486")
                    .row("/cbs/ftp/OLD", "OLD0")   // 基线独有 → 删除
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528交易码")
                    .row("/cbs/ftp/A486", "A486")
                .buildAsMultipartFile("compareFile");

        Map<String, Object> result = service.compareFiles(baseline, compare);
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet eft = sheet(wb, "EFT文件基线");
            // 结果文件以对比文件为底本 → 只有 A486 一行数据
            cellValue(eft, 1, 0, "/cbs/ftp/A486");
            assertTrue(eft.getLastRowNum() == 1
                            || eft.getRow(2) == null
                            || eft.getRow(2).getCell(0) == null
                            || "".equals(eft.getRow(2).getCell(0).toString()),
                    "对比文件没有 OLD 行，结果文件也不应该有");

            // 修订记录：A486 不变 + OLD 删除 = 1 条
            Sheet rev = sheet(wb, "修订记录");
            cellValue(rev, 1, 2, "删除");
            String detail = rev.getRow(1).getCell(3).getStringCellValue();
            assertTrue(detail.contains("删除基线行") && detail.contains("/cbs/ftp/OLD"),
                    "应包含删除明细，实际：" + detail);
            // 删除条目不应有正向超链接（基线行在结果文件不存在）
            assertNull(rev.getRow(1).getCell(3).getHyperlink(),
                    "删除条目不应有超链接");
        }
        assertEquals(1, ((Number) result.get("totalChanges")).intValue());
    }

    @Test
    void column_union_when_widths_differ() throws Exception {
        // 基线 3 列；对比 4 列（多一列"文件分类"）
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528交易码", "文件编号")
                    .row("/cbs/ftp/A486", "A486", "436201")
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "528交易码", "文件编号", "文件分类")
                    .row("/cbs/ftp/A486", "A486", "436201", "回盘文件")
                .buildAsMultipartFile("compareFile");

        Map<String, Object> result = service.compareFiles(baseline, compare);
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet eft = sheet(wb, "EFT文件基线");
            // 新增的"文件分类"列应被识别为差异 → 标黄
            cellBgColor(eft, 1, 3, IndexedColors.YELLOW.getIndex(), "新增列触发修改标记");

            Sheet rev = sheet(wb, "修订记录");
            String detail = rev.getRow(1).getCell(3).getStringCellValue();
            assertTrue(detail.contains("文件分类") && detail.contains("回盘文件"),
                    "新增列应出现在修订明细，实际：" + detail);
        }
        assertEquals(1, ((Number) result.get("totalChanges")).intValue());
    }
}
