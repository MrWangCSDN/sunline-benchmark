# 对公核心文件基线比对 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 sunline-benchmark 项目新增"对公核心文件基线比对"功能 — 上传两个 Excel，对 `EFT文件基线` 这一个 sheet 进行整行差异比对（A 列为唯一键），结果生成带颜色标记和双向超链接的修订记录。

**Architecture:** 复用现有 `ExcelCompareServiceImpl` 工程基础设施（`StyleCache` / `RevisionEntry` / `paintCell` / `writeRevisionSheet`），新增 `CompareMode.PUBLIC_CORE_FILE_BASELINE` 枚举 + Controller/Service 薄壳 + 新的入口方法 `comparePublicCoreFileBaseline`。前端复制 `new-old-core-interface-compare.html` 改造（移除"排除 sheet"输入框）。菜单注入沿用 SQL + index.html 4 处修改模式。

**Tech Stack:** Java 17 + Spring Boot 3.1.5 + Apache POI（Excel 读写）+ JUnit 5 + 前端 HTML/JS（无框架）+ MySQL（sys_menu 表注入）

**Spec reference:** `/Users/java/obsidian/01 Engineering/sunline-benchmark/对公核心文件基线比对-设计.md`

---

## File Structure

**Backend (Java)**:
- Modify: `src/main/java/com/sunline/dict/common/CompareMode.java` — 新增枚举值
- Modify: `src/main/java/com/sunline/dict/service/ExcelCompareService.java` — 新增接口方法
- Modify: `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java` — 新增主算法、辅助方法、`RowData` 内部类、3 个 `RevisionEntry` 工厂方法
- Create: `src/main/java/com/sunline/dict/service/PublicCoreFileBaselineCompareService.java` — Service interface（薄壳）
- Create: `src/main/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImpl.java` — Service impl（薄壳，委托给 `ExcelCompareServiceImpl`）
- Create: `src/main/java/com/sunline/dict/controller/PublicCoreFileBaselineCompareController.java` — REST 端点（薄壳）

**Frontend (HTML/JS)**:
- Create: `src/main/resources/static/public-core-file-baseline-compare.html` — 子页面（复制改造）
- Modify: `src/main/resources/static/index.html` — 4 处菜单注入

**SQL**:
- Create: `src/main/resources/sql/add_public_core_file_baseline_menu.sql`

**Tests**:
- Create: `src/test/java/com/sunline/dict/testutil/PublicCoreFileBaselineFixtureBuilder.java` — 测试 fixture
- Create: `src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java` — 单元测试

---

## Task 1: 后端骨架（CompareMode + Service interface + Service impl + Controller，暂 throw UnsupportedOperationException）

**Files:**
- Modify: `src/main/java/com/sunline/dict/common/CompareMode.java`
- Modify: `src/main/java/com/sunline/dict/service/ExcelCompareService.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java`
- Create: `src/main/java/com/sunline/dict/service/PublicCoreFileBaselineCompareService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImpl.java`
- Create: `src/main/java/com/sunline/dict/controller/PublicCoreFileBaselineCompareController.java`

- [ ] **Step 1.1：在 CompareMode 末尾新增枚举值 `PUBLIC_CORE_FILE_BASELINE`**

修改 `src/main/java/com/sunline/dict/common/CompareMode.java`：在 `NEW_OLD_CORE_INTERFACE` 之后追加（注意将其分号改为逗号）：

```java
    NEW_OLD_CORE_INTERFACE,

    /**
     * 对公核心文件基线比对模式
     * - 左边：基线文件，右边：对比文件（作为输出底本）
     * - 只比对名为 "EFT文件基线" 的 sheet（其他 sheet 原样从对比文件复制到结果，不参与比对）
     * - 表头行：第 1 行（0-based row 0）；数据起始：第 2 行（0-based row 1）
     * - 唯一键：A 列文本（trim 后）
     * - 列范围：A 列起到第一个空，取两侧并集（对比文件顺序在前、基线独有列追加在尾）
     * - 差异标记：新增=绿、修改=黄（仅差异列）、删除=灰+删除线（仅在修订记录登记，不复制旧行）
     * - 任一侧文件无 EFT文件基线 sheet → 抛错
     */
    PUBLIC_CORE_FILE_BASELINE
```

- [ ] **Step 1.2：在 `ExcelCompareService` 接口加方法 `comparePublicCoreFileBaseline`**

修改 `src/main/java/com/sunline/dict/service/ExcelCompareService.java`，在 `compareNewOldCoreInterfaces` 之后、`getResultFile` 之前插入：

```java
    /**
     * 对公核心文件基线比对模式
     * 只比对名为 "EFT文件基线" 的 sheet，整行 diff，A 列为唯一键
     *
     * @param baselineFile 基线文件（比对基准）
     * @param compareFile  对比文件（作为输出底本，差异画在它上面）
     * @return 比较结果信息，包含 fileName, totalRows, totalChanges 等字段
     */
    Map<String, Object> comparePublicCoreFileBaseline(
            MultipartFile baselineFile, MultipartFile compareFile) throws Exception;
```

- [ ] **Step 1.3：在 `ExcelCompareServiceImpl` 末尾追加方法实现（先 throw UnsupportedOperationException 占位）**

打开 `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java`，在 `compareNewOldCoreInterfaces` 方法之后（搜 `return ret;` 配合 `compareNewOldCoreInterfaces` 定位收尾位置后的合适处）、`parseExcludeSheets` 之前插入：

```java
    /**
     * 对公核心文件基线比对入口（占位实现，Task 3 起填充）
     */
    @Override
    public Map<String, Object> comparePublicCoreFileBaseline(
            MultipartFile baselineFile, MultipartFile compareFile) throws Exception {
        throw new UnsupportedOperationException("尚未实现（计划由 Task 3 起 TDD 实现）");
    }
```

- [ ] **Step 1.4：创建 `PublicCoreFileBaselineCompareService` interface**

创建 `src/main/java/com/sunline/dict/service/PublicCoreFileBaselineCompareService.java`：

```java
package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 对公核心文件基线比对服务接口
 */
public interface PublicCoreFileBaselineCompareService {

    /**
     * 比较两个对公核心文件基线 Excel
     *
     * @param baselineFile 基线文件（比对基准）
     * @param compareFile  对比文件（作为输出底本）
     * @return 比较结果信息（fileName、totalRows、totalChanges 等）
     */
    Map<String, Object> compareFiles(MultipartFile baselineFile, MultipartFile compareFile)
            throws Exception;

    /**
     * 获取结果文件
     */
    File getResultFile(String fileName);
}
```

