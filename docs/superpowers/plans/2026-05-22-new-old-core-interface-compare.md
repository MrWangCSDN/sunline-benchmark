# 新老核心接口文档比对 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在"迁移中间表比对"菜单正下方新增"新老核心接口文档比对"功能 — 接收两个核心接口 Excel（旧版/新版），只比对 J 列及右半边内容，按"列中文名"/"J 列文本"为唯一键找出元信息和字段差异，输出带颜色标记+修订记录 sheet 的结果文件。

**Architecture:** 方案 A — 在 `CompareMode` 枚举上加 `NEW_OLD_CORE_INTERFACE`，复用 `ExcelCompareServiceImpl` 的样式缓存/超链接/修订记录 sheet 工具能力。新增专用 Service+Controller 薄壳委托给 `ExcelCompareServiceImpl.compareNewOldCoreInterfaces(...)`。

**Tech Stack:** Java 17 + Spring Boot 3.1.5 + Apache POI（Excel 读写）+ JUnit 5（程序化 fixture，无需 .xlsx 入库）。前端：HTML + Vue 3 CDN，复制 `migration-table-compare.html` 改造。

**Spec：** `/Users/java/obsidian/01 Engineering/sunline-benchmark/新老核心接口文档比对-设计.md`

**关键约定（spec 已确认）：**
- 错误处理：沿用项目现有风格，抛 `RuntimeException("xxx")`（项目无 `BizException` 类，按 YAGNI 不新建）
- excludeSheets 参数传递：在 `ExcelCompareService` 接口加专用方法 `compareNewOldCoreInterfaces(old, new, excludeSheets)`，**不破坏现有 5 种模式的接口签名**
- 默认排除 sheet：`说明,修订记录,索引`
- 颜色：绿 `#90EE90` (新增) / 黄 `#FFFF99` (修改) / 灰 `#D3D3D3` (删除，含删除线) — 沿用 `ExcelCompareServiceImpl.StyleCache`
- 输出文件名：`new-old-core-compare-yyyyMMddHHmmssSSS.xlsx`，存目录 `excel_compare_results`

**测试策略：** 程序化生成 fixture（不入库 .xlsx）。新建 `ExcelFixtureBuilder` 测试工具 + `ExcelAssert` 测试工具，所有 case 内联在测试方法里用 POI 构造输入/期望。

---

## File Structure

**Create:**

| 文件 | 责任 |
|---|---|
| `src/main/java/com/sunline/dict/controller/NewOldCoreInterfaceCompareController.java` | REST 入口，参数校验+委托 Service |
| `src/main/java/com/sunline/dict/service/NewOldCoreInterfaceCompareService.java` | Service 接口（业务包装） |
| `src/main/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImpl.java` | 薄壳，委托给 `ExcelCompareServiceImpl.compareNewOldCoreInterfaces` |
| `src/main/resources/static/new-old-core-interface-compare.html` | 前端页面 |
| `src/main/resources/sql/add_new_old_core_interface_menu.sql` | 菜单注入（追加在迁移中间表之后，sort_order=12） |
| `src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java` | 测试主类 |
| `src/test/java/com/sunline/dict/testutil/ExcelFixtureBuilder.java` | 程序化构造测试 Excel |
| `src/test/java/com/sunline/dict/testutil/ExcelAssert.java` | Excel 单元格级断言工具 |

**Modify:**

| 文件 | 改动 |
|---|---|
| `src/main/java/com/sunline/dict/common/CompareMode.java` | 加 `NEW_OLD_CORE_INTERFACE` 枚举值 |
| `src/main/java/com/sunline/dict/service/ExcelCompareService.java` | 加方法 `compareNewOldCoreInterfaces(old, new, excludeSheets)` |
| `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java` | 实现 `compareNewOldCoreInterfaces` + 工具方法（findHeaderRow / scanHeaderCols / readMeta / readFields / diffMeta / diffFields） |
| `src/main/resources/static/index.html` | 注入菜单项 + iframe + 视图标题映射（共 4 处） |
| `src/main/java/com/sunline/dict/config/WebMvcConfig.java` | 拦截器排除清单加 `/new-old-core-interface-compare.html`（保持与其他 .html 一致） |

---

## Task 1: 添加 `CompareMode.NEW_OLD_CORE_INTERFACE` 枚举值

**Files:**
- Modify: `src/main/java/com/sunline/dict/common/CompareMode.java`

- [ ] **Step 1: 在枚举末尾追加 NEW_OLD_CORE_INTERFACE**

修改 `CompareMode.java`，在 `MIGRATION_TABLE` 之后追加：

```java
    /**
     * 新老核心接口文档比对模式
     * - 左边：旧版本核心接口 Excel，右边：新版本核心接口 Excel
     * - 只比对每个 sheet 的 J 列及右半边内容（左半边 A-I 不参与）
     * - 区域分界：以 J 列出现"列中文名"为界，之前是接口元信息区，之后是字段明细表
     * - 元信息区唯一键：J 列文本；字段明细区唯一键：列中文名
     * - 列范围：表头行从 J 起向右扫到第一个空单元格，两侧取并集
     * - 排除 sheet：默认 说明,修订记录,索引（前端可调整）
     * - 输出：以新版本为底本复制 + 右半边标颜色 + 追加"修订记录"sheet
     */
    NEW_OLD_CORE_INTERFACE
```

注意：`MIGRATION_TABLE` 原本是枚举的最后一个值（没逗号），要把它的后面加上逗号 `,` 再追加新值。

- [ ] **Step 2: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/sunline/dict/common/CompareMode.java
git commit -m "feat: CompareMode 新增 NEW_OLD_CORE_INTERFACE 枚举值"
```

---

## Task 2: 创建 Service 接口 + Controller + 薄壳实现（占位）

**Files:**
- Create: `src/main/java/com/sunline/dict/service/NewOldCoreInterfaceCompareService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImpl.java`
- Create: `src/main/java/com/sunline/dict/controller/NewOldCoreInterfaceCompareController.java`
- Modify: `src/main/java/com/sunline/dict/service/ExcelCompareService.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java`
- Modify: `src/main/java/com/sunline/dict/config/WebMvcConfig.java`

- [ ] **Step 1: ExcelCompareService 接口加新方法签名**

修改 `src/main/java/com/sunline/dict/service/ExcelCompareService.java`，在 `getResultFile` 之前追加：

```java
    /**
     * 新老核心接口文档比对模式
     * 只比对每个 sheet 的 J 列及右半边内容
     *
     * @param oldFile        旧版本核心接口 Excel
     * @param newFile        新版本核心接口 Excel（作为输出底本）
     * @param excludeSheets  排除的 sheet 名，逗号分隔；为空 / null 视为"不排除任何 sheet"
     * @return 比较结果信息，包含 fileName, totalSheets, totalChanges 等字段
     */
    Map<String, Object> compareNewOldCoreInterfaces(
            MultipartFile oldFile, MultipartFile newFile, String excludeSheets) throws Exception;
```

- [ ] **Step 2: ExcelCompareServiceImpl 加占位实现**

修改 `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java`，在类末尾的最后一个 `}` 之前追加：

```java
    /**
     * 新老核心接口文档比对入口（详细算法见 spec §4）
     * 当前为占位实现，后续 Task 通过 TDD 逐步填充
     */
    @Override
    public Map<String, Object> compareNewOldCoreInterfaces(
            MultipartFile oldFile, MultipartFile newFile, String excludeSheets) throws Exception {
        throw new UnsupportedOperationException("compareNewOldCoreInterfaces 待实现 (Task 5+)");
    }
```

注意：`@Override` 注解需要的 import 是 `java.lang.Override`（无需 import）。`Map`、`MultipartFile` 在文件顶部已有 import。

- [ ] **Step 3: 创建 NewOldCoreInterfaceCompareService 接口**

创建文件 `src/main/java/com/sunline/dict/service/NewOldCoreInterfaceCompareService.java`：

```java
package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 新老核心接口文档比对服务接口
 */
public interface NewOldCoreInterfaceCompareService {

    /**
     * 比较新老两版核心接口 Excel 文档
     *
     * @param oldFile       旧版本（基准）
     * @param newFile       新版本（作为输出底本）
     * @param excludeSheets 排除的 sheet 名，逗号分隔；为空 / null 视为"不排除任何 sheet"
     * @return 比较结果信息（fileName、totalSheets、totalChanges 等）
     */
    Map<String, Object> compareFiles(MultipartFile oldFile, MultipartFile newFile, String excludeSheets)
            throws Exception;

