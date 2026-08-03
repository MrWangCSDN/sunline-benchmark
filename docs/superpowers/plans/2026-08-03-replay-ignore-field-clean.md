# Replay Ignore Field Clean Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a menu tool that compares old-core/ESF and ESF/new-core mapping workbooks, reports transaction-level ESF differences, and generates replay ignore-field rows for offline output fields.

**Architecture:** Keep the feature outside the existing oversized `ExcelCompareServiceImpl`. A dedicated parser converts the two fixed workbook layouts into immutable records, a pure processor performs pairing/diff/config generation, a writer creates the two-sheet result workbook, and a thin Spring service/controller handles files and HTTP. The static page follows the existing dual-upload iframe pattern and uses `AbortController` for page-side cancellation.

**Tech Stack:** Java 17, Spring Boot 3.1.x, Apache POI 5.2.3, JUnit 5, Mockito, HTML/CSS/ES6, existing Vue/axios application shell.

## Global Constraints

- Process only sheet names matching `^[A-Za-z0-9]{4}$`.
- Pair sheet names case-insensitively with `Locale.ROOT`; preserve original names in output and errors.
- Compare ESF field names case-sensitively after trimming surrounding whitespace.
- Compare input and output sections separately; any section difference prevents all config generation for that transaction.
- Only new-workbook output rows whose D cell is exactly `下线` generate config; input rows never generate config.
- Each offline output field produces exactly three rows in order: `sop`, `soap`, `bzjson`.
- `sop` uses the mapped old-core A-column field; `soap` and `bzjson` use the ESF field.
- Output workbook contains exactly `差异明细` then `回放忽略字段配置`.
- Do not add frontend or backend dependencies.
- Do not modify or stage existing user changes in `src/main/resources/sql/create_flow_field_detail.sql` or `clash-config.yaml`.

---

## File Map

**New production files**

- `src/main/java/com/sunline/dict/service/replay/ReplayIgnoreFieldModels.java`: immutable internal records shared by parser, processor, and writer.
- `src/main/java/com/sunline/dict/service/replay/ReplayMappingDocumentParser.java`: fixed-layout workbook parsing and structural validation.
- `src/main/java/com/sunline/dict/service/replay/ReplayIgnoreFieldProcessor.java`: pure pairing, input/output diff, and config-row generation.
- `src/main/java/com/sunline/dict/service/replay/ReplayIgnoreFieldWorkbookWriter.java`: two-sheet `.xlsx` rendering and formatting.
- `src/main/java/com/sunline/dict/service/ReplayIgnoreFieldCleanService.java`: public service contract.
- `src/main/java/com/sunline/dict/service/impl/ReplayIgnoreFieldCleanServiceImpl.java`: upload parsing, orchestration, result-file persistence, and safe file lookup.
- `src/main/java/com/sunline/dict/controller/ReplayIgnoreFieldCleanController.java`: request validation and download response.
- `src/main/resources/static/replay-ignore-field-clean.html`: two-file workflow UI.
- `src/main/resources/sql/add_replay_ignore_field_clean_menu.sql`: idempotent child-menu registration.

**Modified production files**

- `src/main/resources/static/index.html`: permission visibility, menu item, iframe, and title mapping.

**New test files**

- `src/test/java/com/sunline/dict/testutil/ReplayIgnoreFieldExcelFixtureBuilder.java`: in-memory old/new workbook fixtures.
- `src/test/java/com/sunline/dict/service/replay/ReplayMappingDocumentParserTest.java`: parser and validation behavior.
- `src/test/java/com/sunline/dict/service/replay/ReplayIgnoreFieldProcessorTest.java`: pure business rules.
- `src/test/java/com/sunline/dict/service/impl/ReplayIgnoreFieldCleanServiceImplTest.java`: generated workbook and safe result-file behavior.
- `src/test/java/com/sunline/dict/controller/ReplayIgnoreFieldCleanControllerTest.java`: file validation and HTTP-facing result behavior.
- `src/test/java/com/sunline/dict/frontend/ReplayIgnoreFieldFrontendContractTest.java`: static menu/page wiring contract.

---

### Task 1: Parse Both Mapping Workbook Layouts

**Files:**
- Create: `src/test/java/com/sunline/dict/testutil/ReplayIgnoreFieldExcelFixtureBuilder.java`
- Create: `src/test/java/com/sunline/dict/service/replay/ReplayMappingDocumentParserTest.java`
- Create: `src/main/java/com/sunline/dict/service/replay/ReplayIgnoreFieldModels.java`
- Create: `src/main/java/com/sunline/dict/service/replay/ReplayMappingDocumentParser.java`