- [ ] **Step 1.5：创建 `PublicCoreFileBaselineCompareServiceImpl`（薄壳）**

创建 `src/main/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImpl.java`：

```java
package com.sunline.dict.service.impl;

import com.sunline.dict.service.PublicCoreFileBaselineCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 对公核心文件基线比对服务实现
 * 委托给 ExcelCompareServiceImpl.comparePublicCoreFileBaseline
 */
@Service
public class PublicCoreFileBaselineCompareServiceImpl implements PublicCoreFileBaselineCompareService {

    private static final Logger log = LoggerFactory.getLogger(PublicCoreFileBaselineCompareServiceImpl.class);

    @Autowired
    private ExcelCompareServiceImpl excelCompareService;

    @Override
    public Map<String, Object> compareFiles(MultipartFile baselineFile, MultipartFile compareFile)
            throws Exception {
        log.info("开始对公核心文件基线比对");
        return excelCompareService.comparePublicCoreFileBaseline(baselineFile, compareFile);
    }

    @Override
    public File getResultFile(String fileName) {
        return excelCompareService.getResultFile(fileName);
    }
}
```

- [ ] **Step 1.6：创建 `PublicCoreFileBaselineCompareController`**

创建 `src/main/java/com/sunline/dict/controller/PublicCoreFileBaselineCompareController.java`：

```java
package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.PublicCoreFileBaselineCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 对公核心文件基线比对控制器
 */
@RestController
@RequestMapping("/api/public-core-file-baseline")
public class PublicCoreFileBaselineCompareController {

    private static final Logger log = LoggerFactory.getLogger(PublicCoreFileBaselineCompareController.class);

    @Autowired
    private PublicCoreFileBaselineCompareService service;

    /**
     * 比对两个对公核心文件基线 Excel
     */
    @PostMapping("/compare")
    public Result<Map<String, Object>> compare(
            @RequestParam("baselineFile") MultipartFile baselineFile,
            @RequestParam("compareFile") MultipartFile compareFile) {

        try {
            log.info("收到对公核心文件基线比对请求 baseline={} compare={}",
                    baselineFile.getOriginalFilename(), compareFile.getOriginalFilename());

            if (baselineFile.isEmpty() || compareFile.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String baselineName = baselineFile.getOriginalFilename();
            String compareName = compareFile.getOriginalFilename();
            if (baselineName == null || compareName == null
                    || (!baselineName.endsWith(".xlsx") && !baselineName.endsWith(".xls"))
                    || (!compareName.endsWith(".xlsx") && !compareName.endsWith(".xls"))) {
                return Result.error("文件格式不正确，只支持.xlsx和.xls格式");
            }

            Map<String, Object> result = service.compareFiles(baselineFile, compareFile);
            log.info("对公核心文件基线比对完成，结果文件: {}", result.get("fileName"));
            return Result.success(result);

        } catch (Exception e) {
            log.error("对公核心文件基线比对失败", e);
            return Result.error("比较失败：" + e.getMessage());
        }
    }

    /**
     * 下载比较结果
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadResult(@PathVariable String fileName) {
        try {
            File file = service.getResultFile(fileName);
            if (!file.exists()) {
                log.error("结果文件不存在: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(file);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment",
                    URLEncoder.encode(fileName, StandardCharsets.UTF_8));

            return ResponseEntity.ok().headers(headers).body(resource);

        } catch (Exception e) {
            log.error("下载结果文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
```

- [ ] **Step 1.7：编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS（无编译错误）

- [ ] **Step 1.8：提交 Task 1**

```bash
git add src/main/java/com/sunline/dict/common/CompareMode.java \
        src/main/java/com/sunline/dict/service/ExcelCompareService.java \
        src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java \
        src/main/java/com/sunline/dict/service/PublicCoreFileBaselineCompareService.java \
        src/main/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImpl.java \
        src/main/java/com/sunline/dict/controller/PublicCoreFileBaselineCompareController.java
git commit -m "feat: 对公核心文件基线比对 后端骨架（CompareMode/Service/Controller）"
```

---

## Task 2: 测试 fixture builder

**Files:**
- Create: `src/test/java/com/sunline/dict/testutil/PublicCoreFileBaselineFixtureBuilder.java`

> 现有 `ExcelFixtureBuilder` 绑定"J 列起 + 列中文名"模型，不适合本需求"A 列起 + 第 1 行表头"，新建独立 builder，单一职责。

- [ ] **Step 2.1：创建 `PublicCoreFileBaselineFixtureBuilder`**

创建 `src/test/java/com/sunline/dict/testutil/PublicCoreFileBaselineFixtureBuilder.java`：

```java
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

    /** 任意位置写值（用于"汇总"等无结构 sheet 或边界 case） */
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
```

- [ ] **Step 2.2：编译验证**

Run: `mvn -q -DskipTests test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 2.3：提交 Task 2**

```bash
git add src/test/java/com/sunline/dict/testutil/PublicCoreFileBaselineFixtureBuilder.java
git commit -m "test: 新增 PublicCoreFileBaselineFixtureBuilder（A 列起表头模型）"
```

---

## Task 3: TDD — `baseline_no_diff`（首个测试驱动核心实现）

**Files:**
- Create: `src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java`

> 这是核心 TDD 任务，会让 `comparePublicCoreFileBaseline` 主流程 + 辅助方法 + `RowData` 内部类 + `RevisionEntry` 3 个工厂方法首次落地。

- [ ] **Step 3.1：写失败测试 `baseline_no_diff`**

创建 `src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java`：

```java
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
```

- [ ] **Step 3.2：跑测试验证它失败**

Run: `mvn -q -Dtest=PublicCoreFileBaselineCompareServiceImplTest#baseline_no_diff test`
Expected: FAIL — `UnsupportedOperationException: 尚未实现（计划由 Task 3 起 TDD 实现）`

- [ ] **Step 3.3：在 `ExcelCompareServiceImpl` 末尾追加 `RowData` 内部类（紧挨已有 `FieldRow`/`MetaCell` 之后）**

打开 `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java`，找到 `private static class MetaCell { ... }` 这个内部类，在它之后追加：

