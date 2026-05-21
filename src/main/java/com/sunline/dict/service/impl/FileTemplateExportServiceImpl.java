package com.sunline.dict.service.impl;

import com.sunline.dict.entity.EschemaDetail;
import com.sunline.dict.entity.UschemaDetail;
import com.sunline.dict.mapper.EschemaDetailMapper;
import com.sunline.dict.mapper.UschemaDetailMapper;
import com.sunline.dict.service.FileTemplateExportService;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件模版导出服务实现。
 * 基于 /home/facility/code 下四个 impl 工程的 batchfile XML 实时扫描生成 Excel。
 */
@Service
public class FileTemplateExportServiceImpl implements FileTemplateExportService {

    private static final Logger log = LoggerFactory.getLogger(FileTemplateExportServiceImpl.class);

    private static final Path CODE_ROOT = Paths.get("/home/facility/code");
    private static final List<String> PROJECTS = Arrays.asList(
            "ccbs-comm-impl",
            "ccbs-dept-impl",
            "ccbs-loan-impl",
            "ccbs-sett-impl"
    );
    private static final String FILE_SUFFIX = ".file_batch_tran.xml";
    private static final Set<String> XML_INVALID_SHEET_CHARS = new TreeSet<>(Arrays.asList("\\", "/", "?", "*", "[", "]", ":"));

    private final UschemaDetailMapper uschemaDetailMapper;
    private final EschemaDetailMapper eschemaDetailMapper;

    @Autowired
    public FileTemplateExportServiceImpl(UschemaDetailMapper uschemaDetailMapper,
                                         EschemaDetailMapper eschemaDetailMapper) {
        this.uschemaDetailMapper = uschemaDetailMapper;
        this.eschemaDetailMapper = eschemaDetailMapper;
    }

    @Override
    public byte[] exportTemplateWorkbook(String scope) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            LookupContext lookupContext = buildLookupContext();
            List<BatchTemplateRecord> records = scanRecords(scope);

            createIndexSheet(workbook, styles, records, scope);
            for (BatchTemplateRecord record : records) {
                createTemplateSheet(workbook, styles, record, lookupContext);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private List<BatchTemplateRecord> scanRecords(String scope) throws Exception {
        List<BatchTemplateRecord> records = new ArrayList<>();
        Set<String> usedSheetNames = new TreeSet<>();

        for (String project : PROJECTS) {
            String domain = domainByProject(project);
            if (!matchScope(scope, domain)) {
                continue;
            }

            Path projectRoot = CODE_ROOT.resolve(project);
            if (!Files.isDirectory(projectRoot)) {
                log.warn("文件模版导出扫描时未找到工程目录: {}", projectRoot);
                continue;
            }

            try (Stream<Path> pathStream = Files.walk(projectRoot)) {
                pathStream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.toString().contains("/src/main/resources/batchfile/"))
                        .filter(path -> path.getFileName().toString().endsWith(FILE_SUFFIX))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(path -> {
                            try {
                                BatchTemplateRecord record = parseRecord(path, project, domain, usedSheetNames);
                                if (record != null) {
                                    records.add(record);
                                }
                            } catch (Exception e) {
                                log.error("解析文件批 XML 失败: {}", path, e);
                            }
                        });
            }
        }

        records.sort(Comparator
                .comparing(BatchTemplateRecord::getDomainName)
                .thenComparing(BatchTemplateRecord::getTxCode)
                .thenComparing(BatchTemplateRecord::getSourcePath));
        return records;
    }