**Interfaces:**
- Produces: `LinkedHashMap<String, OldSheetMapping> parseOld(Workbook workbook)`.
- Produces: `LinkedHashMap<String, NewSheetMapping> parseNew(Workbook workbook)`.
- Produces nested records `OldSheetMapping`, `NewSheetMapping`, `DifferenceRow`, `ConfigRow`, and `CleanData` in `ReplayIgnoreFieldModels`.

- [ ] **Step 1: Create an in-memory fixture builder**

The builder must write the layout the parser consumes rather than hiding it behind mocks:

```java
ReplayIgnoreFieldExcelFixtureBuilder.oldWorkbook()
    .sheet("A497", "A497", "对公存掉异步处理查询(s010031138)",
           "CorpAsynchrTranHndlQry")
    .input("MdlBusspReputNo", "MdlBusspReputNo")
    .output("ClientCHNNameOld", "ClientCHNName")
    .build();

ReplayIgnoreFieldExcelFixtureBuilder.newWorkbook()
    .sheet("a497", "C191")
    .input("MdlBusspReputNo", "")
    .output("ClientCHNName", "下线")
    .build();
```

Use fixed cells `B1`, `G1`, `L1`, and `L2`; write section markers in A; write old rows to A/K and new rows to A/D. Add `raw(row, col, value)` for malformed cases.

- [ ] **Step 2: Write parser tests before production types exist**

```java
@Test
void parses_only_four_alphanumeric_sheets_and_preserves_section_order() throws Exception {
    try (Workbook old = oldWorkbook()
            .sheet("A497", "A497", "服务(s010031138)", "CorpAsynchrTranHndlQry")
            .input("oldIn", "EsfIn")
            .output("oldOut", "ClientCHNName")
            .sheet("说明", "", "", "").input("ignored", "Ignored")
            .build()) {
        LinkedHashMap<String, OldSheetMapping> parsed = parser.parseOld(old);
        assertEquals(List.of("A497"), parsed.keySet().stream().toList());
        OldSheetMapping mapping = parsed.get("A497");
        assertEquals(List.of("EsfIn"), mapping.inputEsfFields());
        assertEquals(List.of("ClientCHNName"), mapping.outputEsfFields());
        assertEquals("oldOut", mapping.oldOutputMappings().get("ClientCHNName"));
        assertEquals("S010031138CorpAsynchrTranHndlQry", mapping.replayTransactionPrefix());
    }
}

@Test
void rejects_case_insensitive_duplicate_sheet_keys() throws Exception {
    try (Workbook old = oldWorkbook()
            .sheet("A497", "A497", "服务(s1)", "Op").input("a", "A").output("b", "B")
            .sheet("a497", "A497", "服务(s1)", "Op").input("a", "A").output("b", "B")
            .build()) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parseOld(old));
        assertTrue(ex.getMessage().contains("A497/a497"));
    }
}
```

Also add focused tests for `4497`, `49A7`, `ABCD`, invalid names, missing input/output markers, duplicate ESF fields within one section, full-width parentheses in L1, and new output `下线` collection.

- [ ] **Step 3: Run the parser test and verify RED**

Run:

```bash
mvn -DskipTests=false -Dtest=ReplayMappingDocumentParserTest test
```

Expected: compilation fails because `ReplayMappingDocumentParser` and model records do not exist.

- [ ] **Step 4: Implement immutable records**

```java
public final class ReplayIgnoreFieldModels {
    private ReplayIgnoreFieldModels() {}

    public record OldSheetMapping(
            String sheetName,
            String matchKey,
            String oldTransactionCode,
            String replayTransactionPrefix,
            List<String> inputEsfFields,
            List<String> outputEsfFields,
            Map<String, String> oldOutputMappings) {}

    public record NewSheetMapping(
            String sheetName,
            String matchKey,
            String newTransactionCode,
            List<String> inputEsfFields,
            List<String> outputEsfFields,
            List<String> offlineOutputFields) {}

    public record DifferenceRow(String oldTransaction, String newTransaction, String detail) {}
    public record ConfigRow(String domain, String transactionCode, String ignoredField,
                            String batch, String registered) {}
    public record CleanData(List<DifferenceRow> differences, List<ConfigRow> configs,
                            int processedTransactionCount) {}
}
```