```java
    /** 对公核心文件基线 — 一行数据 */
    private static class RowData {
        final Map<String, String> values;  // 列名 → 单元格显示值（trim 后）
        final int row;                      // 在对比文件中的物理行号（0-based）
        RowData(Map<String, String> v, int r) { values = v; row = r; }
    }
```

- [ ] **Step 3.4：在 `RevisionEntry` 类内末尾新增 3 个工厂方法**

打开 `ExcelCompareServiceImpl.java` 找到 `private static class RevisionEntry { ... }`，在 `fieldDeleted` 方法之后、闭合 `}` 之前插入：

```java
        // ===== 对公核心文件基线比对（PUBLIC_CORE_FILE_BASELINE）专用工厂 =====

        static RevisionEntry baselineAdded(String sheetName, String key, int row) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "文件基线";
            e.way = "新增";
            e.detail = "新增基线行：" + key;
            e.linkSheetName = sheetName;
            e.linkRow = row;
            e.linkCol = 0;
            return e;
        }

        static RevisionEntry baselineModified(String sheetName, String key, List<String> diffs, int row) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "文件基线";
            e.way = "修改";
            e.detail = "基线行[" + key + "] " + String.join("; ", diffs);
            e.linkSheetName = sheetName;
            e.linkRow = row;
            e.linkCol = 0;
            return e;
        }

        static RevisionEntry baselineDeleted(String sheetName, String key) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "文件基线";
            e.way = "删除";
            e.detail = "删除基线行：" + key;
            return e;
        }
```

- [ ] **Step 3.5：实现 `comparePublicCoreFileBaseline` 主流程（替换 Task 1 的占位）**

把 Task 1 中那段 `throw new UnsupportedOperationException(...)` 整段替换为：

```java
    /**
     * 对公核心文件基线比对入口
     */
    @Override
    public Map<String, Object> comparePublicCoreFileBaseline(
            MultipartFile baselineFile, MultipartFile compareFile) throws Exception {

        final String TARGET_SHEET = "EFT文件基线";
        log.info("开始对公核心文件基线比对，目标 sheet=[{}]", TARGET_SHEET);

        // 创建输出目录
        File resultDir = new File(RESULT_DIR);
        if (!resultDir.exists()) resultDir.mkdirs();

        // 调整 Zip bomb 阈值（与现有模式一致）
        ZipSecureFile.setMinInflateRatio(0.001);

        try (Workbook baselineWb = WorkbookFactory.create(baselineFile.getInputStream());
             Workbook compareWb  = WorkbookFactory.create(compareFile.getInputStream());
             Workbook resultWb   = new XSSFWorkbook()) {

            StyleCache styles = new StyleCache(resultWb);
            List<RevisionEntry> revisions = new ArrayList<>();

            // ① 复制对比文件所有 sheet（保持顺序）
            Sheet targetResultSheet = null;
            for (int i = 0; i < compareWb.getNumberOfSheets(); i++) {
                String name = compareWb.getSheetName(i);
                Sheet src = compareWb.getSheetAt(i);
                Sheet dst = resultWb.createSheet(name);
                copySheetContent(src, dst);
                if (TARGET_SHEET.equals(name)) targetResultSheet = dst;
            }

            // ② 校验：两侧都必须有 EFT文件基线
            Sheet baselineSheet = baselineWb.getSheet(TARGET_SHEET);
            Sheet compareSheet  = compareWb.getSheet(TARGET_SHEET);
            if (baselineSheet == null) {
                throw new RuntimeException("基线文件缺少 sheet[" + TARGET_SHEET + "]");
            }
            if (compareSheet == null) {
                throw new RuntimeException("对比文件缺少 sheet[" + TARGET_SHEET + "]");
            }

            // ③ 表头并集
            List<String> baselineCols = scanHeaderColsFromA(baselineSheet, 0);
            List<String> compareCols  = scanHeaderColsFromA(compareSheet,  0);
            LinkedHashSet<String> unionCols = new LinkedHashSet<>(compareCols);
            unionCols.addAll(baselineCols);   // 基线独有列追加在尾部

            // ④ 读数据区
            Map<String, RowData> baselineRows = readRowsFromA(baselineSheet, 1, baselineCols);
            Map<String, RowData> compareRows  = readRowsFromA(compareSheet,  1, compareCols);

            // ⑤ 整行 diff
            diffRows(baselineRows, compareRows, unionCols, TARGET_SHEET,
                     targetResultSheet, styles, revisions);

            // ⑥ 修订记录 sheet
            writeRevisionSheet(resultWb, revisions, Collections.emptySet(), styles);

            // ⑦ 写盘
            String fileName = "public-core-file-baseline-compare-"
                    + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()) + ".xlsx";
            File out = new File(resultDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                resultWb.write(fos);
            }

            log.info("对公核心文件基线比对完成，对比行数={}，差异数={}",
                    compareRows.size(), revisions.size());

            Map<String, Object> ret = new HashMap<>();
            ret.put("fileName", fileName);
            ret.put("totalRows", compareRows.size());
            ret.put("totalChanges", revisions.size());
            return ret;
        }
    }

    /** 表头行从 A 列起向右扫，直到第一个空单元格 */
    private List<String> scanHeaderColsFromA(Sheet sheet, int headerRow) {
        Row row = sheet.getRow(headerRow);
        if (row == null) {
            throw new RuntimeException("sheet[" + sheet.getSheetName() + "] 表头行 "
                    + (headerRow + 1) + " 不存在");
        }
        DataFormatter df = new DataFormatter();
        List<String> cols = new ArrayList<>();
        int lastCellNum = row.getLastCellNum();
        for (int c = 0; c < lastCellNum; c++) {
            Cell cell = row.getCell(c);
            String v = cell == null ? "" : df.formatCellValue(cell).trim();
            if (v.isEmpty()) break;
            cols.add(v);
        }
        if (cols.isEmpty()) {
            throw new RuntimeException("sheet[" + sheet.getSheetName() + "] 表头行 A 列为空");
        }
        return cols;
    }

    /** 数据区 → Map<A列文本, RowData>（保持物理顺序） */
    private Map<String, RowData> readRowsFromA(Sheet sheet, int fromRow, List<String> cols) {
        DataFormatter df = new DataFormatter();
        Map<String, RowData> map = new LinkedHashMap<>();
        for (int r = fromRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell aCell = row.getCell(0);
            String key = aCell == null ? "" : df.formatCellValue(aCell).trim();
            if (key.isEmpty()) continue;   // 空 A 列视为空行，跳过

            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < cols.size(); i++) {
                Cell cell = row.getCell(i);
                values.put(cols.get(i),
                        cell == null ? "" : df.formatCellValue(cell).trim());
            }
            if (map.containsKey(key)) {
                log.warn("sheet[{}] A 列 key '{}' 重复（后行覆盖前行）",
                        sheet.getSheetName(), key);
            }
            map.put(key, new RowData(values, r));
        }
        return map;
    }

    /** 整行 diff（基线 vs 对比） */
    private void diffRows(Map<String, RowData> baseRows, Map<String, RowData> cmpRows,
                           LinkedHashSet<String> unionCols, String sheetName,
                           Sheet resultSheet, StyleCache styles, List<RevisionEntry> revisions) {

        // 新增 + 修改（遍历对比文件）
        for (Map.Entry<String, RowData> e : cmpRows.entrySet()) {
            String key = e.getKey();
            RowData cmp = e.getValue();
            RowData base = baseRows.get(key);

            if (base == null) {
                // 新增：整行 A 列起 union 范围标绿
                for (int i = 0; i < unionCols.size(); i++) {
                    paintCell(resultSheet, cmp.row, i, styles.addedBgWithBorder);
                }
                revisions.add(RevisionEntry.baselineAdded(sheetName, key, cmp.row));
            } else {
                // 修改：仅差异列标黄
                List<String> diffs = new ArrayList<>();
                int colIdx = 0;
                for (String col : unionCols) {
                    String oldVal = base.values.getOrDefault(col, "");
                    String newVal = cmp.values.getOrDefault(col, "");
                    if (!Objects.equals(normalize(oldVal), normalize(newVal))) {
                        paintCell(resultSheet, cmp.row, colIdx, styles.modifiedBgWithBorder);
                        diffs.add(col + ": " + oldVal + " → " + newVal);
                    }
                    colIdx++;
                }
                if (!diffs.isEmpty()) {
                    revisions.add(RevisionEntry.baselineModified(sheetName, key, diffs, cmp.row));
                }
            }
        }

        // 删除（遍历基线）
        for (Map.Entry<String, RowData> e : baseRows.entrySet()) {
            if (!cmpRows.containsKey(e.getKey())) {
                revisions.add(RevisionEntry.baselineDeleted(sheetName, e.getKey()));
            }
        }
    }
```

