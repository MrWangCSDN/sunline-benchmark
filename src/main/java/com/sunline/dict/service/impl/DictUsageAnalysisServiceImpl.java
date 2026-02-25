package com.sunline.dict.service.impl;

import com.sunline.dict.service.DictUsageAnalysisService;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 在途字典使用分析服务实现类
 */
@Service
public class DictUsageAnalysisServiceImpl implements DictUsageAnalysisService {
    
    private static final Logger log = LoggerFactory.getLogger(DictUsageAnalysisServiceImpl.class);
    
    // 分析结果文件存储目录
    private static final String ANALYSIS_OUTPUT_DIR = "dict-analysis-results";
    
    @Override
    public Map<String, Object> analyzeUsage(String codeDirectory, MultipartFile dictFile) throws Exception {
        log.info("==================== 开始分析字典使用情况 ====================");
        log.info("代码目录: {}", codeDirectory);
        log.info("字典文件: {}", dictFile.getOriginalFilename());
        
        // 验证代码目录
        File codeDir = new File(codeDirectory);
        if (!codeDir.exists() || !codeDir.isDirectory()) {
            throw new RuntimeException("代码目录不存在或不是有效目录: " + codeDirectory);
        }
        
        // 保存上传的Excel文件到临时目录
        File tempExcelFile = saveTempFile(dictFile);
        
        try {
            // 读取Excel文件
            Workbook workbook = WorkbookFactory.create(tempExcelFile);
            Sheet sheet = workbook.getSheetAt(0); // 第一个sheet页
            
            log.info("读取Excel文件，sheet名称: {}, 总行数: {}", sheet.getSheetName(), sheet.getPhysicalNumberOfRows());
            
            // 收集所有XML文件
            List<File> xmlFiles = collectXmlFiles(codeDir);
            log.info("找到 {} 个XML文件", xmlFiles.size());
            
            // 分析每一行的字典字段
            int totalFields = 0;
            int usedFields = 0;
            int unusedFields = 0;
            int totalMatches = 0;
            
            // 从第2行开始（跳过表头）
            int lastRowNum = sheet.getLastRowNum();
            for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                
                // 读取J列（索引9，从0开始）的值
                Cell jCell = row.getCell(9);
                if (jCell == null || jCell.getCellType() == CellType.BLANK) {
                    continue;
                }
                
                String fieldName = getCellValueAsString(jCell);
                if (fieldName == null || fieldName.trim().isEmpty()) {
                    continue;
                }
                
                fieldName = fieldName.trim();
                totalFields++;
                
                // 组装MDict表达式：MDict.{首字母大写}.{字段名}
                String mdictExpression = buildMDictExpression(fieldName);
                log.debug("第{}行，字段名: {}, MDict表达式: {}", rowIndex + 1, fieldName, mdictExpression);
                
                // 在XML文件中查找使用情况
                List<MatchResult> matches = searchInXmlFiles(xmlFiles, mdictExpression, codeDirectory);
                
                if (!matches.isEmpty()) {
                    usedFields++;
                    totalMatches += matches.size();
                    log.info("字段 {} 被使用 {} 次", fieldName, matches.size());
                    
                    // 在N列（索引13）写入工程名称
                    Cell nCell = row.createCell(13);
                    String projectNames = matches.stream()
                        .map(MatchResult::getProjectName)
                        .distinct()
                        .collect(Collectors.joining(", "));
                    nCell.setCellValue(projectNames);
                    
                    // 在O列（索引14）写入匹配的文件路径和行数
                    Cell oCell = row.createCell(14);
                    String matchInfo = matches.stream()
                        .map(m -> m.getRelativePath() + " (行" + m.getLineNumber() + ")")
                        .collect(Collectors.joining("\n"));
                    oCell.setCellValue(matchInfo);
                    
                    // 设置自动换行
                    CellStyle wrapStyle = workbook.createCellStyle();
                    wrapStyle.setWrapText(true);
                    wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);
                    oCell.setCellStyle(wrapStyle);
                } else {
                    unusedFields++;
                    log.debug("字段 {} 未被使用", fieldName);
                }
            }
            
            // 设置N列和O列的表头（如果第一行是表头）
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                Cell nHeaderCell = headerRow.createCell(13);
                nHeaderCell.setCellValue("使用工程");
                
                Cell oHeaderCell = headerRow.createCell(14);
                oHeaderCell.setCellValue("使用位置");
                