    /**
     * 获取结果文件
     */
    File getResultFile(String fileName);
}
```

- [ ] **Step 4: 创建 NewOldCoreInterfaceCompareServiceImpl（薄壳，委托 ExcelCompareServiceImpl）**

创建 `src/main/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImpl.java`：

```java
package com.sunline.dict.service.impl;

import com.sunline.dict.service.NewOldCoreInterfaceCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 新老核心接口文档比对服务实现
 * 委托给 ExcelCompareServiceImpl 的 compareNewOldCoreInterfaces 方法
 */
@Service
public class NewOldCoreInterfaceCompareServiceImpl implements NewOldCoreInterfaceCompareService {

    private static final Logger log = LoggerFactory.getLogger(NewOldCoreInterfaceCompareServiceImpl.class);

    @Autowired
    private ExcelCompareServiceImpl excelCompareService;

    @Override
    public Map<String, Object> compareFiles(MultipartFile oldFile, MultipartFile newFile, String excludeSheets)
            throws Exception {
        log.info("开始新老核心接口文档比对，excludeSheets={}", excludeSheets);
        return excelCompareService.compareNewOldCoreInterfaces(oldFile, newFile, excludeSheets);
    }

    @Override
    public File getResultFile(String fileName) {
        return excelCompareService.getResultFile(fileName);
    }
}
```

- [ ] **Step 5: 创建 NewOldCoreInterfaceCompareController**

创建 `src/main/java/com/sunline/dict/controller/NewOldCoreInterfaceCompareController.java`：

```java
package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.service.NewOldCoreInterfaceCompareService;
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
 * 新老核心接口文档比对控制器
 */
@RestController
@RequestMapping("/api/new-old-core")
public class NewOldCoreInterfaceCompareController {

    private static final Logger log = LoggerFactory.getLogger(NewOldCoreInterfaceCompareController.class);

    @Autowired
    private NewOldCoreInterfaceCompareService service;