- [ ] **Step 3.6：跑测试验证它通过**

Run: `mvn -q -Dtest=PublicCoreFileBaselineCompareServiceImplTest#baseline_no_diff test`
Expected: PASS — Tests run: 1, Failures: 0, Errors: 0

- [ ] **Step 3.7：提交 Task 3**

```bash
git add src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java
git commit -m "feat: 对公核心文件基线比对 核心算法（无差异场景跑通）"
```

---

## Task 4: TDD — `row_added_marked_green`（新增行整行标绿 + 修订记录）

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java`

- [ ] **Step 4.1：在测试类追加 `row_added_marked_green`**

在 `baseline_no_diff` 方法之后插入：

```java
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
```

- [ ] **Step 4.2：跑测试**

Run: `mvn -q -Dtest=PublicCoreFileBaselineCompareServiceImplTest#row_added_marked_green test`
Expected: PASS

- [ ] **Step 4.3：提交 Task 4**

```bash
git add src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java
git commit -m "test: 新增行整行标绿场景"
```

---

## Task 5: TDD — `row_modified_only_diff_cols_yellow`（修改仅差异列标黄）

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java`

- [ ] **Step 5.1：追加测试**

```java
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
```

- [ ] **Step 5.2：跑测试**

Run: `mvn -q -Dtest=PublicCoreFileBaselineCompareServiceImplTest#row_modified_only_diff_cols_yellow test`
Expected: PASS

- [ ] **Step 5.3：提交 Task 5**

```bash
git add src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java
git commit -m "test: 修改行仅差异列标黄场景"
```

---

## Task 6: TDD — `row_deleted_in_revision_only`（删除仅登记修订，不复制旧行）

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java`

- [ ] **Step 6.1：追加测试**

```java
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
```

- [ ] **Step 6.2：跑测试**

Run: `mvn -q -Dtest=PublicCoreFileBaselineCompareServiceImplTest#row_deleted_in_revision_only test`
Expected: PASS

- [ ] **Step 6.3：提交 Task 6**

```bash
git add src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java
git commit -m "test: 删除行仅登记修订记录场景"
```

---

## Task 7: TDD — `column_union_when_widths_differ`（基线/对比列数不同取并集）

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java`

- [ ] **Step 7.1：追加测试**

```java
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
```

- [ ] **Step 7.2：跑测试**

Run: `mvn -q -Dtest=PublicCoreFileBaselineCompareServiceImplTest#column_union_when_widths_differ test`
Expected: PASS

- [ ] **Step 7.3：提交 Task 7**

```bash
git add src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java
git commit -m "test: 列数不同取并集场景"
```

---

## Task 8: TDD — `duplicate_key_warn_and_overwrite` + `other_sheets_passed_through`

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java`

- [ ] **Step 8.1：追加两个测试**

```java
    @Test
    void duplicate_key_warn_and_overwrite() throws Exception {
        // 对比文件中 A 列重复（同一路径两行）：后行覆盖前行，与新老核心一致
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "文件编号")
                    .row("/cbs/ftp/DUP", "OLD001")
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名", "文件编号")
                    .row("/cbs/ftp/DUP", "FIRST")    // 第一次
                    .row("/cbs/ftp/DUP", "SECOND")   // 后行覆盖
                .buildAsMultipartFile("compareFile");

        Map<String, Object> result = service.compareFiles(baseline, compare);
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet rev = sheet(wb, "修订记录");
            // 应只有一条 modified（基于 SECOND）
            assertEquals(1, ((Number) result.get("totalChanges")).intValue());
            String detail = rev.getRow(1).getCell(3).getStringCellValue();
            assertTrue(detail.contains("OLD001") && detail.contains("SECOND"),
                    "应基于后行(SECOND)对比，实际：" + detail);
        }
    }

    @Test
    void other_sheets_passed_through_unchanged() throws Exception {
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名")
                    .row("/cbs/ftp/A486")
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("变更历史").raw(0, 0, "v1.0 → v2.0")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名")
                    .row("/cbs/ftp/A486")
                .sheet("汇总").raw(0, 0, "1063").raw(0, 1, "总记录数")
                .buildAsMultipartFile("compareFile");

        Map<String, Object> result = service.compareFiles(baseline, compare);
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            // 三个 sheet 都应存在，"变更历史" 和 "汇总" 原样
            cellValue(sheet(wb, "变更历史"), 0, 0, "v1.0 → v2.0");
            cellValue(sheet(wb, "汇总"), 0, 0, "1063");
            cellValue(sheet(wb, "汇总"), 0, 1, "总记录数");
            // 不应有任何颜色
            cellNoFill(sheet(wb, "变更历史"), 0, 0);
            cellNoFill(sheet(wb, "汇总"), 0, 0);
        }
        assertEquals(0, ((Number) result.get("totalChanges")).intValue());
    }