Copy incoming lists/maps in compact constructors with `List.copyOf` and `Map.copyOf` so later processing cannot mutate parser output.

- [ ] **Step 5: Implement the fixed-layout parser**

Key constants and signatures:

```java
public final class ReplayMappingDocumentParser {
    private static final Pattern TRANSACTION_SHEET = Pattern.compile("^[A-Za-z0-9]{4}$");
    private static final int COL_A = 0;
    private static final int COL_B = 1;
    private static final int COL_D = 3;
    private static final int COL_G = 6;
    private static final int COL_K = 10;
    private static final int COL_L = 11;
}
```

Use `DataFormatter`, dynamic A-column markers, and exclusive row ranges `(输入 marker, 输出 marker)` and `(输出 marker, last row]`. A row is a business field only when its ESF source cell is nonblank; skip marker and header text such as `英文名称`. Reject duplicate match keys and duplicate ESF fields with messages containing document side, sheet, section, field, and Excel row number.

For L1, accept ASCII and full-width parentheses, take the final nonblank parenthesized token, uppercase that token with `Locale.ROOT`, and append trimmed L2. Do not require replay prefix while parsing new-only or structurally different transactions; validate it when a transaction actually generates config.

- [ ] **Step 6: Run parser tests and the existing suite**

```bash
mvn -DskipTests=false -Dtest=ReplayMappingDocumentParserTest test
mvn -DskipTests=false test
```

Expected: all parser tests pass; existing tests remain green.

- [ ] **Step 7: Commit parser slice**

```bash
git add src/main/java/com/sunline/dict/service/replay/ReplayIgnoreFieldModels.java \
        src/main/java/com/sunline/dict/service/replay/ReplayMappingDocumentParser.java \
        src/test/java/com/sunline/dict/testutil/ReplayIgnoreFieldExcelFixtureBuilder.java \
        src/test/java/com/sunline/dict/service/replay/ReplayMappingDocumentParserTest.java
git commit -m "feat: parse replay field mapping workbooks"
```

### Task 2: Pair Transactions, Report Differences, and Build Config Rows

**Files:**
- Create: `src/test/java/com/sunline/dict/service/replay/ReplayIgnoreFieldProcessorTest.java`
- Create: `src/main/java/com/sunline/dict/service/replay/ReplayIgnoreFieldProcessor.java`

**Interfaces:**
- Consumes: parser maps keyed by uppercase four-character match key.
- Produces: `CleanData process(LinkedHashMap<String, OldSheetMapping> oldSheets, LinkedHashMap<String, NewSheetMapping> newSheets)`.

- [ ] **Step 1: Write failing difference tests**

```java
@Test
void records_old_only_new_only_and_separate_input_output_differences() {
    CleanData data = processor.process(oldSheets(
            old("A497", "OLD1", List.of("InA"), List.of("OutA")),
            old("B497", "OLD_ONLY", List.of("In"), List.of("Out"))),
        newSheets(
            newer("a497", "NEW1", List.of("InB"), List.of("OutB")),
            newer("C497", "NEW_ONLY", List.of("In"), List.of("Out"))));

    assertEquals(3, data.differences().size());
    assertEquals("OLD_ONLY", data.differences().get(1).oldTransaction());
    assertEquals("NEW_ONLY", data.differences().get(2).newTransaction());
    assertTrue(data.differences().get(0).detail().contains("输入差异"));
    assertTrue(data.differences().get(0).detail().contains("输出差异"));
    assertTrue(data.configs().isEmpty());
}
```

Add a strict-case test where `ClientCHNName` and `clientCHNName` are different, and assert that detail lists only side-exclusive fields, never shared fields.

- [ ] **Step 2: Write failing config-generation tests**

```java
@Test
void every_offline_output_field_generates_sop_soap_and_bzjson_rows() {
    OldSheetMapping old = oldWithMappings("A497", "S010031138CorpAsynchrTranHndlQry",
            Map.of("ClientCHNName", "ClientCHNNameOld", "ReserveAmt", "ReserveAmtOld"));
    NewSheetMapping newer = newWithOfflineOutputs("a497", "C191",
            List.of("ClientCHNName", "ReserveAmt"),
            List.of("ClientCHNName", "ReserveAmt"));

    CleanData data = processor.process(map(old), map(newer));

    assertEquals(6, data.configs().size());
    assertEquals("S010031138CorpAsynchrTranHndlQry&sop", data.configs().get(0).transactionCode());
    assertEquals("ClientCHNNameOld", data.configs().get(0).ignoredField());
    assertEquals("ClientCHNName", data.configs().get(1).ignoredField());
    assertEquals("ClientCHNName", data.configs().get(2).ignoredField());
    assertEquals("ReserveAmtOld", data.configs().get(3).ignoredField());
}
```

