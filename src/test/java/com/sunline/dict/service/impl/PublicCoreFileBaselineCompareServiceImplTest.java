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
}
