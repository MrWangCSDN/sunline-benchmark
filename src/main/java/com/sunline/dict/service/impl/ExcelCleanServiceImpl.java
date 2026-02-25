package com.sunline.dict.service.impl;

import com.sunline.dict.service.ExcelCleanService;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Excel数据清洗服务实现
 */
@Service
public class ExcelCleanServiceImpl implements ExcelCleanService {
    
    private static final Logger log = LoggerFactory.getLogger(ExcelCleanServiceImpl.class);
    
    private static final String RESULT_DIR = "excel_clean_results";
    private static final int COLUMN_TABLE_NAME = 1; // B列索引（从0开始）
    
    @Override
    public Map<String, Object> cleanExcelFile(MultipartFile excelFile, String sheetNames, String renameRules) throws Exception {
        log.info("开始清洗Excel文件");
        
        // 创建结果目录
        File resultDir = new File(RESULT_DIR);
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }
        
        // 解析保留的Sheet页名称（忽略大小写）
        Set<String> keepSheetNames = new HashSet<>();
        if (sheetNames != null && !sheetNames.trim().isEmpty()) {
            for (String name : sheetNames.split("\n")) {
                String trimmed = name.trim();
                if (!trimmed.isEmpty()) {
                    keepSheetNames.add(trimmed.toLowerCase());
                }
            }
        }
        
        // 解析sheet页名称替换规则
        Map<String, String> renameMap = new LinkedHashMap<>();
        if (renameRules != null && !renameRules.trim().isEmpty()) {
            for (String rule : renameRules.split("\n")) {
                String trimmed = rule.trim();
                if (!trimmed.isEmpty() && trimmed.contains("-->")) {
                    String[] parts = trimmed.split("-->", 2);
                    if (parts.length == 2) {
                        String oldName = parts[0].trim();
                        String newName = parts[1].trim();
                        if (!oldName.isEmpty() && !newName.isEmpty()) {
                            renameMap.put(oldName, newName);
                        }
                    }
                }
            }
        }
        
        log.info("要保留的Sheet页数量: {}, Sheet页重命名规则数量: {}", keepSheetNames.size(), renameMap.size());
        
        // 读取Excel文件
        Workbook workbook = WorkbookFactory.create(excelFile.getInputStream());
        
