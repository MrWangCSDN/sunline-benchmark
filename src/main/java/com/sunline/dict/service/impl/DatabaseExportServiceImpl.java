package com.sunline.dict.service.impl;

import com.sunline.dict.config.DatabaseExportConfig;
import com.sunline.dict.service.DatabaseExportService;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.Comparator;

/**
 * 数据库导出服务实现
 */
@Service
public class DatabaseExportServiceImpl implements DatabaseExportService {
    
    private static final Logger log = LoggerFactory.getLogger(DatabaseExportServiceImpl.class);
    
    private static final String RESULT_DIR = "db_export_results";
    private static final String GROUP_CONFIG_DIR = "db_group_configs";
    private static final List<String> ALL_GROUPS = Arrays.asList("deposit", "loan", "public", "settlement", "platform");
    
    @Autowired
    private DatabaseExportConfig databaseExportConfig;
    
    
    /**
     * 表信息类
     */
    private static class TableInfo {
        String tableName;
        String remarks;
        
        TableInfo(String tableName, String remarks) {
            this.tableName = tableName;
            this.remarks = remarks;
        }
        
        String getDisplayName() {
            // 使用表名作为sheet名称
            if (tableName == null || tableName.isEmpty()) {
                return "unknown";
            }
            
            String displayName = tableName;
            
            // 首字母大写
            displayName = displayName.substring(0, 1).toUpperCase() + displayName.substring(1);
            
            // 限制sheet名称长度（Excel最大31个字符）
            // 如果超过30位，截取前28位+后2位
            if (displayName.length() > 30) {
                String prefix = displayName.substring(0, 28);
                String suffix = displayName.substring(displayName.length() - 2);
                displayName = prefix + suffix;
            }
            
            return displayName;
        }
    }
    
    /**
     * 获取所有表信息（包括表名和描述）
     */
    private List<TableInfo> getAllTables(Connection conn) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        
        DatabaseMetaData metaData = conn.getMetaData();
        ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"});
        
        while (rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            String remarks = rs.getString("REMARKS");
            
            // 过滤系统表
            if (!tableName.toLowerCase().startsWith("pg_") && 
                !tableName.toLowerCase().startsWith("sql_")) {
                tables.add(new TableInfo(tableName, remarks));
            }
        }
        rs.close();
        