    /**
     * 比对新老核心接口 Excel 文档
     */
    @PostMapping("/compare")
    public Result<Map<String, Object>> compare(
            @RequestParam("oldFile") MultipartFile oldFile,
            @RequestParam("newFile") MultipartFile newFile,
            @RequestParam(value = "excludeSheets", required = false, defaultValue = "") String excludeSheets) {

        try {
            log.info("收到新老核心接口比对请求 旧={} 新={} 排除={}",
                    oldFile.getOriginalFilename(), newFile.getOriginalFilename(), excludeSheets);

            if (oldFile.isEmpty() || newFile.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String oldName = oldFile.getOriginalFilename();
            String newName = newFile.getOriginalFilename();
            if (oldName == null || newName == null
                    || (!oldName.endsWith(".xlsx") && !oldName.endsWith(".xls"))
                    || (!newName.endsWith(".xlsx") && !newName.endsWith(".xls"))) {
                return Result.error("文件格式不正确，只支持.xlsx和.xls格式");
            }

            Map<String, Object> result = service.compareFiles(oldFile, newFile, excludeSheets);
            log.info("新老核心接口比对完成，结果文件: {}", result.get("fileName"));
            return Result.success(result);

        } catch (Exception e) {
            log.error("新老核心接口比对失败", e);
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

- [ ] **Step 6: WebMvcConfig 加 .html 排除路径（如果需要）**

查 `src/main/java/com/sunline/dict/config/WebMvcConfig.java`，其拦截器 `excludePathPatterns` 已经有 `"/*.html"` 通配，所以 `/new-old-core-interface-compare.html` **自动覆盖**，**无需改动**。

实际执行时验证：

Run: `grep '"\*\.html"' src/main/java/com/sunline/dict/config/WebMvcConfig.java`
Expected: 至少一行匹配 `"/*.html"`

如果**没有**通配，需要追加 `"/new-old-core-interface-compare.html"` 到 excludePathPatterns。

- [ ] **Step 7: 编译验证**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Spring Boot 启动冒烟（验证 bean 装配 + 调用占位方法报 UnsupportedOperationException）**

Run: `mvn spring-boot:run` 启动一次（后台），用 curl POST 上传两个 .xlsx 试探。或者用现有 `DictManagerApplicationTests.contextLoads()` 验证上下文加载不挂。

简化版本：
Run: `mvn test -Dtest=DictManagerApplicationTests -q`
Expected: BUILD SUCCESS（contextLoads 通过，证明所有新 bean 装配 OK）

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/sunline/dict/service/ExcelCompareService.java \
        src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java \
        src/main/java/com/sunline/dict/service/NewOldCoreInterfaceCompareService.java \
        src/main/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImpl.java \
        src/main/java/com/sunline/dict/controller/NewOldCoreInterfaceCompareController.java
git commit -m "feat: 新老核心接口比对 后端骨架（Service/Controller + 占位实现）"
```

---

## Task 3: 测试工具类 ExcelFixtureBuilder + ExcelAssert

> 程序化生成 Excel，不入库二进制 .xlsx。所有测试 fixture 在测试代码里构造。

**Files:**
- Create: `src/test/java/com/sunline/dict/testutil/ExcelFixtureBuilder.java`
- Create: `src/test/java/com/sunline/dict/testutil/ExcelAssert.java`

- [ ] **Step 1: 写 ExcelFixtureBuilder（用 POI 程序化构造一个核心接口 Excel）**

创建 `src/test/java/com/sunline/dict/testutil/ExcelFixtureBuilder.java`：

```java
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
        currentSheet.metas.add(new MetaSpec(label, value));
        return this;
    }

    /** 字段表头行（出现"列中文名"标识分界点）—— 第一个元素必须是"列中文名" */
    public ExcelFixtureBuilder headerCols(String... cols) {
        if (cols.length == 0 || !"列中文名".equals(cols[0])) {
            throw new IllegalArgumentException("headerCols 第一个必须是'列中文名'");
        }
        currentSheet.headerCols = Arrays.asList(cols);
        return this;
    }

    /** 字段明细行（按 headerCols 顺序传值） */
    public ExcelFixtureBuilder field(String... values) {
        if (currentSheet.headerCols == null) {
            throw new IllegalStateException("先调 headerCols 再调 field");
        }
        if (values.length != currentSheet.headerCols.size()) {
            throw new IllegalArgumentException("field 列数与 headerCols 不一致");
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
```

- [ ] **Step 2: 写 ExcelAssert（断言 Excel 结构、单元格值、颜色）**

创建 `src/test/java/com/sunline/dict/testutil/ExcelAssert.java`：

```java
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
        return new XSSFWorkbook(new FileInputStream(file));
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
        String actual = c == null ? "" : new DataFormatter().formatCellValue(c).trim();
        assertEquals(expected, actual,
                "sheet[" + sheet.getSheetName() + "] (" + row + "," + col + ") 单元格值不符");
    }

    /** 断言单元格背景色（IndexedColors 的 short index 比对） */
    public static void cellBgColor(Sheet sheet, int row, int col, short expectedIndex, String label) {
        Cell c = sheet.getRow(row).getCell(col);
        assertNotNull(c, label + " 单元格不存在");
        CellStyle style = c.getCellStyle();
        assertEquals(expectedIndex, style.getFillForegroundColor(),
                label + " 单元格(" + row + "," + col + ") 背景色不符");
    }

    /** 断言单元格字体含删除线 */
    public static void cellHasStrikeout(Workbook wb, Sheet sheet, int row, int col, String label) {
        Cell c = sheet.getRow(row).getCell(col);
        assertNotNull(c, label + " 单元格不存在");
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
```

- [ ] **Step 3: 编译验证**

Run: `mvn test-compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/sunline/dict/testutil/
git commit -m "test: 新增 ExcelFixtureBuilder/ExcelAssert 测试工具类"
```

---

## Task 4: TDD — baseline（两文件完全相同，0 差异）

**Files:**
- Create: `src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java`

> 此 task 把"骨架走通"：输入两个完全相同的 Excel，输出 = 新版本副本 + 一个空的"修订记录"sheet。后续 Task 在此基础上扩展。

- [ ] **Step 1: 写测试 baseline_no_diff**

创建 `src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java`：

```java
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
```

- [ ] **Step 2: 跑测试验证 fail**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest#baseline_no_diff -q`
Expected: FAIL with `UnsupportedOperationException: compareNewOldCoreInterfaces 待实现 (Task 5+)`

- [ ] **Step 3: 实现 compareNewOldCoreInterfaces 主流程（最小版本，跑通 baseline）**

修改 `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java`，替换 Task 2 加的占位实现：

```java
    /**
     * 新老核心接口文档比对入口
     */
    @Override
    public Map<String, Object> compareNewOldCoreInterfaces(
            MultipartFile oldFile, MultipartFile newFile, String excludeSheets) throws Exception {

        log.info("开始新老核心接口比对");

        // 解析 excludeSheets
        Set<String> excludeSet = parseExcludeSheets(excludeSheets);
        log.info("排除 sheet 集合: {}", excludeSet);

        // 创建输出目录
        File resultDir = new File(RESULT_DIR);
        if (!resultDir.exists()) resultDir.mkdirs();

        // 调整 Zip bomb 阈值（与现有模式一致）
        ZipSecureFile.setMinInflateRatio(0.001);

        try (Workbook oldWb = WorkbookFactory.create(oldFile.getInputStream());
             Workbook newWb = WorkbookFactory.create(newFile.getInputStream())) {

            if (newWb.getNumberOfSheets() == 0 && oldWb.getNumberOfSheets() == 0) {
                throw new RuntimeException("Excel 至少需要包含一个 sheet");
            }

            // 以新版本为底本：复制到目标 Workbook
            Workbook resultWb = new XSSFWorkbook();
            StyleCache styles = new StyleCache(resultWb);

            // 累积修订条目
            List<RevisionEntry> revisions = new ArrayList<>();

            // 收集新版本所有 sheet 名（保持顺序），处理或原样复制
            for (int i = 0; i < newWb.getNumberOfSheets(); i++) {
                String name = newWb.getSheetName(i);
                Sheet newSheet = newWb.getSheetAt(i);
                Sheet oldSheet = oldWb.getSheet(name);  // 同名旧 sheet

                // 复制到结果工作簿（原样）
                Sheet resultSheet = resultWb.createSheet(name);
                copySheetContent(newSheet, resultSheet);

                if (excludeSet.contains(name)) {
                    log.info("sheet[{}] 被排除，不参与比对", name);
                    continue;
                }

                // 比对单 sheet
                // 本 Task 4 只走通 baseline (compareOneSheet 当前是空体，无真正 diff)
                // Task 5/6 会通过 Edit 替换 compareOneSheet 方法体加入 diffMeta/diffFields
                if (oldSheet != null) {
                    compareOneSheet(oldSheet, newSheet, resultSheet, name, styles, revisions);
                }
            }

            // 处理旧版本独有 sheet（删除接口）
            for (int i = 0; i < oldWb.getNumberOfSheets(); i++) {
                String name = oldWb.getSheetName(i);
                if (excludeSet.contains(name)) continue;
                if (newWb.getSheet(name) == null) {
                    revisions.add(RevisionEntry.sheetDeleted(name, readInterfaceName(oldWb.getSheetAt(i))));
                }
            }

            // 追加修订记录 sheet
            writeRevisionSheet(resultWb, revisions, excludeSet, styles);

            // 写盘
            String fileName = "new-old-core-compare-"
                    + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()) + ".xlsx";
            File out = new File(resultDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                resultWb.write(fos);
            }
            resultWb.close();

            Map<String, Object> ret = new HashMap<>();
            ret.put("fileName", fileName);
            ret.put("totalSheets", newWb.getNumberOfSheets());
            ret.put("totalChanges", revisions.size());
            return ret;
        }
    }

    /** 解析 excludeSheets 字符串 */
    private Set<String> parseExcludeSheets(String s) {
        if (s == null || s.trim().isEmpty()) return new HashSet<>();
        Set<String> set = new HashSet<>();
        for (String part : s.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) set.add(trimmed);
        }
        return set;
    }

    /** 复制源 sheet 全部内容到目标 sheet（值+样式） */
    private void copySheetContent(Sheet src, Sheet dst) {
        for (int r = 0; r <= src.getLastRowNum(); r++) {
            Row srcRow = src.getRow(r);
            if (srcRow == null) continue;
            Row dstRow = dst.createRow(r);
            for (int c = 0; c < srcRow.getLastCellNum(); c++) {
                Cell srcCell = srcRow.getCell(c);
                if (srcCell == null) continue;
                Cell dstCell = dstRow.createCell(c);
                dstCell.setCellValue(new DataFormatter().formatCellValue(srcCell));
            }
        }
    }

    /** 单 sheet 比对 —— Task 4 只跑空骨架，后续 task 填充实际 diff */
    private void compareOneSheet(Sheet oldSheet, Sheet newSheet, Sheet resultSheet,
                                  String sheetName, StyleCache styles, List<RevisionEntry> revisions) {
        // Task 5+ 实现：findHeaderRow / diffMeta / diffFields
        // 当前 Task 4 留空，保证 baseline 跑通
    }

    /** 修订记录 sheet 写入 */
    private void writeRevisionSheet(Workbook wb, List<RevisionEntry> revisions,
                                     Set<String> excludeSet, StyleCache styles) {
        // 避让同名：如果"修订记录"已经存在（被复制过来了），改名"修订记录_diff"
        String sheetName = wb.getSheet("修订记录") != null ? "修订记录_diff" : "修订记录";
        Sheet rev = wb.createSheet(sheetName);

        // 表头
        Row header = rev.createRow(0);
        header.createCell(0).setCellValue("交易码");
        header.createCell(1).setCellValue("修订级别");
        header.createCell(2).setCellValue("修订方式");
        header.createCell(3).setCellValue("修订明细");

        // 数据行
        for (int i = 0; i < revisions.size(); i++) {
            RevisionEntry e = revisions.get(i);
            Row row = rev.createRow(i + 1);
            row.createCell(0).setCellValue(e.txnCode);
            row.createCell(1).setCellValue(e.level);
            row.createCell(2).setCellValue(e.way);
            row.createCell(3).setCellValue(e.detail);

            // C 列按修订方式染色
            Cell wayCell = row.getCell(2);
            switch (e.way) {
                case "新增": wayCell.setCellStyle(styles.addedBgWithBorder); break;
                case "修改": wayCell.setCellStyle(styles.modifiedBgWithBorder); break;
                case "删除": wayCell.setCellStyle(styles.deletedBgWithBorder); break;
                default: // 不处理
            }
        }
    }

    /** 从一个 sheet 的元信息区读出"接口名称"（用于"接口删除"的明细描述） */
    private String readInterfaceName(Sheet sheet) {
        if (sheet == null) return "";
        for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 20); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell jCell = row.getCell(9);
            if (jCell == null) continue;
            String j = new DataFormatter().formatCellValue(jCell).trim();
            if ("接口名称".equals(j)) {
                Cell kCell = row.getCell(10);
                return kCell == null ? "" : new DataFormatter().formatCellValue(kCell).trim();
            }
        }
        return "";
    }

    /** 修订条目数据结构 */
    private static class RevisionEntry {
        String txnCode;  // sheet 名
        String level;    // 接口 / 字段
        String way;      // 新增 / 修改 / 删除
        String detail;   // 明细描述
        // 超链接目标（行/列，0-based，sheet 名）—— Task 10 实现
        String linkSheetName;
        Integer linkRow;
        Integer linkCol;

        static RevisionEntry sheetDeleted(String sheetName, String interfaceName) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "接口";
            e.way = "删除";
            e.detail = "删除接口：" + (interfaceName.isEmpty() ? sheetName : interfaceName);
            return e;
        }
    }
```

注意：要 import 的：`org.apache.poi.ss.usermodel.WorkbookFactory`、`org.apache.poi.ss.usermodel.Row`、`org.apache.poi.ss.usermodel.Cell`、`org.apache.poi.ss.usermodel.Sheet`、`org.apache.poi.ss.usermodel.DataFormatter`。文件顶部已经 import `org.apache.poi.ss.usermodel.*` 通配，所以**不需要新加 import**。

- [ ] **Step 4: 跑测试验证 pass**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest#baseline_no_diff -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java
git commit -m "feat: 新老核心接口比对 baseline 流程跑通（无差异场景）"
```

---

## Task 5: TDD — 元信息区差异（修改 / 新增 / 删除）

**Files:**
- Modify: `src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java`
- Modify: `src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java`

- [ ] **Step 1: 加测试 metainfo_modified**

在 test 类追加：

```java
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
```

- [ ] **Step 2: 跑测试验证 fail**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest#metainfo_modified -q`
Expected: FAIL

- [ ] **Step 3: 实现 findHeaderRow + diffMeta**

在 `ExcelCompareServiceImpl.java` 内 `compareOneSheet` 方法所在位置，扩展逻辑：

```java
    /** 单 sheet 比对（元信息 + 字段，本 task 仅实现元信息） */
    private void compareOneSheet(Sheet oldSheet, Sheet newSheet, Sheet resultSheet,
                                  String sheetName, StyleCache styles, List<RevisionEntry> revisions) {

        // ⓪ 空 sheet 短路
        boolean oldEmpty = isSheetEmpty(oldSheet);
        boolean newEmpty = isSheetEmpty(newSheet);
        if (oldEmpty && newEmpty) return;
        if (oldEmpty) {
            // sheet 新增（旧无新有）—— Task 8 完整实现
            revisions.add(RevisionEntry.sheetAdded(sheetName, readInterfaceName(newSheet)));
            return;
        }
        if (newEmpty) {
            revisions.add(RevisionEntry.sheetDeleted(sheetName, readInterfaceName(oldSheet)));
            return;
        }

        // ① 找分界点
        int oldHeaderRow = findHeaderRow(oldSheet);
        int newHeaderRow = findHeaderRow(newSheet);

        // ② 元信息区：J=label, K=value
        Map<String, MetaCell> oldMeta = readMeta(oldSheet, 0, oldHeaderRow);
        Map<String, MetaCell> newMeta = readMeta(newSheet, 0, newHeaderRow);
        diffMeta(oldMeta, newMeta, sheetName, resultSheet, styles, revisions);

        // ③ 字段明细区 —— Task 6 实现
    }

    /** sheet 是否完全空（无非空单元格） */
    private boolean isSheetEmpty(Sheet sheet) {
        if (sheet == null || sheet.getLastRowNum() < 0) return true;
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell != null && !new DataFormatter().formatCellValue(cell).trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** J 列出现"列中文名"的行号；找不到抛错（非空 sheet 强制） */
    private int findHeaderRow(Sheet sheet) {
        for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell j = row.getCell(9);
            if (j == null) continue;
            String v = new DataFormatter().formatCellValue(j).trim();
            if ("列中文名".equals(v)) return r;
        }
        throw new RuntimeException("sheet[" + sheet.getSheetName() + "] 在 J 列未找到'列中文名'表头行");
    }

    /** 读元信息区：fromRow（含）到 toRow（不含），key=J列, value={K列值, 行号} */
    private Map<String, MetaCell> readMeta(Sheet sheet, int fromRow, int toRow) {
        Map<String, MetaCell> map = new LinkedHashMap<>();
        for (int r = fromRow; r < toRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell j = row.getCell(9);
            if (j == null) continue;
            String label = new DataFormatter().formatCellValue(j).trim();
            if (label.isEmpty()) continue;
            // 跳过类似"原输出文件"/"新输出文件"这种 merge 标题行（只有 J，没有 K）—— 但保险起见也读
            Cell k = row.getCell(10);
            String value = k == null ? "" : new DataFormatter().formatCellValue(k).trim();
            if (map.containsKey(label)) {
                log.warn("sheet[{}] 元信息区 J 列重复标签: {}（后值覆盖前值）", sheet.getSheetName(), label);
            }
            map.put(label, new MetaCell(value, r));
        }
        return map;
    }

    /** 元信息区比对：标颜色 + 累积修订条目 */
    private void diffMeta(Map<String, MetaCell> oldMeta, Map<String, MetaCell> newMeta,
                           String sheetName, Sheet resultSheet, StyleCache styles, List<RevisionEntry> revisions) {

        // 修改 + 新增（遍历新版本）
        for (Map.Entry<String, MetaCell> e : newMeta.entrySet()) {
            String label = e.getKey();
            MetaCell newCell = e.getValue();
            MetaCell oldCell = oldMeta.get(label);

            if (oldCell == null) {
                // 新增：J + K 标绿
                paintCell(resultSheet, newCell.row, 9, styles.addedBgWithBorder);
                paintCell(resultSheet, newCell.row, 10, styles.addedBgWithBorder);
                revisions.add(RevisionEntry.metaAdded(sheetName, label, newCell.value, newCell.row));
            } else if (!Objects.equals(normalize(oldCell.value), normalize(newCell.value))) {
                // 修改：K 标黄
                paintCell(resultSheet, newCell.row, 10, styles.modifiedBgWithBorder);
                revisions.add(RevisionEntry.metaModified(sheetName, label, oldCell.value, newCell.value, newCell.row));
            }
            // 完全一致：不标
        }

        // 删除（遍历旧版本找新版本没有的）
        for (Map.Entry<String, MetaCell> e : oldMeta.entrySet()) {
            if (!newMeta.containsKey(e.getKey())) {
                revisions.add(RevisionEntry.metaDeleted(sheetName, e.getKey(), e.getValue().value));
            }
        }
    }

    /** 给目标 sheet 的指定单元格涂色（若行/单元格不存在则创建） */
    private void paintCell(Sheet sheet, int row, int col, CellStyle style) {
        Row r = sheet.getRow(row);
        if (r == null) r = sheet.createRow(row);
        Cell c = r.getCell(col);
        if (c == null) c = r.createCell(col);
        c.setCellStyle(style);
    }

    /** 单元格值归一化：null/""/" " 视为相等 */
    private String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    /** 元信息单元格 */
    private static class MetaCell {
        final String value;
        final int row;
        MetaCell(String v, int r) { value = v; row = r; }
    }
```

在 `RevisionEntry` 类里补全工厂方法：

```java
        static RevisionEntry sheetAdded(String sheetName, String interfaceName) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "接口";
            e.way = "新增";
            e.detail = "新增接口：" + (interfaceName.isEmpty() ? sheetName : interfaceName);
            return e;
        }

        static RevisionEntry metaModified(String sheetName, String label, String oldVal, String newVal, int row) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "接口";
            e.way = "修改";
            e.detail = label + ": " + oldVal + " → " + newVal;
            e.linkSheetName = sheetName;
            e.linkRow = row;
            e.linkCol = 10;  // K 列
            return e;
        }

        static RevisionEntry metaAdded(String sheetName, String label, String newVal, int row) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "接口";
            e.way = "新增";
            e.detail = "新增项 " + label + ": " + newVal;
            e.linkSheetName = sheetName;
            e.linkRow = row;
            e.linkCol = 10;
            return e;
        }

        static RevisionEntry metaDeleted(String sheetName, String label, String oldVal) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "接口";
            e.way = "删除";
            e.detail = "删除项 " + label + ": " + oldVal;
            return e;
        }
```

- [ ] **Step 4: 跑测试验证 pass**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest -q`
Expected: 两个测试都 PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java
git commit -m "feat: 元信息区差异比对（新增/修改/删除）"
```

---

## Task 6: TDD — 字段明细区差异（新增 / 修改 / 删除）

**Files:**
- Modify: test + ExcelCompareServiceImpl

- [ ] **Step 1: 加测试 field_added_modified_deleted**

```java
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
```

- [ ] **Step 2: 跑测试验证 fail**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest#field_added_modified_deleted -q`
Expected: FAIL

- [ ] **Step 3: 实现 scanHeaderCols + readFields + diffFields**

在 `ExcelCompareServiceImpl.java` 内 `compareOneSheet` 的末尾（"字段明细区 —— Task 6 实现" 注释处）追加调用 + 实现：

```java
        // ③ 字段明细区
        List<String> oldCols = scanHeaderCols(oldSheet, oldHeaderRow);
        List<String> newCols = scanHeaderCols(newSheet, newHeaderRow);
        LinkedHashSet<String> unionCols = new LinkedHashSet<>(newCols);
        unionCols.addAll(oldCols);

        Map<String, FieldRow> oldFields = readFields(oldSheet, oldHeaderRow + 1, oldCols);
        Map<String, FieldRow> newFields = readFields(newSheet, newHeaderRow + 1, newCols);
        diffFields(oldFields, newFields, unionCols, sheetName, resultSheet, styles, revisions);
    }
```

并在文件中追加方法：

```java
    /** 表头行从 J 列起向右扫，直到第一个空单元格 */
    private List<String> scanHeaderCols(Sheet sheet, int headerRow) {
        Row row = sheet.getRow(headerRow);
        List<String> cols = new ArrayList<>();
        int lastCellNum = row.getLastCellNum();
        for (int c = 9; c < lastCellNum; c++) {
            Cell cell = row.getCell(c);
            String v = cell == null ? "" : new DataFormatter().formatCellValue(cell).trim();
            if (v.isEmpty()) break;
            cols.add(v);
        }
        if (cols.size() <= 1) {
            throw new RuntimeException("sheet[" + sheet.getSheetName()
                    + "] 表头行 J 列右侧无有效列（至少需'列顺序'等属性列）");
        }
        return cols;
    }

    /** 字段明细区 → Map<列中文名, FieldRow> */
    private Map<String, FieldRow> readFields(Sheet sheet, int fromRow, List<String> cols) {
        Map<String, FieldRow> map = new LinkedHashMap<>();
        for (int r = fromRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell jCell = row.getCell(9);
            String name = jCell == null ? "" : new DataFormatter().formatCellValue(jCell).trim();
            if (name.isEmpty()) continue;  // 跳过空行（一般是字段表底部）

            Map<String, String> values = new LinkedHashMap<>();
            for (int i = 0; i < cols.size(); i++) {
                Cell cell = row.getCell(9 + i);
                values.put(cols.get(i),
                        cell == null ? "" : new DataFormatter().formatCellValue(cell).trim());
            }
            if (map.containsKey(name)) {
                log.warn("sheet[{}] 字段表'{}'重复（后行覆盖前行）", sheet.getSheetName(), name);
            }
            map.put(name, new FieldRow(values, r));
        }
        return map;
    }

    /** 字段差异比对 */
    private void diffFields(Map<String, FieldRow> oldFields, Map<String, FieldRow> newFields,
                             LinkedHashSet<String> unionCols, String sheetName,
                             Sheet resultSheet, StyleCache styles, List<RevisionEntry> revisions) {

        // 新增 + 修改（遍历新版本）
        for (Map.Entry<String, FieldRow> e : newFields.entrySet()) {
            String name = e.getKey();
            FieldRow newRow = e.getValue();
            FieldRow oldRow = oldFields.get(name);

            if (oldRow == null) {
                // 新增：整行 J 起标绿
                for (int i = 0; i < unionCols.size(); i++) {
                    paintCell(resultSheet, newRow.row, 9 + i, styles.addedBgWithBorder);
                }
                String summary = formatFieldSummary(newRow.values);
                revisions.add(RevisionEntry.fieldAdded(sheetName, name, summary, newRow.row));
            } else {
                // 修改：仅不同的列标黄
                List<String> diffs = new ArrayList<>();
                int colIdx = 0;
                for (String col : unionCols) {
                    String oldVal = oldRow.values.getOrDefault(col, "");
                    String newVal = newRow.values.getOrDefault(col, "");
                    if (!Objects.equals(normalize(oldVal), normalize(newVal))) {
                        paintCell(resultSheet, newRow.row, 9 + colIdx, styles.modifiedBgWithBorder);
                        diffs.add(col + ": " + oldVal + " → " + newVal);
                    }
                    colIdx++;
                }
                if (!diffs.isEmpty()) {
                    revisions.add(RevisionEntry.fieldModified(sheetName, name, diffs, newRow.row));
                }
            }
        }

        // 删除（遍历旧版本）
        for (Map.Entry<String, FieldRow> e : oldFields.entrySet()) {
            if (!newFields.containsKey(e.getKey())) {
                String summary = formatFieldSummary(e.getValue().values);
                revisions.add(RevisionEntry.fieldDeleted(sheetName, e.getKey(), summary));
            }
        }
    }

    /** 格式化字段的属性摘要，用于修订明细 */
    private String formatFieldSummary(Map<String, String> values) {
        StringBuilder sb = new StringBuilder("(");
        String type = values.getOrDefault("列数据类型", "");
        String len = values.getOrDefault("列最大长度", "");
        sb.append(type);
        if (!len.isEmpty()) sb.append(", 长度").append(len);
        sb.append(")");
        return sb.toString();
    }

    /** 字段数据结构 */
    private static class FieldRow {
        final Map<String, String> values;
        final int row;
        FieldRow(Map<String, String> v, int r) { values = v; row = r; }
    }
```

在 `RevisionEntry` 加 3 个工厂方法：

```java
        static RevisionEntry fieldAdded(String sheetName, String fieldName, String summary, int row) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "字段";
            e.way = "新增";
            e.detail = "新增字段：" + fieldName + summary;
            e.linkSheetName = sheetName;
            e.linkRow = row;
            e.linkCol = 9;
            return e;
        }

        static RevisionEntry fieldModified(String sheetName, String fieldName, List<String> diffs, int row) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "字段";
            e.way = "修改";
            e.detail = "字段[" + fieldName + "] " + String.join("; ", diffs);
            e.linkSheetName = sheetName;
            e.linkRow = row;
            e.linkCol = 9;
            return e;
        }

        static RevisionEntry fieldDeleted(String sheetName, String fieldName, String summary) {
            RevisionEntry e = new RevisionEntry();
            e.txnCode = sheetName;
            e.level = "字段";
            e.way = "删除";
            e.detail = "删除字段：" + fieldName + summary;
            return e;
        }
```

- [ ] **Step 4: 跑测试验证全 pass**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest -q`
Expected: PASS（baseline + metainfo_modified + field_added_modified_deleted）

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java
git commit -m "feat: 字段明细区差异比对（新增/修改/删除）"
```

---

## Task 7: TDD — Sheet 维度新增 / 删除

**Files:**
- Modify: test + ExcelCompareServiceImpl

- [ ] **Step 1: 加测试 sheet_added_and_deleted**

```java
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
```

- [ ] **Step 2: 跑测试验证 fail**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest#sheet_added_and_deleted -q`
Expected: FAIL（NEW_ONLY 没标绿）

- [ ] **Step 3: 实现 sheet 新增标绿**

在 `compareNewOldCoreInterfaces` 的主循环里，遇到旧版无对应 sheet 时，调用专用方法整体标绿：

把 `compareOneSheet` 后面这段调整：

```java
                if (oldSheet != null) {
                    compareOneSheet(oldSheet, newSheet, resultSheet, name, styles, revisions);
                } else {
                    // 新版本独有的 sheet：整 sheet 右半边标绿
                    paintWholeSheetGreen(newSheet, resultSheet, name, styles, revisions);
                }
```

并追加方法：

```java
    /** 新版本独有 sheet：右半边整体标绿 + 追加"接口新增"修订条目 */
    private void paintWholeSheetGreen(Sheet newSheet, Sheet resultSheet, String sheetName,
                                       StyleCache styles, List<RevisionEntry> revisions) {
        if (isSheetEmpty(newSheet)) {
            revisions.add(RevisionEntry.sheetAdded(sheetName, ""));
            return;
        }

        int headerRow = findHeaderRow(newSheet);  // 找不到会抛错（沿用一致策略）
        List<String> cols = scanHeaderCols(newSheet, headerRow);

        // 元信息区：J + K 所有非空单元格标绿
        for (int r = 0; r < headerRow; r++) {
            Row row = newSheet.getRow(r);
            if (row == null) continue;
            for (int c = 9; c <= 10; c++) {
                Cell cell = row.getCell(c);
                if (cell != null && !new DataFormatter().formatCellValue(cell).trim().isEmpty()) {
                    paintCell(resultSheet, r, c, styles.addedBgWithBorder);
                }
            }
        }

        // 字段表头行 + 明细行：J 起 cols.size() 个单元格标绿
        for (int r = headerRow; r <= newSheet.getLastRowNum(); r++) {
            Row row = newSheet.getRow(r);
            if (row == null) continue;
            for (int i = 0; i < cols.size(); i++) {
                Cell cell = row.getCell(9 + i);
                if (cell != null && !new DataFormatter().formatCellValue(cell).trim().isEmpty()) {
                    paintCell(resultSheet, r, 9 + i, styles.addedBgWithBorder);
                }
            }
        }

        revisions.add(RevisionEntry.sheetAdded(sheetName, readInterfaceName(newSheet)));
    }
```

- [ ] **Step 4: 跑测试验证 pass**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest -q`
Expected: 所有测试 PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java
git commit -m "feat: sheet 维度新增/删除（整体标绿 + 修订记录）"
```

---

## Task 8: TDD — 排除 sheet（默认 / 自定义 / 空字符串）

**Files:**
- Modify: test 类

- [ ] **Step 1: 加三个测试**

```java
    @Test
    void exclude_sheets_default() throws Exception {
        // 旧/新都有 "说明" sheet（内容不同），但被默认排除 → 不应有差异
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("说明").raw(0, 0, "旧说明")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .headerCols("列中文名", "列顺序")
                    .field("产品编号", "1")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("说明").raw(0, 0, "新说明")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .headerCols("列中文名", "列顺序")
                    .field("产品编号", "1")
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "说明,修订记录,索引");
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet shuoming = sheet(wb, "说明");
            cellValue(shuoming, 0, 0, "新说明");
            // 不应标颜色
            cellNoFill(shuoming, 0, 0);
        }
        assertEquals(0, ((Number) result.get("totalChanges")).intValue());
    }

    @Test
    void exclude_sheets_custom() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("封面").raw(0, 0, "旧封面")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .headerCols("列中文名", "列顺序")
                    .field("产品编号", "1")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("封面").raw(0, 0, "新封面")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .headerCols("列中文名", "列顺序")
                    .field("产品编号", "1")
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "封面");
        assertEquals(0, ((Number) result.get("totalChanges")).intValue());
        resultFile = service.getResultFile((String) result.get("fileName"));
    }

    @Test
    void exclude_empty_means_no_exclude() throws Exception {
        // 排除清空 → "说明" sheet 内容不同也会被比对（默认值不会自动回填）
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("说明").raw(1, 9, "旧").raw(2, 9, "列中文名").raw(2, 10, "列顺序")  // 模拟一个"列中文名"表头
                                .raw(3, 9, "fieldA").raw(3, 10, "1")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("说明").raw(1, 9, "新").raw(2, 9, "列中文名").raw(2, 10, "列顺序")
                                .raw(3, 9, "fieldA").raw(3, 10, "1")
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "");
        // "说明" 不被排除，元信息差异（"旧"→"新"）会被识别
        // 即应至少有 1 条修订
        assertTrue(((Number) result.get("totalChanges")).intValue() >= 1,
                "排除清空时'说明'应被比对");
        resultFile = service.getResultFile((String) result.get("fileName"));
    }
```

- [ ] **Step 2: 跑测试，验证主要场景已经过（默认排除逻辑可能已经在 Task 4-5 实现）**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest -q`
Expected: 全部 PASS（`parseExcludeSheets` + 主循环里 `if (excludeSet.contains(name))` 已经实现，应直接通过）

如果 FAIL，按提示调整逻辑（最大可能是空字符串处理）。

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java
git commit -m "test: 验证排除 sheet 逻辑（默认/自定义/空字符串）"
```

---

## Task 9: TDD — 异常路径（空 Excel / 找不到表头 / 表头列为空）

**Files:**
- Modify: test 类

- [ ] **Step 1: 加 3 个异常测试**

```java
    @Test
    void throws_when_excel_empty() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx").buildAsMultipartFile("oldFile");
        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx").buildAsMultipartFile("newFile");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.compareFiles(oldFile, newFile, ""));
        assertTrue(ex.getMessage().contains("至少需要包含一个 sheet"),
                "应抛'至少需要包含一个 sheet'，实际：" + ex.getMessage());
    }

    @Test
    void throws_when_header_row_missing() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")  // 只有元信息，没有"列中文名"表头
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")
                .buildAsMultipartFile("newFile");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.compareFiles(oldFile, newFile, ""));
        assertTrue(ex.getMessage().contains("未找到'列中文名'"),
                "应抛'未找到列中文名'，实际：" + ex.getMessage());
    }

    @Test
    void throws_when_header_cols_blank() throws Exception {
        // 表头只有"列中文名"自己，右侧 K 列为空 → 应抛错
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .raw(2, 9, "列中文名")  // 只有 J 列，K 起为空
                    .raw(3, 9, "产品编号")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .raw(2, 9, "列中文名")
                    .raw(3, 9, "产品编号")
                .buildAsMultipartFile("newFile");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.compareFiles(oldFile, newFile, ""));
        assertTrue(ex.getMessage().contains("表头行 J 列右侧无有效列"),
                "应抛'表头列为空'，实际：" + ex.getMessage());
    }
```

- [ ] **Step 2: 跑测试**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest -q`
Expected: 全部 PASS（已有实现都已经抛对应错误）

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java
git commit -m "test: 验证异常路径（空 Excel / 缺表头 / 表头列空）"
```

---

## Task 10: TDD — 列范围并集（旧 vs 新 列数不一致）

**Files:**
- Modify: test 类

- [ ] **Step 1: 加测试 column_union_when_widths_differ**

```java
    @Test
    void column_union_when_widths_differ() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .headerCols("列中文名", "列顺序", "列数据类型")  // 3 列
                    .field("产品编号", "1", "string")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("985501")
                    .meta("交易码", "UD49")
                    .headerCols("列中文名", "列顺序", "列数据类型", "列最大长度")  // 4 列（多了 长度）
                    .field("产品编号", "1", "string", "10")
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "");
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet rev = sheet(wb, "修订记录");
            // "列最大长度: → 10" 这种修改条目应存在（旧无新有 = "" → "10"）
            int found = 0;
            for (int r = 1; r <= rev.getLastRowNum(); r++) {
                String detail = rev.getRow(r).getCell(3).getStringCellValue();
                if (detail.contains("列最大长度") && detail.contains("10")) found++;
            }
            assertEquals(1, found, "新增列'列最大长度'应被识别为差异");
        }
    }
```

- [ ] **Step 2: 跑测试**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest#column_union_when_widths_differ -q`
Expected: PASS（unionCols 逻辑已实现）

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java
git commit -m "test: 验证列范围并集（新增列被识别为差异）"
```

---

## Task 11: 修订记录排序 + 双向超链接

**Files:**
- Modify: test + ExcelCompareServiceImpl

- [ ] **Step 1: 加测试 revision_log_ordered_and_linked**

```java
    @Test
    void revision_log_ordered_and_linked() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("S1")
                    .meta("交易码", "A")
                    .headerCols("列中文名", "列顺序")
                    .field("f1", "1")
                .sheet("S2")
                    .meta("交易码", "B")
                    .headerCols("列中文名", "列顺序")
                    .field("f1", "1")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("S1")
                    .meta("交易码", "A2")  // 接口修改
                    .headerCols("列中文名", "列顺序")
                    .field("f1", "1")
                    .field("f_new", "2")  // 字段新增
                .sheet("S2")
                    .meta("交易码", "B")
                    .headerCols("列中文名", "列顺序")
                    .field("f1", "1")
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "");
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            Sheet rev = sheet(wb, "修订记录");
            // 排序：S1 在前（按 sheet 顺序），同 sheet 内"接口"在"字段"前
            // 行 1：S1 / 接口 / 修改 / 交易码: A → A2
            // 行 2：S1 / 字段 / 新增 / 新增字段：f_new...
            revisionRow(rev, 1, "S1", "接口", "修改", "交易码: A → A2");
            cellValue(rev, 2, 0, "S1");
            cellValue(rev, 2, 1, "字段");
            cellValue(rev, 2, 2, "新增");
            assertTrue(rev.getRow(2).getCell(3).getStringCellValue().contains("新增字段：f_new"));

            // 修订记录的 D 列单元格应有超链接（hyperlink target 不为 null）
            assertNotNull(rev.getRow(1).getCell(3).getHyperlink(), "修订记录 D 列应有超链接（行 1）");
        }
    }
```

- [ ] **Step 2: 跑测试验证 fail**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest#revision_log_ordered_and_linked -q`
Expected: FAIL（排序或超链接缺失）

- [ ] **Step 3: 实现排序 + 超链接**

修改 `writeRevisionSheet` 方法的开头加排序，并补加 hyperlink 设置：

```java
    private void writeRevisionSheet(Workbook wb, List<RevisionEntry> revisions,
                                     Set<String> excludeSet, StyleCache styles) {
        // 按 spec §5.4 排序
        Map<String, Integer> sheetOrder = new HashMap<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) {
            sheetOrder.put(wb.getSheetName(i), i);
        }
        revisions.sort((a, b) -> {
            int s = Integer.compare(
                    sheetOrder.getOrDefault(a.txnCode, Integer.MAX_VALUE),
                    sheetOrder.getOrDefault(b.txnCode, Integer.MAX_VALUE));
            if (s != 0) return s;
            // 级别：接口 < 字段
            int lv = Integer.compare(levelRank(a.level), levelRank(b.level));
            if (lv != 0) return lv;
            // 方式：新增 < 修改 < 删除
            int w = Integer.compare(wayRank(a.way), wayRank(b.way));
            if (w != 0) return w;
            // 同方式：按 linkRow 物理顺序
            int rA = a.linkRow == null ? Integer.MAX_VALUE : a.linkRow;
            int rB = b.linkRow == null ? Integer.MAX_VALUE : b.linkRow;
            return Integer.compare(rA, rB);
        });

        String sheetName = wb.getSheet("修订记录") != null ? "修订记录_diff" : "修订记录";
        Sheet rev = wb.createSheet(sheetName);

        // 表头
        Row header = rev.createRow(0);
        header.createCell(0).setCellValue("交易码");
        header.createCell(1).setCellValue("修订级别");
        header.createCell(2).setCellValue("修订方式");
        header.createCell(3).setCellValue("修订明细");

        // 数据行 + 超链接
        CreationHelper helper = wb.getCreationHelper();
        for (int i = 0; i < revisions.size(); i++) {
            RevisionEntry e = revisions.get(i);
            int rowIdx = i + 1;
            Row row = rev.createRow(rowIdx);
            row.createCell(0).setCellValue(e.txnCode);
            row.createCell(1).setCellValue(e.level);
            row.createCell(2).setCellValue(e.way);
            Cell detailCell = row.createCell(3);
            detailCell.setCellValue(e.detail);

            // 染色（C 列）
            switch (e.way) {
                case "新增": row.getCell(2).setCellStyle(styles.addedBgWithBorder); break;
                case "修改": row.getCell(2).setCellStyle(styles.modifiedBgWithBorder); break;
                case "删除": row.getCell(2).setCellStyle(styles.deletedBgWithBorder); break;
                default:
            }

            // 正向超链接：D 列 → 目标 sheet 的差异单元格
            if (e.linkSheetName != null && e.linkRow != null && e.linkCol != null) {
                Hyperlink link = helper.createHyperlink(HyperlinkType.DOCUMENT);
                String address = "'" + e.linkSheetName + "'!"
                        + CellReference.convertNumToColString(e.linkCol) + (e.linkRow + 1);
                link.setAddress(address);
                detailCell.setHyperlink(link);

                // 反向超链接：目标单元格 → 修订记录 sheet
                Sheet targetSheet = wb.getSheet(e.linkSheetName);
                if (targetSheet != null) {
                    Row targetRow = targetSheet.getRow(e.linkRow);
                    if (targetRow != null) {
                        Cell targetCell = targetRow.getCell(e.linkCol);
                        if (targetCell == null) targetCell = targetRow.createCell(e.linkCol);
                        Hyperlink back = helper.createHyperlink(HyperlinkType.DOCUMENT);
                        back.setAddress("'" + sheetName + "'!A" + (rowIdx + 1));
                        targetCell.setHyperlink(back);
                    }
                }
            }
        }
    }

    private int levelRank(String level) {
        if ("接口".equals(level)) return 0;
        if ("字段".equals(level)) return 1;
        return 99;
    }

    private int wayRank(String way) {
        switch (way) {
            case "新增": return 0;
            case "修改": return 1;
            case "删除": return 2;
            default: return 99;
        }
    }
```

需要 import：`org.apache.poi.ss.util.CellReference`、`org.apache.poi.ss.usermodel.Hyperlink`、`org.apache.poi.ss.usermodel.CreationHelper`。
顶部已有 `org.apache.poi.ss.usermodel.*` 通配，所以只需补：

```java
import org.apache.poi.ss.util.CellReference;
```

- [ ] **Step 4: 跑测试**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest -q`
Expected: 全部 PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sunline/dict/service/impl/ExcelCompareServiceImpl.java \
        src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java
git commit -m "feat: 修订记录排序 + 双向超链接"
```

---

## Task 12: 前端 HTML 页面

**Files:**
- Create: `src/main/resources/static/new-old-core-interface-compare.html`

- [ ] **Step 1: 复制 migration-table-compare.html 为基础**

Run: `cp src/main/resources/static/migration-table-compare.html src/main/resources/static/new-old-core-interface-compare.html`

- [ ] **Step 2: 修改文案 + 加排除 sheet 输入行**

打开 `src/main/resources/static/new-old-core-interface-compare.html`，做以下替换：

(1) 标题 `<title>` 改为 `<title>新老核心接口文档比对</title>`

(2) header 区：
```html
<h1>🆚 新老核心接口文档比对</h1>
<p>比对两个版本的核心接口 Excel 文档（J 列起的右半边），标记字段的新增、删除和修改</p>
```

(3) 上传框文案：
- `XML表定义文档` → `旧版本核心接口文档`
- `迁移中间表文档` → `新版本核心接口文档`
- input `id="xmlFile"` → `id="oldFile"`
- input `id="migrationFile"` → `id="newFile"`
- 对应 div id 也跟着改：`xmlBox` → `oldBox`，`migrationBox` → `newBox`，`xmlInfo` → `oldInfo`，`xmlName` → `oldName`，`xmlSize` → `oldSize`，等等

(4) 在 `<div class="upload-section">...</div>` 后、`<div class="button-group">` 前插入排除 sheet 输入行：

```html
<!-- 排除 sheet 输入行 -->
<div class="exclude-section" style="margin-bottom: 30px;">
    <h3 style="color:#333; margin-bottom:10px;">⚙️ 排除比对的 Sheet 名称（多个用逗号分隔）</h3>
    <input type="text" id="excludeSheets" value="说明,修订记录,索引"
           placeholder="说明,修订记录,索引"
           style="width:100%; padding:10px; border:1px solid #ddd; border-radius:5px; font-size:14px;">
    <p style="color:#666; font-size:12px; margin-top:8px;">
        默认排除"说明、修订记录、索引"。可按需追加（如：示例,封面）。清空表示不排除任何 sheet。
    </p>
</div>
```

(5) JS 区改造：

把：
```javascript
let xmlFile = null;
let migrationFile = null;
```
改为：
```javascript
let oldFile = null;
let newFile = null;
```

把上传事件绑定改为：
```javascript
document.getElementById('oldFile').addEventListener('change', function(e) {
    handleFileSelect(e, 'old');
});
document.getElementById('newFile').addEventListener('change', function(e) {
    handleFileSelect(e, 'new');
});
```

`handleFileSelect` 和 `removeFile` 里把 `'base' / 'compare'` 改为 `'old' / 'new'`，并对应改各处 dom 引用。

把 `compareTables()` 函数改成调用新 API：

```javascript
async function compareFiles() {
    if (!oldFile || !newFile) {
        showError('请同时选择新旧两个文件');
        return;
    }
    const excludeSheets = document.getElementById('excludeSheets').value;

    document.getElementById('loading').classList.add('show');
    document.getElementById('errorMessage').classList.remove('show');
    document.getElementById('resultSection').classList.remove('show');
    document.getElementById('compareBtn').disabled = true;

    const formData = new FormData();
    formData.append('oldFile', oldFile);
    formData.append('newFile', newFile);
    formData.append('excludeSheets', excludeSheets);

    try {
        const response = await fetch('/api/new-old-core/compare', {
            method: 'POST',
            body: formData
        });
        const result = await response.json();
        if (result.code === 200 || result.success) {
            const data = result.data || result;
            resultFileName = data.fileName;
            document.getElementById('resultInfo').innerHTML =
                `<p>处理 sheet 数: ${data.totalSheets ?? '-'}</p>` +
                `<p>差异条目: ${data.totalChanges ?? 0}</p>`;
            document.getElementById('resultSection').classList.add('show');
        } else {
            showError(result.message || '比对失败');
        }
    } catch (e) {
        showError('请求失败: ' + e.message);
    } finally {
        document.getElementById('loading').classList.remove('show');
        document.getElementById('compareBtn').disabled = false;
    }
}

function downloadResult() {
    if (!resultFileName) return;
    window.location.href = '/api/new-old-core/download/' + encodeURIComponent(resultFileName);
}
```

把开始比较按钮的 onclick 改为 `compareFiles()`。

(6) 改"使用说明"tips 区域为：

```html
<div class="tips">
    <h4>💡 使用说明</h4>
    <ul>
        <li><strong>文件结构</strong>：从 J 列开始的右半边参与比对（A-I 左半边不参与）</li>
        <li><strong>Sheet 比对</strong>：sheet 名相同 → 字段级比对；新增/删除 sheet → 修订记录里登记</li>
        <li><strong>区域分界</strong>：J 列出现"列中文名"为界 — 之前是接口元信息，之后是字段表</li>
        <li><strong>唯一键</strong>：元信息以 J 列文本 / 字段明细以"列中文名"</li>
        <li><strong>排除 Sheet</strong>：默认 说明/修订记录/索引，严格匹配，可在输入框中调整</li>
        <li><strong>颜色标记</strong>：🟢 新增 / 🟡 修改 / ⬜ 删除（带删除线）</li>
    </ul>
</div>
```

- [ ] **Step 3: 启动后端，手动 smoke test**

Run: `mvn spring-boot:run` (后台启动)
然后浏览器访问 `http://localhost:8080/new-old-core-interface-compare.html`，确认页面渲染正常、文件选择/重置按钮工作。

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/new-old-core-interface-compare.html
git commit -m "feat: 新老核心接口比对 前端页面"
```

---

## Task 13: 菜单注入（SQL + index.html）

**Files:**
- Create: `src/main/resources/sql/add_new_old_core_interface_menu.sql`
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: 写菜单 SQL**

创建 `src/main/resources/sql/add_new_old_core_interface_menu.sql`：

```sql
-- 添加"新老核心接口文档比对"菜单（紧跟在"迁移中间表比对"之后）
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time)
SELECT 'new-old-core-interface-compare', '新老核心接口文档比对', id, 2, '🆚', 12, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE
    menu_name  = '新老核心接口文档比对',
    icon       = '🆚',
    sort_order = 12,
    update_time = NOW();
```

- [ ] **Step 2: index.html 注入菜单（4 处）**

修改 `src/main/resources/static/index.html`：

(a) 顶部 `hasMenuPermission(...)` 长串里加 `|| hasMenuPermission('new-old-core-interface-compare')`（在 migration-table-compare 之后）

(b) 菜单项 div 里，在 `migration-table-compare` menu-item 之后追加：

```html
<div class="menu-item" v-if="hasMenuPermission('new-old-core-interface-compare')" @click="switchView('new-old-core-interface-compare')" :class="{ active: currentView === 'new-old-core-interface-compare' }">
    <span>🆚 新老核心接口文档比对</span>
</div>
```

(c) iframe 列表里，在 `migration-table-compare` iframe 之后追加：

```html
<iframe v-show="currentView === 'new-old-core-interface-compare'" src="/new-old-core-interface-compare.html"></iframe>
```

(d) 视图标题映射里，在 `'migration-table-compare': '🔄 迁移中间表比对',` 之后追加：

```javascript
'new-old-core-interface-compare': '🆚 新老核心接口文档比对',
```

- [ ] **Step 3: 启动后端手动验证**

Run: `mvn spring-boot:run`

浏览器：
1. 数据库执行一次 `add_new_old_core_interface_menu.sql`
2. 给当前用户分配新菜单权限（用现有用户权限管理页面，或者直接 SQL 操作 sys_user_menu_permission 表）
3. 刷新主页 `http://localhost:8080/`，验证左侧"工具箱"菜单里出现"🆚 新老核心接口文档比对"，点击能切到新页面

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/sql/add_new_old_core_interface_menu.sql \
        src/main/resources/static/index.html
git commit -m "feat: 菜单注入（SQL + index.html 4 处）"
```

---

## Task 14: 综合 E2E 验证 + 文档同步

**Files:**
- Modify: 设计文档 status

- [ ] **Step 1: 综合测试 multi_changes**

在 test 类追加一个综合场景：3 个 sheet（1 个完全相同、1 个有元信息+字段变更、1 个新增 sheet），验证修订记录条目数符合预期。

```java
    @Test
    void multi_changes_e2e() throws Exception {
        MultipartFile oldFile = ExcelFixtureBuilder.newBuilder("old.xlsx")
                .sheet("说明").raw(0, 0, "说明书")
                .sheet("SHEET_SAME")
                    .meta("交易码", "S0")
                    .headerCols("列中文名", "列顺序")
                    .field("f1", "1")
                .sheet("SHEET_CHANGED")
                    .meta("交易码", "OLD_CODE")
                    .headerCols("列中文名", "列顺序", "列数据类型")
                    .field("f1", "1", "string")
                    .field("f_to_delete", "2", "string")
                .buildAsMultipartFile("oldFile");

        MultipartFile newFile = ExcelFixtureBuilder.newBuilder("new.xlsx")
                .sheet("说明").raw(0, 0, "说明书(更新)")
                .sheet("SHEET_SAME")
                    .meta("交易码", "S0")
                    .headerCols("列中文名", "列顺序")
                    .field("f1", "1")
                .sheet("SHEET_CHANGED")
                    .meta("交易码", "NEW_CODE")  // 修改
                    .headerCols("列中文名", "列顺序", "列数据类型")
                    .field("f1", "1", "decimal")  // 修改
                    .field("f_new", "3", "string")  // 新增
                .sheet("SHEET_BRAND_NEW")  // 新增
                    .meta("交易码", "NEW")
                    .headerCols("列中文名", "列顺序")
                    .field("f1", "1")
                .buildAsMultipartFile("newFile");

        Map<String, Object> result = service.compareFiles(oldFile, newFile, "说明,修订记录,索引");
        resultFile = service.getResultFile((String) result.get("fileName"));

        // 期望差异条目数：
        // SHEET_CHANGED: 交易码修改(1) + f1修改(1) + f_to_delete删除(1) + f_new新增(1) = 4
        // SHEET_BRAND_NEW: 接口新增(1) = 1
        // 合计 5
        assertEquals(5, ((Number) result.get("totalChanges")).intValue());

        try (Workbook wb = ExcelAssert.open(resultFile)) {
            // "说明"被排除，没标颜色
            cellNoFill(sheet(wb, "说明"), 0, 0);
            // SHEET_BRAND_NEW 整体标绿
            cellBgColor(sheet(wb, "SHEET_BRAND_NEW"), 1, 10, IndexedColors.LIGHT_GREEN.getIndex(), "SHEET_BRAND_NEW 交易码 K");
        }
    }
```

- [ ] **Step 2: 跑全部测试 + 启动手动跑一遍**

Run: `mvn test -Dtest=NewOldCoreInterfaceCompareServiceImplTest -q`
Expected: 全部 PASS

启动手动 E2E：
Run: `mvn spring-boot:run`
浏览器进入新页面，用真实 .xlsx 文件传上去比对，下载结果文件用 Excel 打开人眼验证：
- 颜色对（绿/黄/灰）
- 修订记录 sheet 4 列内容对
- 双向超链接可点

- [ ] **Step 3: 更新 Obsidian 设计文档 status**

修改 `/Users/java/obsidian/01 Engineering/sunline-benchmark/新老核心接口文档比对-设计.md` 的 YAML frontmatter：

`status: draft` → `status: implemented`

并在最后一行 `*最后更新：2026-05-22*` 旁加一行 `*实现完成：YYYY-MM-DD*`（当前日期）。

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/sunline/dict/service/impl/NewOldCoreInterfaceCompareServiceImplTest.java
git commit -m "test: 综合 E2E 场景验证"
```

---

## 自检 (Self-Review 结果)

**1. Spec 覆盖检查：**

| Spec 章节 | 对应 Task |
|---|---|
| §3 架构与数据流 | Task 1–7 |
| §4.1 单 sheet 处理 | Task 5–7 |
| §4.2 元信息区比对 | Task 5 |
| §4.3 字段明细区比对 | Task 6 |
| §5.1 整体结构（输出文件） | Task 4–7 |
| §5.2/5.3 修订记录 4 列 | Task 4 (writeRevisionSheet) + Task 6 |
| §5.4 排序规则 | Task 11 |
| §5.5 颜色填充 | Task 4 (StyleCache 复用) |
| §5.6 双向超链接 | Task 11 |
| §5.7 sheet 维度变化 | Task 7 |
| §6 前端 UI | Task 12 |
| §6.3 菜单注入 | Task 13 |
| §6.4 排除 sheet 解析 | Task 4 (parseExcludeSheets) + Task 8 |
| §7 边界 case | Task 9（异常）+ Task 8（排除）+ Task 4（空 sheet 短路） |
| §8 测试方案 | Task 3 (脚手架) + Task 4-11 (各 case) + Task 14 (综合) |

无遗漏。

**2. 无 placeholder**：检查通过，所有 step 都有具体代码或具体命令。

**3. 类型一致性**：
- `RevisionEntry` 各工厂方法（sheetAdded / sheetDeleted / metaModified / metaAdded / metaDeleted / fieldAdded / fieldModified / fieldDeleted）在 Task 4-7 间逐步追加，方法签名前后一致
- `MetaCell`、`FieldRow`、`StyleCache` 均在 Task 4 引入，后续 Task 沿用
- `findHeaderRow / scanHeaderCols / readMeta / readFields / diffMeta / diffFields / paintCell / paintWholeSheetGreen` 命名前后一致

---

*最后更新：2026-05-22*