        try {
            int originalSheetCount = workbook.getNumberOfSheets();
            int deletedSheetCount = 0;
            int deletedRowCount = 0;
            int renamedSheetCount = 0;
            
            // 获取第一个sheet页（总览表）
            String firstSheetName = workbook.getSheetAt(0).getSheetName();
            log.info("第一个Sheet页（总览表）: {}", firstSheetName);
            
            // 1. 删除不在保留列表中的sheet页（从后往前删除，避免索引变化）
            // 只有当keepSheetNames不为空时才执行保留功能
            if (!keepSheetNames.isEmpty()) {
                for (int i = workbook.getNumberOfSheets() - 1; i >= 0; i--) {
                    Sheet sheet = workbook.getSheetAt(i);
                    String sheetName = sheet.getSheetName();
                    
                    // 跳过第一个sheet页（总览表）
                    if (i == 0) {
                        log.info("保留第一个Sheet页（总览表）: {}", sheetName);
                        continue;
                    }
                    
                    // 检查是否在保留列表中（忽略大小写）
                    if (!keepSheetNames.contains(sheetName.toLowerCase())) {
                        log.info("删除Sheet页: {}", sheetName);
                        workbook.removeSheetAt(i);
                        deletedSheetCount++;
                    }
                }
                
                // 2. 清洗总览表（删除B列不在保留列表中的行）
                Sheet overviewSheet = workbook.getSheetAt(0);
                deletedRowCount = cleanOverviewSheet(overviewSheet, keepSheetNames);
                
                // 3. 修复总览表B列的超链接
                fixOverviewHyperlinks(workbook, overviewSheet);
                
                // 4. 修复各个sheet页D列指向总览表的反向超链接
                fixBackLinksToOverview(workbook, overviewSheet);
            }
            
            // 5. 替换sheet页名称（在保留功能之后执行）
            if (!renameMap.isEmpty()) {
                renamedSheetCount = renameSheets(workbook, renameMap);
            }
            
            // 保存结果文件
            String fileName = generateFileName(excelFile.getOriginalFilename());
            File resultFile = new File(resultDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                workbook.write(fos);
            }
            
            log.info("清洗完成，结果文件: {}", fileName);
            
            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("originalSheetCount", originalSheetCount);
            result.put("deletedSheetCount", deletedSheetCount);
            result.put("renamedSheetCount", renamedSheetCount);
            result.put("remainingSheetCount", workbook.getNumberOfSheets());
            result.put("deletedRowCount", deletedRowCount);
            
            return result;
            
        } finally {
            workbook.close();
        }
    }
    
    /**
     * 替换sheet页名称（忽略大小写匹配）
     * @return 替换的sheet页数量
     */
    private int renameSheets(Workbook workbook, Map<String, String> renameMap) {
        int renamedCount = 0;
        
        // 构建忽略大小写的映射（key小写 -> 新名称）
        Map<String, String> lowerCaseRenameMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : renameMap.entrySet()) {
            lowerCaseRenameMap.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        
        // 遍历所有sheet，根据重命名规则修改名称
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String oldName = sheet.getSheetName();
            String oldNameLower = oldName.toLowerCase();
            
            // 检查是否需要重命名（忽略大小写）
            if (lowerCaseRenameMap.containsKey(oldNameLower)) {
                String newName = lowerCaseRenameMap.get(oldNameLower);
                
                // 检查新名称是否已存在
                if (workbook.getSheet(newName) != null && !newName.equals(oldName)) {
                    log.warn("Sheet页名称 {} 已存在，跳过重命名: {} --> {}", newName, oldName, newName);
                    continue;
                }
                
                try {
                    workbook.setSheetName(i, newName);
                    log.info("重命名Sheet页（忽略大小写匹配）: {} --> {}", oldName, newName);
                    renamedCount++;
                    
                    // 更新总览表B列的内容
                    updateOverviewTableName(workbook, oldName, newName);
                } catch (Exception e) {
                    log.error("重命名Sheet页失败: {} --> {}", oldName, newName, e);
                }
            }
        }
        
        // 重命名后，修复所有超链接
        if (renamedCount > 0) {
            Sheet overviewSheet = workbook.getSheetAt(0);
            fixOverviewHyperlinks(workbook, overviewSheet);
            fixBackLinksToOverview(workbook, overviewSheet);
            log.info("重命名 {} 个Sheet页，超链接已更新", renamedCount);
        }
        
        return renamedCount;
    }
    
    /**
     * 更新总览表中的表名（B列）
     */
    private void updateOverviewTableName(Workbook workbook, String oldName, String newName) {
        Sheet overviewSheet = workbook.getSheetAt(0);
        
        // 遍历总览表，查找并更新B列中的旧名称
        for (int i = 1; i <= overviewSheet.getLastRowNum(); i++) {
            Row row = overviewSheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(COLUMN_TABLE_NAME);
                if (cell != null) {
                    String tableName = getCellValueAsString(cell);
                    if (tableName != null && tableName.trim().equals(oldName)) {
                        cell.setCellValue(newName);
                        log.debug("更新总览表B列: {} --> {}", oldName, newName);
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * 清洗总览表（删除B列不在保留列表中的行）
     */
    private int cleanOverviewSheet(Sheet sheet, Set<String> keepSheetNames) {
        // 如果keepSheetNames为空，跳过清洗
        if (keepSheetNames.isEmpty()) {
            log.info("未配置保留Sheet页，跳过总览表清洗");
            return 0;
        }
        int deletedCount = 0;
        
        // 从后往前遍历（避免删除行后索引变化）
        for (int i = sheet.getLastRowNum(); i > 0; i--) { // 跳过第一行（表头，索引0）
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(COLUMN_TABLE_NAME);
                if (cell != null) {
                    String tableName = getCellValueAsString(cell);
                    if (tableName != null && !tableName.trim().isEmpty()) {
                        // 检查是否在保留列表中（忽略大小写）
                        if (!keepSheetNames.contains(tableName.trim().toLowerCase())) {
                            log.debug("删除总览表行: {} (行号: {})", tableName, i + 1);
                            sheet.removeRow(row);
                            
                            // 将下面的行向上移动
                            if (i < sheet.getLastRowNum()) {
                                sheet.shiftRows(i + 1, sheet.getLastRowNum(), -1);
                            }
                            
                            deletedCount++;
                        }
                    }
                }
            }
        }
        
        log.info("总览表删除 {} 行", deletedCount);
        return deletedCount;
    }
    
    /**
     * 修复总览表B列的超链接
     */
    private void fixOverviewHyperlinks(Workbook workbook, Sheet overviewSheet) {
        log.info("开始修复总览表超链接");
        
        int fixedCount = 0;
        int notFoundCount = 0;
        
        // 从第二行开始（跳过表头）
        for (int i = 1; i <= overviewSheet.getLastRowNum(); i++) {
            Row row = overviewSheet.getRow(i);
            if (row == null) continue;
            
            Cell cell = row.getCell(COLUMN_TABLE_NAME);
            if (cell == null) continue;
            
            String tableName = getCellValueAsString(cell);
            if (tableName == null || tableName.trim().isEmpty()) continue;
            
            tableName = tableName.trim();
            
            // 检查该sheet是否存在（忽略大小写查找）
            Sheet targetSheet = findSheetIgnoreCase(workbook, tableName);
            
            if (targetSheet != null) {
                // 清除旧的超链接
                cell.removeHyperlink();
                
                // 创建新的超链接
                Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
                
                // 设置超链接地址（使用实际的sheet名称）
                String actualSheetName = targetSheet.getSheetName();
                String address = "'" + actualSheetName + "'!A1";
                link.setAddress(address);
                link.setLabel(actualSheetName);
                cell.setHyperlink(link);
                
                // 设置超链接样式（蓝色下划线）
                CellStyle originalStyle = cell.getCellStyle();
                CellStyle linkStyle = workbook.createCellStyle();
                
                // 复制原有样式
                if (originalStyle != null) {
                    try {
                        linkStyle.cloneStyleFrom(originalStyle);
                    } catch (Exception e) {
                        log.debug("样式复制失败，使用默认样式");
                    }
                }
                
                // 创建超链接字体
                Font linkFont = workbook.createFont();
                if (originalStyle != null) {
                    Font originalFont = workbook.getFontAt(originalStyle.getFontIndex());
                    linkFont.setFontName(originalFont.getFontName());
                    linkFont.setFontHeightInPoints(originalFont.getFontHeightInPoints());
                    linkFont.setBold(originalFont.getBold());
                }
                linkFont.setColor(IndexedColors.BLUE.getIndex());
                linkFont.setUnderline(Font.U_SINGLE);
                linkStyle.setFont(linkFont);
                
                cell.setCellStyle(linkStyle);
                
                fixedCount++;
                log.debug("修复超链接: {} -> {}", tableName, address);
            } else {
                notFoundCount++;
                log.warn("Sheet不存在，无法创建超链接: {}", tableName);
            }
        }
        
        log.info("修复总览表超链接完成，成功 {} 个，失败 {} 个", fixedCount, notFoundCount);
    }
    
    /**
     * 忽略大小写查找sheet
     */
    private Sheet findSheetIgnoreCase(Workbook workbook, String sheetName) {
        // 先尝试精确匹配
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet != null) {
            return sheet;
        }
        
        // 再尝试忽略大小写匹配
        String lowerName = sheetName.toLowerCase();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet s = workbook.getSheetAt(i);
            if (s.getSheetName().toLowerCase().equals(lowerName)) {
                return s;
            }
        }
        
        return null;
    }
    
    /**
     * 修复各个sheet页中指向总览表的反向超链接
     */
    private void fixBackLinksToOverview(Workbook workbook, Sheet overviewSheet) {
        log.info("开始修复反向超链接");
        
        // 先构建表名到总览表行号的映射
        Map<String, Integer> tableRowMap = new HashMap<>();
        for (int i = 1; i <= overviewSheet.getLastRowNum(); i++) {
            Row row = overviewSheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(COLUMN_TABLE_NAME);
                if (cell != null) {
                    String tableName = getCellValueAsString(cell);
                    if (tableName != null && !tableName.trim().isEmpty()) {
                        tableRowMap.put(tableName.trim().toLowerCase(), i);
                    }
                }
            }
        }
        
        int fixedCount = 0;
        
        // 遍历所有sheet（除了总览表）
        for (int i = 1; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            
            // 查找该表在总览中的行号
            Integer overviewRowIndex = tableRowMap.get(sheetName.toLowerCase());
            if (overviewRowIndex == null) {
                log.debug("Sheet {} 在总览表中未找到", sheetName);
                continue;
            }
            
            // 遍历该sheet的所有行，查找D列（索引3）的超链接
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                
                // 检查D列及其他可能包含反向超链接的列
                for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                    Cell cell = row.getCell(colIndex);
                    if (cell == null) continue;
                    
                    Hyperlink link = cell.getHyperlink();
                    if (link != null && link.getType() == HyperlinkType.DOCUMENT) {
                        String address = link.getAddress();
                        
                        // 检查是否是指向总览表的超链接
                        if (address != null && 
                            (address.contains("总览") || address.contains("overview") || 
                             address.toLowerCase().contains("'表总览'"))) {
                            
                            // 清除旧超链接
                            cell.removeHyperlink();
                            
                            // 创建新的超链接，指向总览表的正确行
                            Hyperlink newLink = workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
                            String overviewSheetName = overviewSheet.getSheetName();
                            String newAddress = "'" + overviewSheetName + "'!B" + (overviewRowIndex + 1);
                            newLink.setAddress(newAddress);
                            newLink.setLabel("返回总览");
                            cell.setHyperlink(newLink);
                            
                            // 保留或设置超链接样式
                            CellStyle originalStyle = cell.getCellStyle();
                            CellStyle linkStyle = workbook.createCellStyle();
                            
                            if (originalStyle != null) {
                                try {
                                    linkStyle.cloneStyleFrom(originalStyle);
                                } catch (Exception e) {
                                    log.debug("样式复制失败");
                                }
                            }
                            
                            Font linkFont = workbook.createFont();
                            if (originalStyle != null) {
                                Font originalFont = workbook.getFontAt(originalStyle.getFontIndex());
                                linkFont.setFontName(originalFont.getFontName());
                                linkFont.setFontHeightInPoints(originalFont.getFontHeightInPoints());
                                linkFont.setBold(originalFont.getBold());
                            }
                            linkFont.setColor(IndexedColors.BLUE.getIndex());
                            linkFont.setUnderline(Font.U_SINGLE);
                            linkStyle.setFont(linkFont);
                            
                            cell.setCellStyle(linkStyle);
                            
                            fixedCount++;
                            log.debug("修复反向超链接: Sheet={}, Cell={}{} -> {}", 
                                    sheetName, getColumnName(colIndex), rowIndex + 1, newAddress);
                        }
                    }
                }
            }
        }
        
        log.info("修复反向超链接完成，共 {} 个", fixedCount);
    }
    
    /**
     * 获取列名（A, B, C, ..., AA, AB, ...）
     */
    private String getColumnName(int columnIndex) {
        StringBuilder columnName = new StringBuilder();
        while (columnIndex >= 0) {
            columnName.insert(0, (char) ('A' + (columnIndex % 26)));
            columnIndex = (columnIndex / 26) - 1;
        }
        return columnName.toString();
    }
    
    /**
     * 获取单元格值（字符串）
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double value = cell.getNumericCellValue();
                    if (value == (long) value) {
                        return String.valueOf((long) value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ex) {
                        return cell.getCellFormula();
                    }
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
    
    /**
     * 生成文件名
     */
    private String generateFileName(String originalFileName) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String extension = originalFileName.substring(originalFileName.lastIndexOf('.'));
        return baseName + "_清洗版_" + timestamp + extension;
    }
    
    @Override
    public File getResultFile(String fileName) {
        return new File(RESULT_DIR, fileName);
    }
}

