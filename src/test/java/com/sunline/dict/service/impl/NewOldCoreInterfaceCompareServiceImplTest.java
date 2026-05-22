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
}