```

- [ ] **Step 8.2：跑测试**

Run: `mvn -q -Dtest=PublicCoreFileBaselineCompareServiceImplTest test`
Expected: PASS — 全部 7 个测试通过

- [ ] **Step 8.3：提交 Task 8**

```bash
git add src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java
git commit -m "test: A 列重复 key 覆盖 + 其他 sheet 原样复制"
```

---

## Task 9: TDD — 异常路径（缺 EFT文件基线 sheet / 空表头）

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java`

- [ ] **Step 9.1：追加 3 个异常测试**

```java
    @Test
    void throws_when_baseline_missing_target_sheet() throws Exception {
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("汇总").raw(0, 0, "无 EFT文件基线")
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名")
                    .row("/cbs/ftp/A486")
                .buildAsMultipartFile("compareFile");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.compareFiles(baseline, compare));
        assertTrue(ex.getMessage().contains("基线文件缺少 sheet[EFT文件基线]"),
                "实际：" + ex.getMessage());
    }

    @Test
    void throws_when_compare_missing_target_sheet() throws Exception {
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("EFT文件基线")
                    .header("528核心全路径文件名")
                    .row("/cbs/ftp/A486")
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("汇总").raw(0, 0, "无 EFT文件基线")
                .buildAsMultipartFile("compareFile");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.compareFiles(baseline, compare));
        assertTrue(ex.getMessage().contains("对比文件缺少 sheet[EFT文件基线]"),
                "实际：" + ex.getMessage());
    }

    @Test
    void throws_when_header_row_empty() throws Exception {
        // EFT文件基线 sheet 存在但 row 0 完全为空（既没 header() 也没 row()）
        MultipartFile baseline = PublicCoreFileBaselineFixtureBuilder.newBuilder("base.xlsx")
                .sheet("EFT文件基线")
                    .raw(1, 0, "/cbs/ftp/A486")   // 直接写数据行，没表头
                .buildAsMultipartFile("baselineFile");

        MultipartFile compare = PublicCoreFileBaselineFixtureBuilder.newBuilder("cmp.xlsx")
                .sheet("EFT文件基线")
                    .raw(1, 0, "/cbs/ftp/A486")
                .buildAsMultipartFile("compareFile");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.compareFiles(baseline, compare));
        assertTrue(ex.getMessage().contains("EFT文件基线")
                        && (ex.getMessage().contains("表头行") || ex.getMessage().contains("A 列为空")),
                "实际：" + ex.getMessage());
    }
```

- [ ] **Step 9.2：跑测试**

Run: `mvn -q -Dtest=PublicCoreFileBaselineCompareServiceImplTest test`
Expected: PASS — 全部 10 个测试通过

- [ ] **Step 9.3：提交 Task 9**

```bash
git add src/test/java/com/sunline/dict/service/impl/PublicCoreFileBaselineCompareServiceImplTest.java
git commit -m "test: 异常路径（缺 EFT文件基线 sheet / 表头为空）"
```

---

## Task 10: 前端页面

**Files:**
- Create: `src/main/resources/static/public-core-file-baseline-compare.html`

- [ ] **Step 10.1：创建前端页面（复制并改造 new-old-core-interface-compare.html）**