Add a test proving new input rows marked `下线` never appear in `offlineOutputFields` or config. Add a missing old mapping/replay-prefix test that asserts a sheet- and field-specific exception.

- [ ] **Step 3: Run processor tests and verify RED**

```bash
mvn -DskipTests=false -Dtest=ReplayIgnoreFieldProcessorTest test
```

Expected: compilation fails because `ReplayIgnoreFieldProcessor` does not exist.

- [ ] **Step 4: Implement pure processing**

```java
public final class ReplayIgnoreFieldProcessor {
    public CleanData process(LinkedHashMap<String, OldSheetMapping> oldSheets,
                             LinkedHashMap<String, NewSheetMapping> newSheets) {
        List<DifferenceRow> differences = new ArrayList<>();
        List<ConfigRow> configs = new ArrayList<>();
        Set<String> paired = new HashSet<>();
        int processed = 0;

        for (OldSheetMapping old : oldSheets.values()) {
            NewSheetMapping newer = newSheets.get(old.matchKey());
            if (newer == null) {
                differences.add(new DifferenceRow(old.oldTransactionCode(), "", ""));
                continue;
            }
            paired.add(old.matchKey());
            String detail = buildDifferenceDetail(old, newer);
            if (!detail.isEmpty()) {
                differences.add(new DifferenceRow(old.oldTransactionCode(),
                        newer.newTransactionCode(), detail));
                continue;
            }
            processed++;
            appendConfigRows(old, newer, configs);
        }
        for (NewSheetMapping newer : newSheets.values()) {
            if (!paired.contains(newer.matchKey())) {
                differences.add(new DifferenceRow("", newer.newTransactionCode(), ""));
            }
        }
        return new CleanData(differences, configs, processed);
    }
}
```

Compute ordered set subtraction by iterating source lists and checking a `HashSet` of the opposite side. Use exact Java `String.equals`. Format only differing sections with newline separation and `无` for the empty side. For every offline output field, require nonblank replay prefix and old mapping, then append these exact rows in protocol order:

```java
configs.add(new ConfigRow("", prefix + "&sop", oldCoreField, "", ""));
configs.add(new ConfigRow("", prefix + "&soap", esfField, "", ""));
configs.add(new ConfigRow("", prefix + "&bzjson", esfField, "", ""));
```

- [ ] **Step 5: Run processor and parser tests**

```bash
mvn -DskipTests=false -Dtest=ReplayIgnoreFieldProcessorTest,ReplayMappingDocumentParserTest test
```

Expected: all tests pass.

- [ ] **Step 6: Commit processor slice**

```bash
git add src/main/java/com/sunline/dict/service/replay/ReplayIgnoreFieldProcessor.java \
        src/test/java/com/sunline/dict/service/replay/ReplayIgnoreFieldProcessorTest.java
git commit -m "feat: build replay ignore field differences and configs"
```

### Task 3: Render and Persist the Result Workbook

**Files:**
- Create: `src/test/java/com/sunline/dict/service/impl/ReplayIgnoreFieldCleanServiceImplTest.java`
- Create: `src/main/java/com/sunline/dict/service/replay/ReplayIgnoreFieldWorkbookWriter.java`
- Create: `src/main/java/com/sunline/dict/service/ReplayIgnoreFieldCleanService.java`
- Create: `src/main/java/com/sunline/dict/service/impl/ReplayIgnoreFieldCleanServiceImpl.java`

**Interfaces:**
- Consumes: `CleanData` from Task 2.
- Produces: `void write(CleanData data, OutputStream outputStream)`.
- Produces: `Map<String, Object> cleanFiles(MultipartFile oldCoreEsfFile, MultipartFile esfNewCoreFile) throws Exception`.
- Produces: `File getResultFile(String fileName)` with canonical-path containment.

- [ ] **Step 1: Write the failing end-to-end service test**