                // 加粗表头
                CellStyle headerStyle = workbook.createCellStyle();
                Font headerFont = workbook.createFont();
                headerFont.setBold(true);
                headerStyle.setFont(headerFont);
                nHeaderCell.setCellStyle(headerStyle);
                oHeaderCell.setCellStyle(headerStyle);
            }
            
            // 自动调整列宽
            sheet.autoSizeColumn(13);
            sheet.setColumnWidth(14, 15000); // O列设置固定宽度
            
            // 生成输出文件名
            String originalFileName = dictFile.getOriginalFilename();
            String outputFileName = generateOutputFileName(originalFileName);
            
            // 保存新的Excel文件
            saveAnalysisResult(workbook, outputFileName);
            
            // 关闭workbook
            workbook.close();
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("totalFields", totalFields);
            result.put("usedFields", usedFields);
            result.put("unusedFields", unusedFields);
            result.put("totalMatches", totalMatches);
            result.put("outputFileName", outputFileName);
            result.put("downloadUrl", "/api/dict-usage/download/" + outputFileName);
            
            log.info("==================== 分析完成 ====================");
            log.info("总字段数: {}, 已使用: {}, 未使用: {}, 匹配总数: {}", 
                totalFields, usedFields, unusedFields, totalMatches);
            
            return result;
            
        } finally {
            // 删除临时Excel文件
            if (tempExcelFile.exists()) {
                tempExcelFile.delete();
            }
        }
    }
    
    @Override
    public File getAnalysisResultFile(String fileName) {
        File outputDir = new File(ANALYSIS_OUTPUT_DIR);
        return new File(outputDir, fileName);
    }
    
    /**
     * 保存上传的文件到临时目录
     */
    private File saveTempFile(MultipartFile file) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        File tempFile = new File(tempDir, "dict_" + System.currentTimeMillis() + "_" + file.getOriginalFilename());
        file.transferTo(tempFile);
        return tempFile;
    }
    
    /**
     * 收集目录下所有的XML文件
     */
    private List<File> collectXmlFiles(File directory) {
        List<File> xmlFiles = new ArrayList<>();
        
        try (Stream<Path> paths = Files.walk(Paths.get(directory.getAbsolutePath()))) {
            xmlFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                .map(Path::toFile)
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("遍历目录失败", e);
        }
        
        return xmlFiles;
    }
    
    /**
     * 构建MDict表达式
     * 规则：MDict.{首字母大写}.{字段名}
     * 例如：acLvlFld -> MDict.A.acLvlFld
     */
    private String buildMDictExpression(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return "";
        }
        
        // 获取首字母并转大写
        String firstLetter = fieldName.substring(0, 1).toUpperCase();
        
        return "MDict." + firstLetter + "." + fieldName;
    }
    
    /**
     * 在XML文件中搜索MDict表达式
     */
    private List<MatchResult> searchInXmlFiles(List<File> xmlFiles, String mdictExpression, String baseDirectory) {
        List<MatchResult> matches = new ArrayList<>();
        
        // 转义特殊字符用于正则表达式
        String escapedExpression = Pattern.quote(mdictExpression);
        Pattern pattern = Pattern.compile(escapedExpression);
        
        for (File xmlFile : xmlFiles) {
            try {
                // 提取工程名称（假设工程名是baseDirectory后的第一级目录）
                String relativePath = xmlFile.getAbsolutePath().substring(baseDirectory.length());
                if (relativePath.startsWith(File.separator)) {
                    relativePath = relativePath.substring(1);
                }
                
                String projectName = extractProjectName(relativePath);
                
                // 读取文件内容并搜索
                List<String> lines = Files.readAllLines(xmlFile.toPath(), StandardCharsets.UTF_8);
                
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (pattern.matcher(line).find()) {
                        MatchResult match = new MatchResult();
                        match.setProjectName(projectName);
                        match.setFilePath(xmlFile.getAbsolutePath());
                        match.setRelativePath(relativePath);
                        match.setLineNumber(i + 1);
                        match.setLineContent(line.trim());
                        matches.add(match);
                        
                        log.debug("找到匹配: {} 在 {} 第{}行", mdictExpression, relativePath, i + 1);
                    }
                }
            } catch (IOException e) {
                log.error("读取文件失败: {}", xmlFile.getAbsolutePath(), e);
            }
        }
        
        return matches;
    }
    
    /**
     * 从相对路径中提取工程名称
     */
    private String extractProjectName(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return "未知工程";
        }
        
        // 获取第一级目录作为工程名
        int firstSeparator = relativePath.indexOf(File.separator);
        if (firstSeparator > 0) {
            return relativePath.substring(0, firstSeparator);
        }
        
        return "根目录";
    }
    
    /**
     * 生成输出文件名
     * 规则：原文件名-分析版.xlsx
     */
    private String generateOutputFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isEmpty()) {
            originalFileName = "字典文件";
        }
        
        // 移除扩展名
        int lastDot = originalFileName.lastIndexOf('.');
        String nameWithoutExt = lastDot > 0 ? originalFileName.substring(0, lastDot) : originalFileName;
        
        // 添加时间戳避免文件名冲突
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        
        return nameWithoutExt + "-分析版_" + timestamp + ".xlsx";
    }
    
    /**
     * 保存分析结果到文件
     */
    private File saveAnalysisResult(Workbook workbook, String fileName) throws IOException {
        // 创建输出目录
        File outputDir = new File(ANALYSIS_OUTPUT_DIR);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        File outputFile = new File(outputDir, fileName);
        
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            workbook.write(fos);
        }
        
        log.info("分析结果已保存到: {}", outputFile.getAbsolutePath());
        return outputFile;
    }
    
    /**
     * 获取单元格值为字符串
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
    
    /**
     * 匹配结果内部类
     */
    private static class MatchResult {
        private String projectName;      // 工程名称
        private String filePath;          // 文件绝对路径
        private String relativePath;      // 文件相对路径
        private int lineNumber;           // 行号
        private String lineContent;       // 行内容
        
        public String getProjectName() {
            return projectName;
        }
        
        public void setProjectName(String projectName) {
            this.projectName = projectName;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }
        
        public String getRelativePath() {
            return relativePath;
        }
        
        public void setRelativePath(String relativePath) {
            this.relativePath = relativePath;
        }
        
        public int getLineNumber() {
            return lineNumber;
        }
        
        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
        
        public String getLineContent() {
            return lineContent;
        }
        
        public void setLineContent(String lineContent) {
            this.lineContent = lineContent;
        }
    }
}