创建 `src/main/resources/static/public-core-file-baseline-compare.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>对公核心文件基线比对</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: 'Microsoft YaHei', Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            padding: 20px; min-height: 100vh;
        }
        .container {
            max-width: 1400px; margin: 0 auto; background: white;
            border-radius: 10px; box-shadow: 0 10px 40px rgba(0,0,0,0.1); overflow: hidden;
        }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white; padding: 30px; text-align: center;
        }
        .header h1 { font-size: 28px; margin-bottom: 10px; }
        .header p { font-size: 14px; opacity: 0.9; }
        .content { padding: 30px; }

        .upload-section { display: grid; grid-template-columns: 1fr 1fr; gap: 30px; margin-bottom: 30px; }
        .upload-box {
            border: 2px dashed #ddd; border-radius: 10px; padding: 30px;
            text-align: center; transition: all 0.3s; background: #f8f9fa;
        }
        .upload-box:hover { border-color: #667eea; background: #f0f4ff; }
        .upload-box.has-file { border-color: #28a745; background: #f0fff4; }
        .upload-box h3 { color: #333; margin-bottom: 15px; font-size: 18px; }
        .upload-box input[type="file"] { display: none; }

        .upload-btn {
            display: inline-block; padding: 12px 30px; background: #667eea;
            color: white; border-radius: 5px; cursor: pointer; transition: all 0.3s; font-size: 14px;
        }
        .upload-btn:hover { background: #5568d3; transform: translateY(-2px); }

        .file-info { margin-top: 15px; padding: 10px; background: white; border-radius: 5px; display: none; }
        .file-info.show { display: block; }
        .file-info .file-name { color: #333; font-weight: bold; margin-bottom: 5px; }
        .file-info .file-size { color: #666; font-size: 12px; }
        .remove-file {
            margin-top: 10px; padding: 6px 15px; background: #dc3545;
            color: white; border: none; border-radius: 5px; cursor: pointer; font-size: 12px;
        }
        .remove-file:hover { background: #c82333; }

        .button-group { display: flex; justify-content: center; gap: 20px; margin-bottom: 30px; }
        .btn {
            padding: 12px 40px; border: none; border-radius: 5px;
            font-size: 16px; cursor: pointer; transition: all 0.3s; font-weight: bold;
        }
        .btn-primary { background: #28a745; color: white; }
        .btn-primary:hover { background: #218838; transform: translateY(-2px); }
        .btn-primary:disabled { background: #ccc; cursor: not-allowed; transform: none; }
        .btn-secondary { background: #6c757d; color: white; }
        .btn-secondary:hover { background: #5a6268; transform: translateY(-2px); }
        .btn-danger { background: #dc3545; color: white; }
        .btn-danger:hover { background: #c82333; transform: translateY(-2px); }

        .result-section { display: none; margin-top: 30px; }
        .result-section.show { display: block; }
        .result-header {
            background: #f8f9fa; padding: 15px; border-radius: 5px;
            margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center;
        }
        .result-header h3 { color: #333; font-size: 18px; }
        .download-btn {
            padding: 8px 20px; background: #007bff; color: white;
            border: none; border-radius: 5px; cursor: pointer; font-size: 14px;
        }
        .download-btn:hover { background: #0056b3; }
        .result-info {
            background: #fff3cd; border-left: 4px solid #ffc107;
            padding: 15px; margin-bottom: 20px; border-radius: 5px;
        }
        .result-info h4 { color: #856404; margin-bottom: 10px; }
        .result-info ul { list-style: none; padding-left: 0; }
        .result-info li { padding: 5px 0; color: #856404; }

        .legend { background: #f8f9fa; padding: 15px; border-radius: 5px; margin-bottom: 20px; }
        .legend h4 { color: #333; margin-bottom: 10px; font-size: 16px; }
        .legend-items { display: flex; gap: 20px; flex-wrap: wrap; }
        .legend-item { display: flex; align-items: center; gap: 8px; }
        .legend-color { width: 20px; height: 20px; border-radius: 3px; border: 1px solid #ddd; }
        .legend-color.green { background: #90EE90; }
        .legend-color.yellow { background: #ffff99; }
        .legend-color.gray { background: #d3d3d3; }

        .loading { display: none; text-align: center; padding: 30px; }
        .loading.show { display: block; }
        .spinner {
            border: 4px solid #f3f3f3; border-top: 4px solid #667eea;
            border-radius: 50%; width: 40px; height: 40px;
            animation: spin 1s linear infinite; margin: 0 auto 15px;
        }
        @keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }

        .error-message {
            background: #f8d7da; border-left: 4px solid #dc3545;
            color: #721c24; padding: 15px; border-radius: 5px;
            margin-top: 20px; display: none;
        }
        .error-message.show { display: block; }

        .tips {
            background: #d1ecf1; border-left: 4px solid #17a2b8;
            padding: 15px; border-radius: 5px; margin-top: 20px;
        }
        .tips h4 { color: #0c5460; margin-bottom: 10px; }
        .tips ul { color: #0c5460; padding-left: 20px; }
        .tips li { margin-bottom: 5px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 对公核心文件基线比对</h1>
            <p>比对两个版本的"对公核心文件基线" Excel，只针对 EFT文件基线 sheet，按 A 列（528核心全路径文件名）整行匹配差异</p>
        </div>

        <div class="content">
            <!-- 上传区域 -->
            <div class="upload-section">
                <div class="upload-box" id="baselineBox">
                    <h3>📄 基线文件</h3>
                    <input type="file" id="baselineFile" accept=".xlsx,.xls">
                    <label for="baselineFile" class="upload-btn">选择文件</label>
                    <div class="file-info" id="baselineInfo">
                        <div class="file-name" id="baselineName"></div>
                        <div class="file-size" id="baselineSize"></div>
                        <button class="remove-file" onclick="removeFile('baseline')">移除</button>
                    </div>
                </div>

                <div class="upload-box" id="compareBox">
                    <h3>📄 对比文件</h3>
                    <input type="file" id="compareFile" accept=".xlsx,.xls">
                    <label for="compareFile" class="upload-btn">选择文件</label>
                    <div class="file-info" id="compareInfo">
                        <div class="file-name" id="compareName"></div>
                        <div class="file-size" id="compareSize"></div>
                        <button class="remove-file" onclick="removeFile('compare')">移除</button>
                    </div>
                </div>
            </div>

            <!-- 按钮组（无排除 sheet 输入框） -->
            <div class="button-group">
                <button class="btn btn-primary" id="compareBtn" disabled>🔍 开始比较</button>
                <button class="btn btn-secondary" onclick="cancelCompare()">❌ 取消</button>
                <button class="btn btn-danger" onclick="resetForm()">🔄 重置</button>
            </div>

            <div class="loading" id="loading">
                <div class="spinner"></div>
                <p>正在比较文档，请稍候...</p>
            </div>

            <div class="error-message" id="errorMessage"></div>

            <div class="result-section" id="resultSection">
                <div class="result-header">
                    <h3>📈 比对结果</h3>
                    <button class="download-btn" onclick="downloadResult()">⬇️ 下载结果</button>
                </div>

                <div class="legend">
                    <h4>📌 颜色说明</h4>
                    <div class="legend-items">
                        <div class="legend-item">
                            <div class="legend-color green"></div>
                            <span>新增（绿色）- 在修订记录中标记</span>
                        </div>
                        <div class="legend-item">
                            <div class="legend-color yellow"></div>
                            <span>修改（黄色）- 仅差异列标黄</span>
                        </div>
                        <div class="legend-item">
                            <div class="legend-color gray"></div>
                            <span>删除（灰色+删除线）- 仅在修订记录中体现</span>
                        </div>
                    </div>
                </div>

                <div class="result-info" id="resultInfo"></div>
            </div>

            <div class="tips">
                <h4>💡 使用说明</h4>
                <ul>
                    <li><strong>比对范围：</strong>只比对名为 <code>EFT文件基线</code> 的 sheet 页；其他 sheet 原样复制不参与比对。</li>
                    <li><strong>匹配规则：</strong>从第 2 行开始，以 A 列内容（528核心全路径文件名）为唯一键，进行整行匹配。</li>
                    <li><strong>表头行：</strong>第 1 行作为表头；如果基线和对比文件的列数不一致，取两侧并集进行比较。</li>
                    <li><strong>差异标记：</strong>
                        <ul>
                            <li><span style="color:#28a745">✅ 行新增</span>：整行标绿（出现在对比文件、基线没有）</li>
                            <li><span style="color:#ffc107">✏️ 行修改</span>：仅差异列标黄，明细在修订记录展开</li>
                            <li><span style="color:#6c757d">❌ 行删除</span>：仅在修订记录中体现，结果文件不复制旧行</li>
                        </ul>
                    </li>
                    <li><strong>修订记录：</strong>4 列 — EFT文件基线 / 文件基线 / 新增（或修改、删除）/ 明细 + 双向超链接。</li>
                    <li><strong>异常提示：</strong>任一文件无 <code>EFT文件基线</code> sheet → 抛错终止比对。</li>
                </ul>
            </div>
        </div>
    </div>

    <script>
        let baselineFile = null;
        let compareFile = null;
        let resultFileName = null;

        document.getElementById('baselineFile').addEventListener('change', function(e) {
            handleFileSelect(e, 'baseline');
        });
        document.getElementById('compareFile').addEventListener('change', function(e) {
            handleFileSelect(e, 'compare');
        });

        function handleFileSelect(e, type) {
            const file = e.target.files[0];
            if (!file) return;
            if (type === 'baseline') {
                baselineFile = file;
                document.getElementById('baselineName').textContent = file.name;
                document.getElementById('baselineSize').textContent = formatFileSize(file.size);
                document.getElementById('baselineInfo').classList.add('show');
                document.getElementById('baselineBox').classList.add('has-file');
            } else {
                compareFile = file;
                document.getElementById('compareName').textContent = file.name;
                document.getElementById('compareSize').textContent = formatFileSize(file.size);
                document.getElementById('compareInfo').classList.add('show');
                document.getElementById('compareBox').classList.add('has-file');
            }
            checkFilesReady();
        }

        function removeFile(type) {
            if (type === 'baseline') {
                baselineFile = null;
                document.getElementById('baselineFile').value = '';
                document.getElementById('baselineInfo').classList.remove('show');
                document.getElementById('baselineBox').classList.remove('has-file');
            } else {
                compareFile = null;
                document.getElementById('compareFile').value = '';
                document.getElementById('compareInfo').classList.remove('show');
                document.getElementById('compareBox').classList.remove('has-file');
            }
            checkFilesReady();
        }

        function checkFilesReady() {
            document.getElementById('compareBtn').disabled = !(baselineFile && compareFile);
        }

        function formatFileSize(bytes) {
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB';
            return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
        }

        document.getElementById('compareBtn').addEventListener('click', async function() {
            if (!baselineFile || !compareFile) {
                showError('请先上传基线文件和对比文件');
                return;
            }

            const formData = new FormData();
            formData.append('baselineFile', baselineFile);
            formData.append('compareFile', compareFile);

            document.getElementById('loading').classList.add('show');
            document.getElementById('resultSection').classList.remove('show');
            document.getElementById('errorMessage').classList.remove('show');

            try {
                const response = await fetch('/api/public-core-file-baseline/compare', {
                    method: 'POST',
                    body: formData
                });
                const result = await response.json();
                if (result.code === 200) {
                    resultFileName = result.data.fileName;
                    showResult(result.data);
                } else {
                    showError(result.message || '比较失败');
                }
            } catch (error) {
                console.error('比较失败', error);
                showError('比较失败：' + error.message);
            } finally {
                document.getElementById('loading').classList.remove('show');
            }
        });

        function showResult(data) {
            document.getElementById('resultInfo').innerHTML =
                '<p>对比文件基线行数: ' + (data.totalRows ?? '-') + '</p>' +
                '<p>差异条目: ' + (data.totalChanges ?? 0) + '</p>';
            document.getElementById('resultSection').classList.add('show');
        }

        function downloadResult() {
            if (!resultFileName) {
                showError('没有可下载的结果');
                return;
            }
            window.location.href = '/api/public-core-file-baseline/download/' + encodeURIComponent(resultFileName);
        }

        function showError(message) {
            const errorDiv = document.getElementById('errorMessage');
            errorDiv.textContent = message;
            errorDiv.classList.add('show');
            setTimeout(() => { errorDiv.classList.remove('show'); }, 5000);
        }

        function cancelCompare() {
            console.log('取消比较');
        }

        function resetForm() {
            removeFile('baseline');
            removeFile('compare');
            document.getElementById('resultSection').classList.remove('show');
            document.getElementById('errorMessage').classList.remove('show');
            resultFileName = null;
        }
    </script>
</body>
</html>
```