```java
@SpringBootTest
class ReplayIgnoreFieldCleanServiceImplTest {
    @Autowired ReplayIgnoreFieldCleanService service;
    private File resultFile;

    @Test
    void creates_two_formatted_sheets_and_expected_statistics() throws Exception {
        MultipartFile oldFile = oldFixtureWithOneMappedOfflineField();
        MultipartFile newFile = newFixtureWithOneMappedOfflineField();

        Map<String, Object> result = service.cleanFiles(oldFile, newFile);
        resultFile = service.getResultFile((String) result.get("fileName"));

        try (Workbook workbook = WorkbookFactory.create(resultFile)) {
            assertEquals(List.of("差异明细", "回放忽略字段配置"),
                    IntStream.range(0, workbook.getNumberOfSheets())
                        .mapToObj(workbook::getSheetName).toList());
            Sheet config = workbook.getSheetAt(1);
            assertEquals("领域", config.getRow(0).getCell(0).getStringCellValue());
            assertEquals("交易码", config.getRow(0).getCell(1).getStringCellValue());
            assertEquals("S010031138CorpAsynchrTranHndlQry&sop",
                    config.getRow(1).getCell(1).getStringCellValue());
            assertEquals("ClientCHNNameOld", config.getRow(1).getCell(2).getStringCellValue());
            assertEquals("ClientCHNName", config.getRow(2).getCell(2).getStringCellValue());
            assertEquals("ClientCHNName", config.getRow(3).getCell(2).getStringCellValue());
            assertEquals(1, config.getFreezePane().getHorizontalSplitPosition());
            assertTrue(((XSSFSheet) config).getCTWorksheet().isSetAutoFilter());
        }
        assertEquals(0, result.get("differenceCount"));
        assertEquals(1, result.get("processedTransactionCount"));
        assertEquals(3, result.get("configRowCount"));
    }
}
```

Add tests for both sheets remaining present with headers when lists are empty, multiline difference text, exact filename prefix/suffix, and `getResultFile("../application.yml")` rejection.

- [ ] **Step 2: Run the service test and verify RED**

```bash
mvn -DskipTests=false -Dtest=ReplayIgnoreFieldCleanServiceImplTest test
```

Expected: compilation fails because service and writer types do not exist.

- [ ] **Step 3: Implement the workbook writer**

```java
public final class ReplayIgnoreFieldWorkbookWriter {
    public static final String DIFFERENCE_SHEET = "差异明细";
    public static final String CONFIG_SHEET = "回放忽略字段配置";

    public void write(CleanData data, OutputStream outputStream) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet differences = workbook.createSheet(DIFFERENCE_SHEET);
            Sheet configs = workbook.createSheet(CONFIG_SHEET);
            writeDifferenceSheet(workbook, differences, data.differences());
            writeConfigSheet(workbook, configs, data.configs());
            workbook.write(outputStream);
        }
    }
}
```

Create a reusable header style with borders, solid fill, bold white font, and centered alignment. Use text data format `@` for all body cells, wrap the difference detail column, freeze row 1 with `createFreezePane(0, 1)`, set `CellRangeAddress(0, 0, 0, lastColumn)`, and set explicit widths rather than autosizing potentially large workbooks.

- [ ] **Step 4: Implement the service contract and orchestration**

```java
public interface ReplayIgnoreFieldCleanService {
    Map<String, Object> cleanFiles(MultipartFile oldCoreEsfFile,
                                   MultipartFile esfNewCoreFile) throws Exception;
    File getResultFile(String fileName);
}
```

`ReplayIgnoreFieldCleanServiceImpl` should constructor-inject parser, processor, and writer. Mark those collaborators `@Component`, or expose them as package-visible `@Bean`s; do not instantiate them inside request methods.

Use `WorkbookFactory.create` for both inputs and `XSSFWorkbook` only for output. Persist under existing `excel_compare_results`, create directories with `Files.createDirectories`, and write to a same-directory temporary file before atomic move to the final Chinese filename. Return a `LinkedHashMap` with keys in this order: `fileName`, `differenceCount`, `processedTransactionCount`, `configRowCount`.

Canonical-path check:

```java
Path resultDir = Path.of(RESULT_DIR).toAbsolutePath().normalize();
Path candidate = resultDir.resolve(fileName).normalize();
if (!candidate.startsWith(resultDir)) {
    throw new IllegalArgumentException("非法的文件名: " + fileName);
}
return candidate.toFile();
```

- [ ] **Step 5: Run service tests and full backend tests**

```bash
mvn -DskipTests=false -Dtest=ReplayIgnoreFieldCleanServiceImplTest test
mvn -DskipTests=false test
```