    private BatchTemplateRecord parseRecord(Path path, String project, String domain, Set<String> usedSheetNames) throws Exception {
        String content = Files.readString(path, StandardCharsets.UTF_8);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(content)));

        Element root = doc.getDocumentElement();
        if (root == null || !"file_batch_transaction".equals(root.getTagName())) {
            return null;
        }

        String txCode = trim(root.getAttribute("id"));
        String longName = trim(root.getAttribute("longname"));
        String kind = trim(root.getAttribute("kind"));
        if (txCode.isEmpty()) {
            return null;
        }

        Element fileTemplate = firstDirectChild(root, "fileTemplate");
        if (fileTemplate == null) {
            return null;
        }

        String encoding = trim(fileTemplate.getAttribute("encoding"));
        String splitor = trim(fileTemplate.getAttribute("splitor"));

        List<FieldItem> headerFields = parseFields(fileTemplate, "header");
        List<FieldItem> bodyFields = parseFields(fileTemplate, "body");
        List<FieldItem> footFields = parseFields(fileTemplate, "foot");

        String sheetName = uniqueSheetName(sanitizeSheetName(txCode), usedSheetNames);
        String relativePath = relativizeSafe(path);

        BatchTemplateRecord record = new BatchTemplateRecord();
        record.domainName = domain;
        record.projectName = project;
        record.txCode = txCode;
        record.txName = longName;
        record.kind = kind;
        record.fileName = path.getFileName().toString();
        record.sourcePath = relativePath;
        record.sheetName = sheetName;
        record.encoding = encoding;
        record.splitor = splitor;
        record.headerFields = headerFields;
        record.bodyFields = bodyFields;
        record.footFields = footFields;
        return record;
    }

    private List<FieldItem> parseFields(Element fileTemplate, String sectionTagName) {
        List<FieldItem> fields = new ArrayList<>();
        Element section = firstDirectChild(fileTemplate, sectionTagName);
        if (section == null) {
            return fields;
        }

        NodeList childNodes = section.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;
            if (!"field".equals(element.getTagName())) {
                continue;
            }

            FieldItem item = new FieldItem();
            item.id = trim(element.getAttribute("id"));
            item.longName = trim(element.getAttribute("longname"));
            item.type = trim(element.getAttribute("type"));
            fields.add(item);
        }
        return fields;
    }

    private void createIndexSheet(XSSFWorkbook workbook, Styles styles, List<BatchTemplateRecord> records, String scope) {
        Sheet sheet = workbook.createSheet("索引");
        enableGridlines(sheet);
        sheet.setDefaultRowHeightInPoints(22);

        String[] headers = {
                "序号", "领域", "文件编号", "老交易码", "老交易名称", "528全路径文件名",
                "文件接口变化情况", "新交易码", "新交易名称", "新全路径文件名", "行内负责人", "开发负责人（厂商/行员）", "备注"
        };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            setCell(headerRow, i, headers[i], styles.headerStyle);
        }

        CreationHelper creationHelper = workbook.getCreationHelper();
        int rowIndex = 1;
        for (int i = 0; i < records.size(); i++) {
            BatchTemplateRecord record = records.get(i);
            Row row = sheet.createRow(rowIndex++);
            setCell(row, 0, String.valueOf(i + 1), styles.dataStyle);
            setCell(row, 1, record.domainName, styles.dataStyle);
            setHyperlinkCell(row, 2, record.txCode, styles.linkStyle, creationHelper, record.sheetName);
            setCell(row, 3, "", styles.dataStyle);
            setCell(row, 4, "", styles.dataStyle);
            setCell(row, 5, "", styles.dataStyle);
            setCell(row, 6, "", styles.dataStyle);
            setCell(row, 7, record.txCode, styles.dataStyle);
            setCell(row, 8, record.txName, styles.dataStyle);
            setCell(row, 9, "", styles.dataStyle);
            setCell(row, 10, "", styles.dataStyle);
            setCell(row, 11, "", styles.dataStyle);
            setCell(row, 12, "", styles.dataStyle);
        }

        int[] widths = {8, 14, 14, 14, 18, 20, 28, 14, 22, 20, 14, 22, 12};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void createTemplateSheet(XSSFWorkbook workbook, Styles styles, BatchTemplateRecord record, LookupContext lookupContext) {
        Sheet sheet = workbook.createSheet(record.sheetName);
        enableGridlines(sheet);
        sheet.setDefaultRowHeightInPoints(22);

        createFixedTopArea(sheet, styles, record);
        createFieldHeader(sheet, styles);
        createFieldSections(sheet, styles, record, lookupContext);

        int[] widths = {24, 10, 18, 16, 14, 16, 18, 18, 18};
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void createFixedTopArea(Sheet sheet, Styles styles, BatchTemplateRecord record) {
        createFixedRow(sheet, styles, 0, "交易码", record.txCode);
        createFixedRow(sheet, styles, 1, "接口名称", record.txName);
        createFixedRow(sheet, styles, 2, "文件名", "");
        createFixedRow(sheet, styles, 3, "接口描述", record.txName);
        createFixedRow(sheet, styles, 4, "输出文件目录", "");
        createFixedRow(sheet, styles, 5, "传入目录", "");
        createFixedRow(sheet, styles, 6, "传出目录", "");
        createFixedRow(sheet, styles, 7, "上传信号文件", "");

        Row formatRow = sheet.createRow(8);
        Cell labelCell = formatRow.createCell(0);
        labelCell.setCellValue("文件格式");
        labelCell.setCellStyle(styles.labelStyle);
        merge(sheet, 8, 9, 0, 0);

        fillCells(formatRow, 1, 8, styles.multilineValueStyle);
        Cell valueCell = formatRow.createCell(1);
        valueCell.setCellValue(buildFormatText(record));
        valueCell.setCellStyle(styles.multilineValueStyle);
        merge(sheet, 8, 9, 1, 8);
        formatRow.setHeightInPoints(120);
        Row row10 = sheet.createRow(9);
        for (int i = 1; i <= 8; i++) {
            Cell cell = row10.createCell(i);
            cell.setCellStyle(styles.multilineValueStyle);
        }

        merge(sheet, 10, 10, 0, 8);
        setCell(sheet, 10, 0, "read".equalsIgnoreCase(record.kind) ? "新输入文件" : "新输出文件", styles.sectionTitleStyle);
    }

    private void createFixedRow(Sheet sheet, Styles styles, int rowIndex, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        setCell(row, 0, label, styles.labelStyle);
        fillCells(row, 1, 8, styles.valueStyle);
        setCell(row, 1, value, styles.valueStyle);
        merge(sheet, rowIndex, rowIndex, 1, 8);
    }

    private String buildFormatText(BatchTemplateRecord record) {
        return String.join("\n",
                "文件编码：" + safeValue(record.encoding),
                "分隔符：" + safeValue(record.splitor),
                "换行符：",
                "文件长度：",
                "文件长度单位：",
                "文件名：",
                "文件频率：",
                "批次号、序号：");
    }

    private void createFieldHeader(Sheet sheet, Styles styles) {
        Row headerRow = sheet.createRow(11);
        String[] headers = {
                "列名", "列顺序", "列基础类型", "列最大长度（字节）",
                "是否非空", "列描述", "枚举值", "备注", "差异化分析"
        };
        for (int i = 0; i < headers.length; i++) {
            setCell(headerRow, i, headers[i], styles.headerStyle);
        }
    }

    private void createFieldSections(Sheet sheet, Styles styles, BatchTemplateRecord record, LookupContext lookupContext) {
        int currentRow = 12;
        currentRow = writeSection(sheet, styles, currentRow, "Header", record.headerFields, lookupContext);
        currentRow = writeSection(sheet, styles, currentRow, "Body", record.bodyFields, lookupContext);
        writeSection(sheet, styles, currentRow, "Foot", record.footFields, lookupContext);
    }

    private int writeSection(Sheet sheet, Styles styles, int currentRow, String sectionName, List<FieldItem> fields, LookupContext lookupContext) {
        Row sectionRow = sheet.createRow(currentRow++);
        fillCells(sectionRow, 0, 8, styles.blockStyle);
        setCell(sectionRow, 0, sectionName, styles.blockStyle);
        merge(sheet, currentRow - 1, currentRow - 1, 0, 8);

        if (fields == null || fields.isEmpty()) {
            return currentRow;
        }

        int order = 1;
        for (FieldItem field : fields) {
            Row row = sheet.createRow(currentRow++);
            ResolvedField resolvedField = resolveField(field, lookupContext);
            setCell(row, 0, field.id, styles.dataStyle);
            setCell(row, 1, String.valueOf(order++), styles.dataStyle);
            setCell(row, 2, resolvedField.displayType, styles.dataStyle);
            setCell(row, 3, resolvedField.displayMaxLength, styles.dataStyle);
            for (int i = 3; i <= 8; i++) {
                if (i == 3 || i == 5 || i == 6) {
                    continue;
                }
                Cell cell = row.createCell(i);
                cell.setCellStyle(styles.dataStyle);
            }
            setCell(row, 5, field.longName, styles.dataStyle);
            setCell(row, 6, resolvedField.enumerationValues, styles.dataStyle);
        }

        return currentRow;
    }

    private LookupContext buildLookupContext() {
        Map<String, UschemaRestrictionValue> uschemaRestrictionMap = new LinkedHashMap<>();
        for (UschemaDetail detail : uschemaDetailMapper.selectList(null)) {
            String key = buildUschemaKey(detail.getUschemaId(), detail.getRestrictionTypeId());
            if (key == null) {
                continue;
            }
            UschemaRestrictionValue value = new UschemaRestrictionValue();
            value.minLength = trim(detail.getRestrictionTypeMinLength());
            value.maxLength = trim(detail.getRestrictionTypeMaxLength());
            value.restrictionType = trim(detail.getRestrictionTypeBase());
            value.fractionDigits = trim(detail.getRestrictionTypeFractionDigits());
            uschemaRestrictionMap.put(key, value);
        }

        Map<String, List<EschemaDetail>> groupedEschemaDetails = eschemaDetailMapper.selectList(null).stream()
                .filter(detail -> buildEschemaKey(detail.getEschemaId(), detail.getRestrictionTypeId()) != null)
                .collect(Collectors.groupingBy(
                        detail -> buildEschemaKey(detail.getEschemaId(), detail.getRestrictionTypeId()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, String> eschemaRestrictionMap = new LinkedHashMap<>();
        groupedEschemaDetails.forEach((key, details) -> {
            String joinedValue = details.stream()
                    .map(this::buildEnumerationItem)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .collect(Collectors.joining("\n"));
            eschemaRestrictionMap.put(key, joinedValue);
        });

        return new LookupContext(uschemaRestrictionMap, eschemaRestrictionMap);
    }

    private ResolvedField resolveField(FieldItem field, LookupContext lookupContext) {
        ResolvedField resolvedField = new ResolvedField();
        resolvedField.displayType = safeValue(field.type);
        resolvedField.displayMaxLength = "";
        resolvedField.enumerationValues = "";

        if (field == null || field.type == null || field.type.isBlank()) {
            return resolvedField;
        }

        TypeRef typeRef = parseTypeRef(field.type);
        if (typeRef == null) {
            return resolvedField;
        }

        if (typeRef.uschema) {
            UschemaRestrictionValue uschemaValue = lookupContext.uschemaRestrictionMap.get(typeRef.lookupKey);
            if (uschemaValue == null) {
                return resolvedField;
            }
            if (!uschemaValue.restrictionType.isBlank()) {
                resolvedField.displayType = uschemaValue.restrictionType;
            }
            resolvedField.displayMaxLength = buildDisplayMaxLength(uschemaValue);
            return resolvedField;
        }

        String enumerationValues = lookupContext.eschemaRestrictionMap.get(typeRef.lookupKey);
        if (enumerationValues == null || enumerationValues.isBlank()) {
            return resolvedField;
        }
        resolvedField.displayType = "string";
        resolvedField.enumerationValues = enumerationValues;
        return resolvedField;
    }

    private String buildDisplayMaxLength(UschemaRestrictionValue value) {
        if (value == null || value.maxLength.isBlank()) {
            return "";
        }
        if ("decimal".equalsIgnoreCase(value.restrictionType) && !value.fractionDigits.isBlank()) {
            return "(" + value.maxLength + "，" + value.fractionDigits + ")";
        }
        return value.maxLength;
    }

    private TypeRef parseTypeRef(String rawType) {
        String value = trim(rawType);
        int dotIndex = value.indexOf('.');
        if (dotIndex <= 0 || dotIndex >= value.length() - 1) {
            return null;
        }

        String schemaId = trim(value.substring(0, dotIndex));
        String restrictionTypeId = trim(value.substring(dotIndex + 1));
        if (schemaId.isBlank() || restrictionTypeId.isBlank()) {
            return null;
        }

        if (restrictionTypeId.startsWith("U_")) {
            return new TypeRef(true, buildUschemaKey(schemaId, restrictionTypeId));
        }
        if (restrictionTypeId.startsWith("E_")) {
            return new TypeRef(false, buildEschemaKey(schemaId, restrictionTypeId));
        }
        return null;
    }

    private String buildEnumerationItem(EschemaDetail detail) {
        String enumerationValue = trim(detail.getEnumerationValue());
        String enumerationLongname = trim(detail.getEnumerationLongname());
        if (enumerationValue.isBlank() && enumerationLongname.isBlank()) {
            return "";
        }
        if (enumerationValue.isBlank()) {
            return enumerationLongname;
        }
        if (enumerationLongname.isBlank()) {
            return enumerationValue;
        }
        return enumerationValue + "-" + enumerationLongname;
    }

    private String buildUschemaKey(String schemaId, String restrictionTypeId) {
        if (schemaId == null || schemaId.isBlank() || restrictionTypeId == null || restrictionTypeId.isBlank()) {
            return null;
        }
        return schemaId.trim() + "." + restrictionTypeId.trim();
    }

    private String buildEschemaKey(String schemaId, String restrictionTypeId) {
        if (schemaId == null || schemaId.isBlank() || restrictionTypeId == null || restrictionTypeId.isBlank()) {
            return null;
        }
        return schemaId.trim() + "." + restrictionTypeId.trim();
    }

    private Element firstDirectChild(Element parent, String tagName) {
        NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                if (tagName.equals(element.getTagName())) {
                    return element;
                }
            }
        }
        return null;
    }

    private String scopeLabel(String scope) {
        if (scope == null) {
            return "全领域";
        }
        switch (scope.toLowerCase(Locale.ROOT)) {
            case "deposit":
                return "存款领域";
            case "loan":
                return "贷款领域";
            case "public":
                return "公共领域";
            case "settlement":
                return "结算领域";
            default:
                return "全领域";
        }
    }

    private String domainByProject(String project) {
        switch (project) {
            case "ccbs-dept-impl":
                return "存款领域";
            case "ccbs-loan-impl":
                return "贷款领域";
            case "ccbs-comm-impl":
                return "公共领域";
            case "ccbs-sett-impl":
                return "结算领域";
            default:
                return "未知领域";
        }
    }

    private boolean matchScope(String scope, String domain) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return true;
        }
        switch (scope.toLowerCase(Locale.ROOT)) {
            case "deposit":
                return "存款领域".equals(domain);
            case "loan":
                return "贷款领域".equals(domain);
            case "public":
                return "公共领域".equals(domain);
            case "settlement":
                return "结算领域".equals(domain);
            default:
                return true;
        }
    }

    private String sanitizeSheetName(String rawName) {
        String sanitized = rawName == null ? "sheet" : rawName;
        for (String invalidChar : XML_INVALID_SHEET_CHARS) {
            sanitized = sanitized.replace(invalidChar, "_");
        }
        sanitized = sanitized.trim();
        if (sanitized.isEmpty()) {
            sanitized = "sheet";
        }
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    private String uniqueSheetName(String baseName, Set<String> usedSheetNames) {
        String candidate = baseName;
        int counter = 1;
        while (usedSheetNames.contains(candidate)) {
            String suffix = "_" + counter++;
            int maxBaseLength = Math.max(1, 31 - suffix.length());
            String trimmedBase = baseName.length() > maxBaseLength ? baseName.substring(0, maxBaseLength) : baseName;
            candidate = trimmedBase + suffix;
        }
        usedSheetNames.add(candidate);
        return candidate;
    }

    private String relativizeSafe(Path path) {
        try {
            return CODE_ROOT.relativize(path).toString();
        } catch (IllegalArgumentException e) {
            return path.toString();
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private void setCell(Sheet sheet, int rowIndex, int cellIndex, String value, CellStyle style) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        setCell(row, cellIndex, value, style);
    }

    private void setCell(Row row, int cellIndex, String value, CellStyle style) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void setHyperlinkCell(Row row, int cellIndex, String value, CellStyle style, CreationHelper creationHelper, String sheetName) {
        Cell cell = row.createCell(cellIndex);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
        Hyperlink hyperlink = creationHelper.createHyperlink(HyperlinkType.DOCUMENT);
        hyperlink.setAddress("#'" + sheetName + "'!A1");
        cell.setHyperlink(hyperlink);
    }

    private void fillCells(Row row, int startCellIndex, int endCellIndex, CellStyle style) {
        for (int i = startCellIndex; i <= endCellIndex; i++) {
            Cell cell = row.getCell(i);
            if (cell == null) {
                cell = row.createCell(i);
            }
            cell.setCellStyle(style);
        }
    }

    private void merge(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        CellRangeAddress region = new CellRangeAddress(firstRow, lastRow, firstCol, lastCol);
        sheet.addMergedRegion(region);
        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
    }

    private void enableGridlines(Sheet sheet) {
        sheet.setDisplayGridlines(true);
        sheet.setPrintGridlines(true);
    }

    private static final class BatchTemplateRecord {
        private String domainName;
        private String projectName;
        private String txCode;
        private String txName;
        private String kind;
        private String fileName;
        private String sourcePath;
        private String sheetName;
        private String encoding;
        private String splitor;
        private List<FieldItem> headerFields = new ArrayList<>();
        private List<FieldItem> bodyFields = new ArrayList<>();
        private List<FieldItem> footFields = new ArrayList<>();

        private String getDomainName() {
            return domainName;
        }

        private String getTxCode() {
            return txCode;
        }

        private String getSourcePath() {
            return sourcePath;
        }
    }

    private static final class FieldItem {
        private String id;
        private String longName;
        private String type;
    }

    private static final class LookupContext {
        private final Map<String, UschemaRestrictionValue> uschemaRestrictionMap;
        private final Map<String, String> eschemaRestrictionMap;

        private LookupContext(Map<String, UschemaRestrictionValue> uschemaRestrictionMap,
                              Map<String, String> eschemaRestrictionMap) {
            this.uschemaRestrictionMap = uschemaRestrictionMap;
            this.eschemaRestrictionMap = eschemaRestrictionMap;
        }
    }

    private static final class UschemaRestrictionValue {
        private String minLength = "";
        private String maxLength = "";
        private String restrictionType = "";
        private String fractionDigits = "";
    }

    private static final class TypeRef {
        private final boolean uschema;
        private final String lookupKey;

        private TypeRef(boolean uschema, String lookupKey) {
            this.uschema = uschema;
            this.lookupKey = lookupKey;
        }
    }

    private static final class ResolvedField {
        private String displayType;
        private String displayMaxLength;
        private String enumerationValues;
    }

    private static final class Styles {
        private final CellStyle titleStyle;
        private final CellStyle noteStyle;
        private final CellStyle headerStyle;
        private final CellStyle dataStyle;
        private final CellStyle linkStyle;
        private final CellStyle labelStyle;
        private final CellStyle valueStyle;
        private final CellStyle multilineValueStyle;
        private final CellStyle sectionTitleStyle;
        private final CellStyle blockStyle;

        private Styles(XSSFWorkbook workbook) {
            this.titleStyle = createTitleStyle(workbook);
            this.noteStyle = createNoteStyle(workbook);
            this.headerStyle = createHeaderStyle(workbook);
            this.dataStyle = createDataStyle(workbook);
            this.linkStyle = createLinkStyle(workbook);
            this.labelStyle = createLabelStyle(workbook);
            this.valueStyle = createValueStyle(workbook, false);
            this.multilineValueStyle = createValueStyle(workbook, true);
            this.sectionTitleStyle = createSectionTitleStyle(workbook);
            this.blockStyle = createBlockStyle(workbook);
        }

        private CellStyle createTitleStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(style);
            Font font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 14);
            style.setFont(font);
            return style;
        }

        private CellStyle createNoteStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setWrapText(true);
            style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(style);
            return style;
        }

        private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setWrapText(true);
            setBorder(style);
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            return style;
        }

        private CellStyle createDataStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            setBorder(style);
            return style;
        }

        private CellStyle createLinkStyle(XSSFWorkbook workbook) {
            CellStyle style = createDataStyle(workbook);
            Font font = workbook.createFont();
            font.setUnderline(Font.U_SINGLE);
            font.setColor(IndexedColors.BLUE.getIndex());
            style.setFont(font);
            return style;
        }

        private CellStyle createLabelStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setWrapText(true);
            setBorder(style);
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            return style;
        }

        private CellStyle createValueStyle(XSSFWorkbook workbook, boolean wrapText) {
            CellStyle style = workbook.createCellStyle();
            style.setVerticalAlignment(VerticalAlignment.TOP);
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setWrapText(wrapText);
            setBorder(style);
            return style;
        }

        private CellStyle createSectionTitleStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(style);
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            return style;
        }

        private CellStyle createBlockStyle(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setFillForegroundColor(IndexedColors.LEMON_CHIFFON.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorder(style);
            Font font = workbook.createFont();
            font.setBold(true);
            style.setFont(font);
            return style;
        }

        private void setBorder(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