- [ ] **Step 10.2：提交 Task 10**

```bash
git add src/main/resources/static/public-core-file-baseline-compare.html
git commit -m "feat: 对公核心文件基线比对 前端页面（无排除 sheet 输入框）"
```

---

## Task 11: 菜单注入（SQL + index.html）

**Files:**
- Create: `src/main/resources/sql/add_public_core_file_baseline_menu.sql`
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 11.1：创建菜单 SQL 脚本**

创建 `src/main/resources/sql/add_public_core_file_baseline_menu.sql`：

```sql
-- 添加"对公核心文件基线比对"菜单（紧跟在"新老核心接口文档比对"之后）
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time)
SELECT 'public-core-file-baseline-compare', '对公核心文件基线比对', id, 2, '📊', 13, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE
    menu_name  = '对公核心文件基线比对',
    icon       = '📊',
    sort_order = 13,
    update_time = NOW();
```

- [ ] **Step 11.2：在 `index.html` 第 939 行的 `hasMenuPermission(...)` 长链末尾追加权限检查**

打开 `src/main/resources/static/index.html`，搜索 `hasMenuPermission('new-old-core-interface-compare')`（约第 939 行），把它所在那行的末尾 `... hasMenuPermission('new-old-core-interface-compare') || hasMenuPermission('layer-call-rule') || ...` 修改为在 `new-old-core-interface-compare` 之后追加 `public-core-file-baseline-compare`：

把这段：
```html
hasMenuPermission('new-old-core-interface-compare') || hasMenuPermission('layer-call-rule')
```

改为：
```html
hasMenuPermission('new-old-core-interface-compare') || hasMenuPermission('public-core-file-baseline-compare') || hasMenuPermission('layer-call-rule')
```

- [ ] **Step 11.3：在 `index.html` 第 1002~1004 行的菜单项 DOM 插入新条目**

找到这段（约第 1002~1004 行）：
```html
                    <div class="menu-item" v-if="hasMenuPermission('new-old-core-interface-compare')" @click="switchView('new-old-core-interface-compare')" :class="{ active: currentView === 'new-old-core-interface-compare' }">
                        <span>🆚 新老核心接口文档比对</span>
                    </div>
```