Expected: all tests pass and created test result files are removed in `@AfterEach`.

- [ ] **Step 6: Commit writer/service slice**

```bash
git add src/main/java/com/sunline/dict/service/ReplayIgnoreFieldCleanService.java \
        src/main/java/com/sunline/dict/service/impl/ReplayIgnoreFieldCleanServiceImpl.java \
        src/main/java/com/sunline/dict/service/replay/ReplayIgnoreFieldWorkbookWriter.java \
        src/test/java/com/sunline/dict/service/impl/ReplayIgnoreFieldCleanServiceImplTest.java
git commit -m "feat: export replay ignore field workbook"
```

### Task 4: Add HTTP Clean and Download Endpoints

**Files:**
- Create: `src/test/java/com/sunline/dict/controller/ReplayIgnoreFieldCleanControllerTest.java`
- Create: `src/main/java/com/sunline/dict/controller/ReplayIgnoreFieldCleanController.java`

**Interfaces:**
- Consumes: `ReplayIgnoreFieldCleanService` from Task 3.
- Produces: `POST /api/replay-ignore-field-clean/clean` with multipart names `oldCoreEsfFile` and `esfNewCoreFile`.
- Produces: `GET /api/replay-ignore-field-clean/download/{fileName}`.

- [ ] **Step 1: Write failing standalone MockMvc tests**

```java
@ExtendWith(MockitoExtension.class)
class ReplayIgnoreFieldCleanControllerTest {
    @Mock ReplayIgnoreFieldCleanService service;
    @InjectMocks ReplayIgnoreFieldCleanController controller;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void rejects_non_excel_extension_without_calling_service() throws Exception {
        mvc.perform(multipart("/api/replay-ignore-field-clean/clean")
                .file(file("oldCoreEsfFile", "old.csv", "x"))
                .file(file("esfNewCoreFile", "new.xlsx", "x")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(500))
            .andExpect(jsonPath("$.message").value("文件格式不正确，只支持.xlsx和.xls格式"));
        verifyNoInteractions(service);
    }
}
```

Add success delegation, empty file, uppercase `.XLSX` acceptance, a service exception whose response is exactly `清洗失败：测试异常`, existing-file download with content disposition, missing-file 404, and illegal file-name 400 tests.

- [ ] **Step 2: Run controller tests and verify RED**

```bash
mvn -DskipTests=false -Dtest=ReplayIgnoreFieldCleanControllerTest test
```

Expected: compilation fails because `ReplayIgnoreFieldCleanController` does not exist.

- [ ] **Step 3: Implement the thin controller**

```java
@RestController
@RequestMapping("/api/replay-ignore-field-clean")
public class ReplayIgnoreFieldCleanController {
    @PostMapping("/clean")
    public Result<Map<String, Object>> clean(
            @RequestParam("oldCoreEsfFile") MultipartFile oldCoreEsfFile,
            @RequestParam("esfNewCoreFile") MultipartFile esfNewCoreFile) {
        if (oldCoreEsfFile.isEmpty() || esfNewCoreFile.isEmpty()) {
            return Result.error("文件不能为空");
        }
        if (!isExcel(oldCoreEsfFile.getOriginalFilename())
                || !isExcel(esfNewCoreFile.getOriginalFilename())) {
            return Result.error("文件格式不正确，只支持.xlsx和.xls格式");
        }
        try {
            return Result.success(service.cleanFiles(oldCoreEsfFile, esfNewCoreFile));
        } catch (Exception e) {
            return Result.error("清洗失败：" + e.getMessage());
        }
    }

    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        try {
            File file = service.getResultFile(fileName);
            if (!file.exists()) return ResponseEntity.notFound().build();
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(fileName, StandardCharsets.UTF_8).build();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .body(new FileSystemResource(file));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
```

Implement `isExcel` as a private null-safe helper using `toLowerCase(Locale.ROOT)` and exact `.xls`/`.xlsx` suffixes. The code above fixes processing errors, illegal download names, missing files, other IO failures, and standards-compliant Chinese attachment names.

- [ ] **Step 4: Run controller tests and full backend suite**

```bash
mvn -DskipTests=false -Dtest=ReplayIgnoreFieldCleanControllerTest test
mvn -DskipTests=false test
```

Expected: all tests pass.

- [ ] **Step 5: Commit controller slice**