        // 按表名排序
        tables.sort(Comparator.comparing(t -> t.tableName));
        return tables;
    }
    
    /**
     * 创建总览sheet（包含未找到的表）
     */
    private void createOverviewSheet(Sheet sheet, List<TableInfo> tables, Set<String> notFoundTables) {
        Workbook workbook = sheet.getWorkbook();
        
        // 创建带边框的样式
        CellStyle borderStyle = createBorderStyle(workbook);
        CellStyle linkStyle = createLinkStyle(workbook);
        
        // 创建未找到表的样式（灰色背景）
        CellStyle notFoundStyle = workbook.createCellStyle();
        notFoundStyle.setBorderTop(BorderStyle.THIN);
        notFoundStyle.setBorderBottom(BorderStyle.THIN);
        notFoundStyle.setBorderLeft(BorderStyle.THIN);
        notFoundStyle.setBorderRight(BorderStyle.THIN);
        notFoundStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        notFoundStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // 创建标题行
        Row headerRow = sheet.createRow(0);
        createCellWithStyle(headerRow, 0, "类别", borderStyle);
        createCellWithStyle(headerRow, 1, "表英文名", borderStyle);
        createCellWithStyle(headerRow, 2, "表中文名", borderStyle);
        
        int rowNum = 1;
        
        // 创建数据行（数据库中存在的表）
        for (TableInfo tableInfo : tables) {
            Row row = sheet.createRow(rowNum++);
            
            String chineseName = (tableInfo.remarks != null && !tableInfo.remarks.trim().isEmpty()) 
                ? tableInfo.remarks.trim() : tableInfo.tableName;
            
            // A列：类别（表中文名）
            createCellWithStyle(row, 0, chineseName, borderStyle);
            
            // B列：表英文名（带超链接）
            Cell nameCell = createCellWithStyle(row, 1, tableInfo.tableName, linkStyle);
            
            // 创建超链接到对应的sheet
            String sheetName = tableInfo.getDisplayName();
            Hyperlink link = workbook.getCreationHelper()
                    .createHyperlink(HyperlinkType.DOCUMENT);
            link.setAddress("'" + sheetName + "'!A1");
            nameCell.setHyperlink(link);
            
            // C列：表中文名
            createCellWithStyle(row, 2, chineseName, borderStyle);
        }
        
        // 添加未找到的表（灰色背景标记）
        if (notFoundTables != null && !notFoundTables.isEmpty()) {
            for (String notFoundTable : notFoundTables) {
                Row row = sheet.createRow(rowNum++);
                
                // A列：类别（空）
                createCellWithStyle(row, 0, "", notFoundStyle);
                
                // B列：表英文名（未找到的表）
                createCellWithStyle(row, 1, notFoundTable, notFoundStyle);
                
                // C列：标记为数据库中不存在
                createCellWithStyle(row, 2, "数据库中不存在", notFoundStyle);
            }
            
            log.info("表总览中添加了 {} 个未找到的表", notFoundTables.size());
        }
        
        // 设置列宽
        sheet.setColumnWidth(0, 8000);   // 类别
        sheet.setColumnWidth(1, 8000);   // 表英文名
        sheet.setColumnWidth(2, 8000);   // 表中文名
    }
    
    /**
     * 为单个表创建sheet
     */
    private void createTableSheet(Workbook workbook, Connection conn, TableInfo tableInfo) throws SQLException {
        String tableName = tableInfo.tableName;
        String sheetName = tableInfo.getDisplayName();
        String chineseName = (tableInfo.remarks != null && !tableInfo.remarks.trim().isEmpty()) 
            ? tableInfo.remarks.trim() : tableName;
        
        log.debug("创建表sheet: {} (显示名称: {})", tableName, sheetName);
        
        Sheet sheet = workbook.createSheet(sheetName);
        
        // 创建带边框的样式
        CellStyle borderStyle = createBorderStyle(workbook);
        
        // 第1行：表基本信息
        Row row1 = sheet.createRow(0);
        createCellWithStyle(row1, 0, "表中文名称", borderStyle);
        createCellWithStyle(row1, 1, chineseName, borderStyle);
        createCellWithStyle(row1, 2, "表ID", borderStyle);
        createCellWithStyle(row1, 3, tableName, borderStyle);
        createCellWithStyle(row1, 4, "分区键", borderStyle);
        createCellWithStyle(row1, 5, "", borderStyle);
        createCellWithStyle(row1, 6, "业务类别", borderStyle);
        createCellWithStyle(row1, 7, "", borderStyle);
        createCellWithStyle(row1, 8, "", borderStyle);
        createCellWithStyle(row1, 9, "", borderStyle);
        createCellWithStyle(row1, 10, "", borderStyle);
        
        // 第2行：表描述
        Row row2 = sheet.createRow(1);
        createCellWithStyle(row2, 0, "表描述", borderStyle);
        for (int i = 1; i <= 10; i++) {
            createCellWithStyle(row2, i, "", borderStyle);
        }
        
        // 第3行：字段表头
        Row row3 = sheet.createRow(2);
        createCellWithStyle(row3, 0, "字段名", borderStyle);
        createCellWithStyle(row3, 1, "中文名", borderStyle);
        createCellWithStyle(row3, 2, "字段类型", borderStyle);
        createCellWithStyle(row3, 3, "数据库类型", borderStyle);
        createCellWithStyle(row3, 4, "元数据类型", borderStyle);
        createCellWithStyle(row3, 5, "空值", borderStyle);
        createCellWithStyle(row3, 6, "默认值", borderStyle);
        createCellWithStyle(row3, 7, "下拉列表", borderStyle);
        createCellWithStyle(row3, 8, "字段说明", borderStyle);
        createCellWithStyle(row3, 9, "是否贯标", borderStyle);
        
        // 获取表的列信息
        DatabaseMetaData metaData = conn.getMetaData();
        ResultSet columns = metaData.getColumns(null, null, tableName, "%");
        
        // 获取主键和索引信息
        Set<String> primaryKeys = getPrimaryKeys(metaData, tableName);
        List<IndexInfo> indexes = getIndexes(metaData, tableName, primaryKeys);
        
        // 第4行开始：字段数据
        int rowNum = 3;
        while (columns.next()) {
            Row row = sheet.createRow(rowNum++);
            
            String columnName = columns.getString("COLUMN_NAME");
            String dataType = columns.getString("TYPE_NAME");
            int columnSize = columns.getInt("COLUMN_SIZE");
            String nullable = columns.getString("IS_NULLABLE");
            String defaultValue = columns.getString("COLUMN_DEF");
            String columnRemarks = columns.getString("REMARKS");
            
            // A列：字段名
            createCellWithStyle(row, 0, columnName, borderStyle);
            
            // B列：字段中文名称（从REMARKS获取）
            createCellWithStyle(row, 1, columnRemarks != null ? columnRemarks : "", borderStyle);
            
            // C列：字段类型（空）
            createCellWithStyle(row, 2, "", borderStyle);
            
            // D列：数据库类型（转换并合并长度）
            String dbType = convertDataType(dataType);
            if (columnSize > 0 && needsLength(dbType)) {
                dbType = dbType + "(" + columnSize + ")";
            }
            createCellWithStyle(row, 3, dbType, borderStyle);
            
            // E列：元数据类型（空）
            createCellWithStyle(row, 4, "", borderStyle);
            
            // F列：空值（Y/N）
            createCellWithStyle(row, 5, "YES".equals(nullable) ? "Y" : "N", borderStyle);
            
            // G列：默认值
            createCellWithStyle(row, 6, defaultValue != null ? defaultValue : "", borderStyle);
            
            // H列：下拉列表（空）
            createCellWithStyle(row, 7, "", borderStyle);
            
            // I列：字段描述（空）
            createCellWithStyle(row, 8, "", borderStyle);
            
            // J列：是否贯标（空）
            createCellWithStyle(row, 9, "", borderStyle);
        }
        columns.close();
        
        // 字段结束后：索引部分
        Row indexHeaderRow = sheet.createRow(rowNum++);
        createCellWithStyle(indexHeaderRow, 0, "索引ID", borderStyle);
        createCellWithStyle(indexHeaderRow, 1, "字段", borderStyle);
        createCellWithStyle(indexHeaderRow, 2, "索引类型", borderStyle);
        for (int i = 3; i < 10; i++) {
            createCellWithStyle(indexHeaderRow, i, "", borderStyle);
        }
        
        // 索引数据（不包括primarykey）
        for (IndexInfo index : indexes) {
            if (!"primarykey".equalsIgnoreCase(index.type)) {
                Row indexRow = sheet.createRow(rowNum++);
                createCellWithStyle(indexRow, 0, index.indexName, borderStyle);
                createCellWithStyle(indexRow, 1, index.columnName, borderStyle);
                createCellWithStyle(indexRow, 2, index.type, borderStyle);
                for (int i = 3; i < 10; i++) {
                    createCellWithStyle(indexRow, i, "", borderStyle);
                }
            }
        }
        
        // 主键部分
        Row pkHeaderRow = sheet.createRow(rowNum++);
        createCellWithStyle(pkHeaderRow, 0, "主键", borderStyle);
        createCellWithStyle(pkHeaderRow, 1, "字段", borderStyle);
        for (int i = 2; i < 10; i++) {
            createCellWithStyle(pkHeaderRow, i, "", borderStyle);
        }
        
        // 主键数据（获取真正的主键名称，多个主键用逗号分隔）
        if (!primaryKeys.isEmpty()) {
            Row pkRow = sheet.createRow(rowNum++);
            
            // 获取主键约束名称
            String pkName = getPrimaryKeyName(metaData, tableName);
            createCellWithStyle(pkRow, 0, pkName.toLowerCase(), borderStyle);  // 小写
            createCellWithStyle(pkRow, 1, String.join(",", primaryKeys), borderStyle);
            for (int i = 2; i < 10; i++) {
                createCellWithStyle(pkRow, i, "", borderStyle);
            }
        }
        
        // 设置列宽
        sheet.setColumnWidth(0, 4000);  // A列
        sheet.setColumnWidth(1, 4000);  // B列
        sheet.setColumnWidth(2, 3000);  // C列
        sheet.setColumnWidth(3, 4000);  // D列
        sheet.setColumnWidth(4, 3000);  // E列
        sheet.setColumnWidth(5, 2000);  // F列
        sheet.setColumnWidth(6, 4000);  // G列
        sheet.setColumnWidth(7, 3000);  // H列
        sheet.setColumnWidth(8, 6000);  // I列
        sheet.setColumnWidth(9, 3000);  // J列
    }
    
    /**
     * 转换数据库类型
     */
    private String convertDataType(String dataType) {
        if (dataType == null) {
            return "";
        }
        
        // GaussDB类型转换为标准类型
        switch (dataType.toLowerCase()) {
            case "bpchar":
                return "char";
            case "int1":
                return "tinyint";
            case "int2":
                return "smallint";
            case "int4":
                return "integer";
            case "int8":
                return "bigint";
            default:
                return dataType;
        }
    }
    
    /**
     * 判断数据类型是否需要显示长度
     */
    private boolean needsLength(String dataType) {
        String lowerType = dataType.toLowerCase();
        return lowerType.contains("char") || lowerType.contains("varchar") ||
               lowerType.contains("binary") || lowerType.contains("varbinary");
    }
    
    /**
     * 索引信息类
     */
    private static class IndexInfo {
        String indexName;
        String columnName;
        String type;  // index / unique
        
        IndexInfo(String indexName, String columnName, String type) {
            this.indexName = indexName;
            this.columnName = columnName;
            this.type = type;
        }
    }
    
    /**
     * 获取索引信息（不包括主键）
     */
    private List<IndexInfo> getIndexes(DatabaseMetaData metaData, String tableName, Set<String> primaryKeys) throws SQLException {
        List<IndexInfo> indexes = new ArrayList<>();
        ResultSet rs = metaData.getIndexInfo(null, null, tableName, false, false);
        
        while (rs.next()) {
            String indexName = rs.getString("INDEX_NAME");
            String columnName = rs.getString("COLUMN_NAME");
            boolean nonUnique = rs.getBoolean("NON_UNIQUE");
            
            // 跳过主键索引
            if (columnName != null && !primaryKeys.contains(columnName)) {
                String indexType = nonUnique ? "index" : "unique";
                indexes.add(new IndexInfo(indexName, columnName, indexType));
            }
        }
        rs.close();
        
        return indexes;
    }
    
    /**
     * 创建带边框的单元格样式
     */
    private CellStyle createBorderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * 创建超链接样式（带边框）
     */
    private CellStyle createLinkStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        Font linkFont = workbook.createFont();
        linkFont.setColor(IndexedColors.BLUE.getIndex());
        linkFont.setUnderline(Font.U_SINGLE);
        style.setFont(linkFont);
        
        return style;
    }
    
    /**
     * 创建带样式的单元格
     */
    private Cell createCellWithStyle(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return cell;
    }
    
    /**
     * 获取主键列
     */
    private Set<String> getPrimaryKeys(DatabaseMetaData metaData, String tableName) throws SQLException {
        Set<String> primaryKeys = new HashSet<>();
        ResultSet rs = metaData.getPrimaryKeys(null, null, tableName);
        while (rs.next()) {
            primaryKeys.add(rs.getString("COLUMN_NAME"));
        }
        rs.close();
        return primaryKeys;
    }
    
    /**
     * 获取主键约束名称
     */
    private String getPrimaryKeyName(DatabaseMetaData metaData, String tableName) throws SQLException {
        ResultSet rs = metaData.getPrimaryKeys(null, null, tableName);
        String pkName = "pk_" + tableName;  // 默认值
        
        if (rs.next()) {
            String name = rs.getString("PK_NAME");
            if (name != null && !name.trim().isEmpty()) {
                pkName = name;
            }
        }
        rs.close();
        
        return pkName;
    }
    
    
    @Override
    public Map<String, Object> exportTablesByGroups(String environment, List<String> groups) throws Exception {
        log.info("按分组导出数据库表，环境: {}, 分组数: {}", environment, groups.size());
        
        // 创建结果目录
        File resultDir = new File(RESULT_DIR);
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }
        
        // 获取数据库配置
        DatabaseExportConfig.EnvironmentConfig config = databaseExportConfig.getEnvironment(environment);
        if (config == null) {
            throw new RuntimeException("未找到环境配置: " + environment);
        }
        
        // 连接数据库
        Connection conn = null;
        List<Map<String, Object>> fileInfos = new ArrayList<>();
        
        try {
            conn = DriverManager.getConnection(
                config.getUrl(),
                config.getUsername(),
                config.getPassword()
            );
            
            log.info("数据库连接成功");
            
            // 获取所有表
            List<TableInfo> allTables = getAllTables(conn);
            log.info("共找到 {} 个表", allTables.size());
            
            // 是否全选了5个组
            boolean allGroupsSelected = groups.size() == 5 && groups.containsAll(ALL_GROUPS);
            Set<String> exportedTables = new HashSet<>();
            
            // 为每个分组导出Excel
            for (String group : groups) {
                Set<String> groupTableNames = loadGroupTables(group);
                
                // 找出在数据库中存在的表
                List<TableInfo> groupTables = allTables.stream()
                    .filter(t -> groupTableNames.contains(t.tableName.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
                
                // 找出配置了但在数据库中不存在的表
                Set<String> existingTableNames = groupTables.stream()
                    .map(t -> t.tableName.toLowerCase())
                    .collect(java.util.stream.Collectors.toSet());
                
                Set<String> notFoundTables = new HashSet<>();
                for (String configuredTable : groupTableNames) {
                    if (!existingTableNames.contains(configuredTable)) {
                        notFoundTables.add(configuredTable);
                    }
                }
                
                // 导出Excel（包含未找到的表信息）
                String fileName = exportGroupToExcel(conn, environment, group, groupTables, notFoundTables);
                
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("fileName", fileName);
                fileInfo.put("groupName", getGroupDisplayName(group));
                fileInfo.put("tableCount", groupTables.size());
                fileInfo.put("notFoundCount", notFoundTables.size());
                fileInfos.add(fileInfo);
                
                // 记录已导出的表
                groupTables.forEach(t -> exportedTables.add(t.tableName));
                
                if (!notFoundTables.isEmpty()) {
                    log.warn("分组 {} 中有 {} 个表在数据库中未找到: {}", 
                            group, notFoundTables.size(), notFoundTables);
                }
            }
            
            // 如果全选了5个组，导出未分组的表
            if (allGroupsSelected) {
                List<TableInfo> ungroupedTables = allTables.stream()
                    .filter(t -> !exportedTables.contains(t.tableName))
                    .collect(java.util.stream.Collectors.toList());
                
                if (!ungroupedTables.isEmpty()) {
                    // 未分组表没有配置，所以notFoundTables为空
                    String fileName = exportGroupToExcel(conn, environment, "ungrouped", 
                                                        ungroupedTables, new HashSet<>());
                    
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("fileName", fileName);
                    fileInfo.put("groupName", "未分组表");
                    fileInfo.put("tableCount", ungroupedTables.size());
                    fileInfo.put("notFoundCount", 0);
                    fileInfos.add(fileInfo);
                    
                    log.info("导出未分组表 {} 个", ungroupedTables.size());
                }
            }
            
            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("files", fileInfos);
            result.put("environment", environment);
            
            return result;
            
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
    }
    
    /**
     * 导出单个分组到Excel
     */
    private String exportGroupToExcel(Connection conn, String environment, String group, 
                                     List<TableInfo> tables, Set<String> notFoundTables) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        
        // 创建总览sheet
        Sheet overviewSheet = workbook.createSheet("表总览");
        createOverviewSheet(overviewSheet, tables, notFoundTables);
        
        // 为每个表创建sheet
        for (TableInfo tableInfo : tables) {
            createTableSheet(workbook, conn, tableInfo);
        }
        
        // 生成文件名
        String fileName = generateFileNameWithGroup(environment, group);
        File resultFile = new File(RESULT_DIR, fileName);
        try (FileOutputStream fos = new FileOutputStream(resultFile)) {
            workbook.write(fos);
        }
        workbook.close();
        
        log.info("导出分组 {} 完成，文件: {}, 表数: {}", group, fileName, tables.size());
        return fileName;
    }
    
    /**
     * 加载分组的表列表
     */
    private Set<String> loadGroupTables(String group) throws Exception {
        Set<String> tables = new HashSet<>();
        File configDir = new File(GROUP_CONFIG_DIR);
        File configFile = new File(configDir, group + ".txt");
        
        if (configFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String tableName = line.trim();
                    if (!tableName.isEmpty()) {
                        tables.add(tableName.toLowerCase());
                    }
                }
            }
            log.debug("加载分组配置 {}: {} 个表", group, tables.size());
        } else {
            log.warn("分组配置文件不存在: {}", configFile.getAbsolutePath());
        }
        
        return tables;
    }
    
    @Override
    public String getGroupConfig(String group) throws Exception {
        File configDir = new File(GROUP_CONFIG_DIR);
        File configFile = new File(configDir, group + ".txt");
        
        if (!configFile.exists()) {
            return "";
        }
        
        StringBuilder content = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        return content.toString().trim();
    }
    
    @Override
    public void saveGroupConfig(String group, String tables) throws Exception {
        File configDir = new File(GROUP_CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        // 解析要保存的表列表，并检查同组内重复
        Set<String> newTables = new HashSet<>();
        Map<String, Integer> duplicates = new HashMap<>();
        
        String[] lines = tables.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String tableName = lines[i].trim();
            if (!tableName.isEmpty()) {
                String lowerName = tableName.toLowerCase();
                
                // 检查同组内是否有重复
                if (newTables.contains(lowerName)) {
                    duplicates.put(tableName, i + 1); // 记录行号
                } else {
                    newTables.add(lowerName);
                }
            }
        }
        
        // 如果同组内有重复，返回错误
        if (!duplicates.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder("配置中存在重复的表名：\n");
            for (Map.Entry<String, Integer> entry : duplicates.entrySet()) {
                errorMsg.append("  - ").append(entry.getKey())
                       .append(" (第").append(entry.getValue()).append("行)\n");
            }
            errorMsg.append("\n请检查并删除重复的表名");
            throw new RuntimeException(errorMsg.toString());
        }
        
        // 检查表是否已在其他组中配置
        Map<String, String> conflicts = checkTableConflicts(group, newTables);
        
        if (!conflicts.isEmpty()) {
            // 构建错误信息
            StringBuilder errorMsg = new StringBuilder("以下表已在其他分组中配置：\n");
            for (Map.Entry<String, String> entry : conflicts.entrySet()) {
                String tableName = entry.getKey();
                String existingGroup = entry.getValue();
                errorMsg.append("  - ").append(tableName)
                       .append(" 在 ").append(getGroupDisplayName(existingGroup))
                       .append(" 已存在\n");
            }
            throw new RuntimeException(errorMsg.toString());
        }
        
        // 没有冲突，保存配置
        File configFile = new File(configDir, group + ".txt");
        try (java.io.BufferedWriter writer = new java.io.BufferedWriter(
                new java.io.FileWriter(configFile))) {
            writer.write(tables);
        }
        
        log.info("保存分组配置成功: {}, 共 {} 个表", group, newTables.size());
    }
    
    /**
     * 检查表是否在其他分组中已配置
     * @param currentGroup 当前正在配置的分组
     * @param newTables 要保存的表列表
     * @return 冲突的表映射（表名 -> 已存在的分组）
     */
    private Map<String, String> checkTableConflicts(String currentGroup, Set<String> newTables) throws Exception {
        Map<String, String> conflicts = new HashMap<>();
        File configDir = new File(GROUP_CONFIG_DIR);
        
        if (!configDir.exists()) {
            return conflicts;
        }
        
        // 遍历所有分组配置文件
        for (String checkGroup : ALL_GROUPS) {
            // 跳过当前正在配置的分组（同组内可以重复，会覆盖）
            if (checkGroup.equals(currentGroup)) {
                continue;
            }
            
            File configFile = new File(configDir, checkGroup + ".txt");
            if (!configFile.exists()) {
                continue;
            }
            
            // 读取该分组的表列表
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String existingTable = line.trim().toLowerCase();
                    if (!existingTable.isEmpty() && newTables.contains(existingTable)) {
                        // 发现冲突
                        conflicts.put(line.trim(), checkGroup);
                    }
                }
            }
        }
        
        return conflicts;
    }
    
    /**
     * 获取分组显示名称
     */
    private String getGroupDisplayName(String group) {
        switch (group) {
            case "deposit": return "存款组";
            case "loan": return "贷款组";
            case "public": return "公共组";
            case "settlement": return "结算组";
            case "platform": return "平台组";
            case "ungrouped": return "未分组表";
            default: return group;
        }
    }
    
    /**
     * 生成带分组的文件名
     */
    private String generateFileNameWithGroup(String environment, String group) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String groupName = getGroupDisplayName(group);
        return environment + "_" + groupName + "_" + timestamp + ".xlsx";
    }
    
    @Override
    public File getResultFile(String fileName) {
        return new File(RESULT_DIR, fileName);
    }
}