在它**之后**（下一行起）插入：
```html
                    <div class="menu-item" v-if="hasMenuPermission('public-core-file-baseline-compare')" @click="switchView('public-core-file-baseline-compare')" :class="{ active: currentView === 'public-core-file-baseline-compare' }">
                        <span>📊 对公核心文件基线比对</span>
                    </div>
```

- [ ] **Step 11.4：在 `index.html` 第 1074 行的 iframe 路由插入新条目**

找到（约第 1074 行）：
```html
                <iframe v-show="currentView === 'new-old-core-interface-compare'" src="/new-old-core-interface-compare.html"></iframe>
```

在它**之后**插入：
```html
                <iframe v-show="currentView === 'public-core-file-baseline-compare'" src="/public-core-file-baseline-compare.html"></iframe>
```

- [ ] **Step 11.5：在 `index.html` 第 2155 行的视图标题映射表追加**

找到（约第 2155 行）：
```javascript
                        'new-old-core-interface-compare': '🆚 新老核心接口文档比对',
```

在它**之后**插入：
```javascript
                        'public-core-file-baseline-compare': '📊 对公核心文件基线比对',
```

- [ ] **Step 11.6：编译验证**

Run: `mvn -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 11.7：提交 Task 11**

```bash
git add src/main/resources/sql/add_public_core_file_baseline_menu.sql \
        src/main/resources/static/index.html
git commit -m "feat: 菜单注入 对公核心文件基线比对（SQL + index.html 4 处）"
```

---

## Task 12: 全量测试 + 启动验证

**Files:**（无修改，仅验证）

- [ ] **Step 12.1：跑全量测试**

Run: `mvn -q test`
Expected: BUILD SUCCESS — 所有 *Test 类通过（包括新增 10 个测试 + 既有所有测试）

> 如果 spring boot 启动慢，可只跑相关测试：`mvn -q -Dtest='*PublicCoreFileBaseline*,*NewOldCoreInterface*,*ExcelCompare*' test`

- [ ] **Step 12.2：执行菜单注入 SQL（如果本地有 MySQL 实例）**

Run: 在本地 MySQL 客户端连接到对应数据库后执行：
```bash
mysql -u <user> -p<password> <db_name> < src/main/resources/sql/add_public_core_file_baseline_menu.sql
```

Expected: `Query OK, 1 row affected`（首次执行）或 `Query OK, 2 rows affected`（重复执行触发 ON DUPLICATE KEY UPDATE）

> 若没有本地 MySQL，跳过此步并记录"需在部署环境手动执行 SQL"

- [ ] **Step 12.3：启动应用**

Run: `./start.sh`（或 `mvn -q spring-boot:run`）
Expected: 应用启动成功，日志无 ERROR

- [ ] **Step 12.4：浏览器访问 `http://localhost:<port>/index.html`**

预期：
- 左侧菜单"新老核心接口文档比对"下方出现"📊 对公核心文件基线比对"
- 点击进入子页，看到上传两个文件框（基线文件 / 对比文件）、3 个按钮、颜色说明、使用说明，**无排除 sheet 输入框**

- [ ] **Step 12.5：手工验证一个差异场景**

用 Excel 自己构造两个文件（或用之前测试 fixture 导出的 .xlsx）：
- 基线：EFT文件基线 sheet 有 2 行（A486、4013）
- 对比：EFT文件基线 sheet 有 3 行（A486 不变、4013 文件分类改了、NEW 新增）

上传 → 开始比较 → 下载结果 → 用 Excel 打开

Expected：
- `EFT文件基线` sheet 中：4013 的"文件分类"列黄底、NEW 整行绿底
- "修订记录" sheet 2 条：4013 修改 + NEW 新增，D 列可点击跳转到主 sheet

- [ ] **Step 12.6：停止应用**

Run: `./stop.sh`（或 Ctrl+C）

- [ ] **Step 12.7：不需要 commit（仅验证）**

---

## Task 13: 同步更新 Obsidian 状态

**Files:**
- Modify: `/Users/java/obsidian/01 Engineering/sunline-benchmark/对公核心文件基线比对-设计.md`

- [ ] **Step 13.1：把 spec 文档的 `status` 从 `designed` 改为 `implemented`，并更新"最后更新"时间**

Edit `/Users/java/obsidian/01 Engineering/sunline-benchmark/对公核心文件基线比对-设计.md`：

- frontmatter 中 `status: designed` → `status: implemented`
- 文末 `*最后更新：2026-05-28*` 改为 `*最后更新：<今天日期>*\n*实现完成：<今天日期>*`

> 此文件不入 git，按用户宪法只在 Obsidian 中维护。

---

## Self-Review

**Spec coverage check:**
- §1.3 验收 1（菜单注入）→ Task 11
- §1.3 验收 2（上传 + 下载）→ Task 1（API） + Task 10（前端） + Task 12（端到端）
- §1.3 验收 3（差异颜色）→ Task 4 / Task 5 / Task 6
- §1.3 验收 4（修订记录 + 超链接）→ Task 3（写入） + Task 4 / Task 6（链接断言）
- §1.3 验收 5（缺 sheet 抛错）→ Task 9
- §1.3 验收 6（表头空抛错）→ Task 9
- §1.3 验收 7（A 列重复）→ Task 8
- §4.5 值比对规则 → Task 3 主流程已用 `normalize` + `DataFormatter`
- §5 输出文件结构 → Task 3 主流程实现
- §6 前端 UI → Task 10
- §7 后端 API → Task 1
- §8 菜单注入 → Task 11
- §9 边界 case → 散落 Task 3 / 8 / 9
- §10 测试方案 → Task 2 + Task 3-9

**Placeholder scan:** 无 TBD / TODO / "implement later"；每段代码 / 命令 / 期望输出都是完整的。

**Type consistency:**
- Service 接口方法签名一致：`compareFiles(MultipartFile, MultipartFile)` （Service interface） / `comparePublicCoreFileBaseline(MultipartFile, MultipartFile)` （ExcelCompareService interface）
- 返回 Map key 一致：`fileName` / `totalRows` / `totalChanges`
- 表单字段名一致：`baselineFile` / `compareFile`
- API 路径一致：`/api/public-core-file-baseline/{compare,download/{fileName}}`
- sheet 字面量一致：`EFT文件基线`
- RevisionEntry 工厂方法名一致：`baselineAdded` / `baselineModified` / `baselineDeleted`
- 修订记录 level 字面量一致：`文件基线`

无类型 / 命名漂移。

---

*Plan finalized: 2026-05-28*