```bash
git add src/main/java/com/sunline/dict/controller/ReplayIgnoreFieldCleanController.java \
        src/test/java/com/sunline/dict/controller/ReplayIgnoreFieldCleanControllerTest.java
git commit -m "feat: expose replay ignore field clean API"
```

### Task 5: Add Menu, Page, Cancellation, and Download Workflow

**Files:**
- Create: `src/test/java/com/sunline/dict/frontend/ReplayIgnoreFieldFrontendContractTest.java`
- Create: `src/main/resources/static/replay-ignore-field-clean.html`
- Create: `src/main/resources/sql/add_replay_ignore_field_clean_menu.sql`
- Modify: `src/main/resources/static/index.html:939-1010`
- Modify: `src/main/resources/static/index.html:1053-1085`
- Modify: `src/main/resources/static/index.html:2145-2165`

**Interfaces:**
- Consumes: endpoints and response keys from Task 4.
- Produces: menu code `replay-ignore-field-clean` and iframe `/replay-ignore-field-clean.html`.

- [ ] **Step 1: Write a failing static resource contract test**

```java
class ReplayIgnoreFieldFrontendContractTest {
    @Test
    void index_and_feature_page_are_wired_to_the_same_menu_code() throws Exception {
        String index = Files.readString(Path.of("src/main/resources/static/index.html"));
        String page = Files.readString(Path.of("src/main/resources/static/replay-ignore-field-clean.html"));

        assertTrue(index.contains("hasMenuPermission('replay-ignore-field-clean')"));
        assertTrue(index.contains("currentView === 'replay-ignore-field-clean'"));
        assertTrue(index.contains("src=\"/replay-ignore-field-clean.html\""));
        assertTrue(page.contains("/api/replay-ignore-field-clean/clean"));
        assertTrue(page.contains("oldCoreEsfFile"));
        assertTrue(page.contains("esfNewCoreFile"));
        assertTrue(page.contains("new AbortController()"));
    }
}
```

Add assertions for exact upload labels, three action button labels, reset behavior identifiers, result statistic keys, and encoded download URL.

- [ ] **Step 2: Run frontend contract test and verify RED**

```bash
mvn -DskipTests=false -Dtest=ReplayIgnoreFieldFrontendContractTest test
```

Expected: fail because the feature page and index wiring do not exist.

- [ ] **Step 3: Create the idempotent menu SQL**

```sql
INSERT INTO sys_menu
    (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time)
SELECT
    'replay-ignore-field-clean', '回放联机交易忽略字段清洗', id, 2, '🧹', 14, 1, NOW(), NOW()
FROM sys_menu
WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE
    menu_name = '回放联机交易忽略字段清洗',
    icon = '🧹',
    sort_order = 14,
    update_time = NOW();
```

- [ ] **Step 4: Build the feature page**

Use a quiet operational layout consistent with the existing application: two side-by-side dashed upload areas on desktop, one column under `768px`, restrained white/gray surfaces, green primary action, neutral cancel, red reset, stable button dimensions, and no marketing copy.

Required JavaScript state and cancellation:

```javascript
let oldCoreEsfFile = null;
let esfNewCoreFile = null;
let resultFileName = null;
let activeController = null;

async function startClean() {
    activeController = new AbortController();
    const formData = new FormData();
    formData.append('oldCoreEsfFile', oldCoreEsfFile);
    formData.append('esfNewCoreFile', esfNewCoreFile);
    try {
        const response = await fetch('/api/replay-ignore-field-clean/clean', {
            method: 'POST',
            body: formData,
            signal: activeController.signal
        });
        const result = await response.json();
        if (result.code !== 200) throw new Error(result.message || '清洗失败');
        resultFileName = result.data.fileName;
        renderResult(result.data);
    } catch (error) {
        if (error.name !== 'AbortError') showError(error.message);
    } finally {
        activeController = null;
        setProcessing(false);
    }
}

function cancelClean() {
    if (activeController) activeController.abort();
    setProcessing(false);
}

function resetForm() {
    cancelClean();
    // Clear both input elements, selected-file state, errors, stats, and resultFileName.
}
```

Download with:

```javascript
window.location.href = '/api/replay-ignore-field-clean/download/'
    + encodeURIComponent(resultFileName);
```

Do not use emoji-only buttons; the page must visibly show `开始清洗`, `取消`, and `重置`.

- [ ] **Step 5: Wire the application shell**

Update all four required locations in `index.html`:

1. Add `hasMenuPermission('replay-ignore-field-clean')` to the version-branch menu-group visibility expression.
2. Add the menu item immediately after `public-core-file-baseline-compare`.
3. Add `v-show` iframe `/replay-ignore-field-clean.html`.
4. Add title mapping `'replay-ignore-field-clean': '🧹 回放联机交易忽略字段清洗'`.

- [ ] **Step 6: Run frontend contract and full tests**

```bash
mvn -DskipTests=false -Dtest=ReplayIgnoreFieldFrontendContractTest test
mvn -DskipTests=false test
```

Expected: all tests pass.

- [ ] **Step 7: Commit UI/menu slice**

```bash
git add src/main/resources/static/replay-ignore-field-clean.html \
        src/main/resources/static/index.html \
        src/main/resources/sql/add_replay_ignore_field_clean_menu.sql \
        src/test/java/com/sunline/dict/frontend/ReplayIgnoreFieldFrontendContractTest.java
git commit -m "feat: add replay ignore field clean page"
```

### Task 6: Full Verification and Design Status Update

**Files:**
- Modify after successful verification: `/Users/java/obsidian/01 Engineering/sunline-benchmark/回放联机交易忽略字段清洗-系统设计.md`
- Modify after successful verification: `/Users/java/obsidian/01 Engineering/sunline-benchmark/回放联机交易忽略字段清洗-数据模型.md`
- Modify after successful verification: `/Users/java/obsidian/01 Engineering/sunline-benchmark/回放联机交易忽略字段清洗-API接口.md`
- Modify after successful verification: `/Users/java/obsidian/01 Engineering/sunline-benchmark/_overview.md`
- Append after successful verification: `/Users/java/obsidian/log.md`

**Interfaces:**
- Consumes: complete backend, frontend, SQL, and test deliverables.
- Produces: verified runnable feature and up-to-date design status.

- [ ] **Step 1: Run targeted and full automated verification**

```bash
mvn -DskipTests=false \
    -Dtest=ReplayMappingDocumentParserTest,ReplayIgnoreFieldProcessorTest,ReplayIgnoreFieldCleanServiceImplTest,ReplayIgnoreFieldCleanControllerTest,ReplayIgnoreFieldFrontendContractTest \
    test
mvn -DskipTests=false test
mvn -DskipTests=false package
git diff --check
```

Expected: all Maven commands exit 0, all tests pass, package succeeds, and `git diff --check` is silent.

- [ ] **Step 2: Start the application without disturbing an existing server**

Check the configured port and running process first. If port 8080 is occupied by this project, reuse it; otherwise start on an available port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Keep the session running until browser verification finishes.

- [ ] **Step 3: Verify the page in a real browser**

At desktop `1440x900` and mobile `390x844`, verify:

- Both upload labels and selected file names fit without overlap.
- Start is disabled until both files are selected.
- Cancel aborts an active request without showing a failure alert.
- Reset clears both files, stats, errors, and download state.
- A known fixture produces the exact Chinese output filename and downloadable workbook.
- The downloaded workbook opens with two sheets in the required order and expected six rows for two offline fields.
- No console errors or failed static assets appear.

Capture screenshots for both viewports as verification evidence; do not add screenshots to git unless requested.

- [ ] **Step 4: Review the implementation against the written design**

Check every requirement in the system design sections 2, 5, 6, 7, 8, and 9. If implementation requires a design correction, update Obsidian before claiming completion. Confirm there is no repository-local duplicate design spec.

- [ ] **Step 5: Mark the Obsidian design implemented and append the log**

Keep the schema-valid `status: active`, update `updated: 2026-08-03`, add a short implementation verification section to the system design, update `_overview.md` if file names/interfaces differ from the plan, and append exactly one implementation log line:

```text
2026-08-03 [IMPL] sunline-benchmark 回放联机交易忽略字段清洗落地 | 更新代码、测试及 4 工程页 | 双 Excel 输入/输出 ESF 差异门禁、下线输出字段三协议配置、菜单页面与下载流程完成；Maven 全量测试及桌面、移动视口验证通过
```

Stage and commit only these feature-related Obsidian files; preserve all unrelated vault changes.

- [ ] **Step 6: Inspect final repository scope**

```bash
git status --short
git diff --stat HEAD~4..HEAD
git log -5 --oneline
```

Expected: user-owned `src/main/resources/sql/create_flow_field_detail.sql` and `clash-config.yaml` remain untouched; feature commits contain only the files named in this plan.
