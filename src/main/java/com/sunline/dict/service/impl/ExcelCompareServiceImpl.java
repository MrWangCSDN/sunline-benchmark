package com.sunline.dict.service.impl;

import com.sunline.dict.common.CompareMode;
import com.sunline.dict.service.ExcelCompareService;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Excel文档比对服务实现
 */
@Service
public class ExcelCompareServiceImpl implements ExcelCompareService {
    
    private static final Logger log = LoggerFactory.getLogger(ExcelCompareServiceImpl.class);
    
    private static final String RESULT_DIR = "excel_compare_results";
    private static final int COLUMN_TABLE_NAME = 1; // B列索引（从0开始）
    private static final int COLUMN_RESULT = 4; // E列索引
    
    // 线程池（用于并行比对sheet）
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    
    /**
     * 根据比对模式获取术语（表/接口）
     */
    private String getTerminology(CompareMode mode, String defaultTerm) {
        if (mode == CompareMode.ESF_INTERFACE) {
            return defaultTerm.replace("表", "接口");
        }
        return defaultTerm;
    }
    
    // 样式缓存（避免重复创建样式对象，这是性能关键！）
    private static class StyleCache {
        CellStyle modifiedBgWithBorder;  // 修改标记（黄色+边框）
        CellStyle addedBgWithBorder;     // 新增标记（绿色+边框）
        CellStyle deletedBgWithBorder;   // 删除标记（灰色+删除线+边框）
        CellStyle normalWithBorder;      // 普通样式（带边框）
        
        StyleCache(Workbook workbook) {
            // 修改标记样式（黄色背景+边框）
            modifiedBgWithBorder = workbook.createCellStyle();
            modifiedBgWithBorder.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            modifiedBgWithBorder.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(modifiedBgWithBorder);
            
            // 新增标记样式（绿色背景+边框）
            addedBgWithBorder = workbook.createCellStyle();
            addedBgWithBorder.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            addedBgWithBorder.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(addedBgWithBorder);
            
            // 删除标记样式（灰色背景+删除线+边框）
            deletedBgWithBorder = workbook.createCellStyle();
            deletedBgWithBorder.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            deletedBgWithBorder.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            setBorders(deletedBgWithBorder);
            
            // 设置删除线字体
            Font strikeFont = workbook.createFont();
            strikeFont.setStrikeout(true);
            deletedBgWithBorder.setFont(strikeFont);
            
            // 普通样式（带边框）
            normalWithBorder = workbook.createCellStyle();
            setBorders(normalWithBorder);
            
            log.info("样式缓存初始化完成，共创建 4 个样式（性能优化）");
        }
        
        private void setBorders(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
    
    @Override
    public Map<String, Object> compareExcelFiles(MultipartFile baseFile, MultipartFile compareFile) throws Exception {
        // 默认使用普通模式
        return compareExcelFiles(baseFile, compareFile, CompareMode.NORMAL);
    }
    
    @Override
    public Map<String, Object> compareExcelFiles(MultipartFile baseFile, MultipartFile compareFile, CompareMode mode) throws Exception {
        log.info("开始比较Excel文件，模式: {}", mode);
        
        // ESF接口文档比对模式：直接比较所有sheet，不依赖表总览
        if (mode == CompareMode.ESF_INTERFACE) {
            return compareEsfInterfaces(baseFile, compareFile);
        }
        
        // 迁移中间表比对模式：XML表定义 vs 迁移中间表
        if (mode == CompareMode.MIGRATION_TABLE) {
            return compareMigrationTables(baseFile, compareFile);
        }
        
        // 创建结果目录
        File resultDir = new File(RESULT_DIR);
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }
        
        // 调整Zip bomb检测阈值，允许高度压缩的Excel文件
        // 默认值是0.01，这里设置为0.001以支持压缩比更低的文件
        ZipSecureFile.setMinInflateRatio(0.001);
        log.debug("已设置ZipSecureFile最小解压比率为: 0.001");
        
        // 读取Excel文件
        Workbook baseWorkbook = WorkbookFactory.create(baseFile.getInputStream());
        Workbook compareWorkbook = WorkbookFactory.create(compareFile.getInputStream());
        
        try {
            // 创建结果工作簿
            Workbook resultWorkbook = new XSSFWorkbook();
            
            // 创建样式缓存（复用样式对象，避免超出64000个样式限制）
            StyleCache styleCache = new StyleCache(resultWorkbook);
            
            // 统计信息（使用原子类保证线程安全）
            AtomicInteger addedTables = new AtomicInteger(0);
            AtomicInteger deletedTables = new AtomicInteger(0);
            AtomicInteger modifiedTables = new AtomicInteger(0);
            
            // 修订记录列表（使用线程安全的集合）
            List<RevisionRecord> revisionRecords = Collections.synchronizedList(new ArrayList<>());
            
            // 读取表总览sheet
            Sheet baseOverviewSheet = baseWorkbook.getSheetAt(0);
            Sheet compareOverviewSheet = compareWorkbook.getSheetAt(0);
            
            // 获取表列表
            Map<String, Integer> baseTableMap = getTableMap(baseOverviewSheet);
            Map<String, Integer> compareTableMap = getTableMap(compareOverviewSheet);
            
            // 根据模式动态调整术语
            String tableTerm = getTerminology(mode, "表");
            log.info("基本版{}数量: {}, 比较版{}数量: {}", tableTerm, baseTableMap.size(), tableTerm, compareTableMap.size());
            
            // 创建结果总览sheet，合并基本版和比较版的总览
            String overviewSheetName = getTerminology(mode, "表总览");
            Sheet resultOverviewSheet = resultWorkbook.createSheet(overviewSheetName);
            Map<String, Integer> resultTableRowMap = new HashMap<>();
            mergeOverviewSheets(baseOverviewSheet, compareOverviewSheet, resultOverviewSheet, resultTableRowMap);
            
            // 比较表（使用多线程并行处理）
            Set<String> allTables = new LinkedHashSet<>();
            allTables.addAll(baseTableMap.keySet());
            allTables.addAll(compareTableMap.keySet());
            
            log.info("开始并行比对 {} 个{}，使用 {} 个线程", allTables.size(), tableTerm, THREAD_POOL_SIZE);
            
            // 使用CountDownLatch等待所有任务完成
            CountDownLatch latch = new CountDownLatch(allTables.size());
            
            for (String tableName : allTables) {
                // 提交比对任务到线程池
                executorService.submit(() -> {
                    try {
                        compareTable(tableName, baseTableMap, compareTableMap, 
                                   baseWorkbook, compareWorkbook, resultWorkbook, 
                                   resultOverviewSheet, resultTableRowMap,
                                   addedTables, deletedTables, modifiedTables,
                                   revisionRecords, styleCache, mode);
                    } catch (Exception e) {
                        log.error("比对表失败: {}", tableName, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // 等待所有比对任务完成
            try {
                latch.await(30, TimeUnit.MINUTES); // 最多等待30分钟
            } catch (InterruptedException e) {
                log.error("等待比对任务完成时被中断", e);
                Thread.currentThread().interrupt();
            }
            
            log.info("所有{}比对完成", tableTerm);
            
            // 创建修订记录sheet（根据模式使用不同的sheet名称和格式）
            String revisionSheetName;
            if (mode == CompareMode.XML_DB) {
                revisionSheetName = "差异结果";
            } else if (mode == CompareMode.ESF_INTERFACE) {
                revisionSheetName = "修订记录";
            } else {
                revisionSheetName = "修订记录";
            }
            createRevisionSheet(resultWorkbook, revisionRecords, revisionSheetName, mode);
            
            // 修复各个sheet中指向总览的反向超链接
            fixBackLinksToOverview(resultWorkbook, resultTableRowMap, overviewSheetName);
            
            // 保存结果文件
            String fileName = generateFileName(baseFile.getOriginalFilename());
            File resultFile = new File(resultDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                resultWorkbook.write(fos);
            }
            
            log.info("比较完成，结果文件: {}", fileName);
            
            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            // 根据模式动态调整返回结果的key名称（复用之前定义的tableTerm变量）
            result.put("added" + tableTerm + "s", addedTables.get());
            result.put("deleted" + tableTerm + "s", deletedTables.get());
            result.put("modified" + tableTerm + "s", modifiedTables.get());
            result.put("total" + tableTerm + "s", allTables.size());
            
            return result;
            
        } finally {
            baseWorkbook.close();
            compareWorkbook.close();
        }
    }
    
    /**
     * ESF接口文档比对（阶段2：直接比较所有sheet，不依赖表总览）
     * 每个sheet名称就是交易码（接口名称）
     */
    private Map<String, Object> compareEsfInterfaces(MultipartFile baseFile, MultipartFile compareFile) throws Exception {
        log.info("开始ESF接口文档比对（阶段2模式）");
        
        // 创建结果目录
        File resultDir = new File(RESULT_DIR);
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }
        
        // 调整Zip bomb检测阈值
        ZipSecureFile.setMinInflateRatio(0.001);
        log.debug("已设置ZipSecureFile最小解压比率为: 0.001");
        
        // 读取Excel文件
        Workbook baseWorkbook = WorkbookFactory.create(baseFile.getInputStream());
        Workbook compareWorkbook = WorkbookFactory.create(compareFile.getInputStream());
        
        try {
            // 创建结果工作簿
            Workbook resultWorkbook = new XSSFWorkbook();
            
            // 创建样式缓存
            StyleCache styleCache = new StyleCache(resultWorkbook);
            
            // 统计信息
            AtomicInteger addedInterfaces = new AtomicInteger(0);
            AtomicInteger deletedInterfaces = new AtomicInteger(0);
            AtomicInteger modifiedInterfaces = new AtomicInteger(0);
            
            // 修订记录列表
            List<RevisionRecord> revisionRecords = Collections.synchronizedList(new ArrayList<>());
            
            // 收集所有sheet名称（交易码）
            Set<String> allSheetNames = new LinkedHashSet<>();
            
            // 从基本版收集sheet名称
            for (int i = 0; i < baseWorkbook.getNumberOfSheets(); i++) {
                String sheetName = baseWorkbook.getSheetAt(i).getSheetName();
                allSheetNames.add(sheetName);
            }
            
            // 从比较版收集sheet名称
            for (int i = 0; i < compareWorkbook.getNumberOfSheets(); i++) {
                String sheetName = compareWorkbook.getSheetAt(i).getSheetName();
                allSheetNames.add(sheetName);
            }
            
            log.info("基本版接口数量: {}, 比较版接口数量: {}, 总计: {}", 
                    baseWorkbook.getNumberOfSheets(), 
                    compareWorkbook.getNumberOfSheets(), 
                    allSheetNames.size());
            
            // 使用CountDownLatch等待所有任务完成
            CountDownLatch latch = new CountDownLatch(allSheetNames.size());
            
            // 并行比较所有sheet
            for (String sheetName : allSheetNames) {
                executorService.submit(() -> {
                    try {
                        Sheet baseSheet = baseWorkbook.getSheet(sheetName);
                        Sheet compareSheet = compareWorkbook.getSheet(sheetName);
                        
                        if (baseSheet == null && compareSheet != null) {
                            // 接口新增
                            log.info("接口新增: {}", sheetName);
                            synchronized (resultWorkbook) {
                                copySheetIfNotExists(compareWorkbook, resultWorkbook, sheetName);
                            }
                            addedInterfaces.incrementAndGet();
                            revisionRecords.add(new RevisionRecord(
                                sheetName, "接口", "新增", sheetName, sheetName, 0
                            ));
                            
                        } else if (baseSheet != null && compareSheet == null) {
                            // 接口删除
                            log.info("接口删除: {}", sheetName);
                            synchronized (resultWorkbook) {
                                copySheetIfNotExists(baseWorkbook, resultWorkbook, sheetName);
                            }
                            deletedInterfaces.incrementAndGet();
                            revisionRecords.add(new RevisionRecord(
                                sheetName, "接口", "删除", sheetName, sheetName, 0
                            ));
                            
                        } else if (baseSheet != null && compareSheet != null) {
                            // 比较接口内容
                            List<RevisionRecord> sheetRevisions = new ArrayList<>();
                            boolean hasChanges;
                            
                            synchronized (resultWorkbook) {
                                hasChanges = compareAndMergeSheet(
                                    baseSheet, compareSheet, resultWorkbook, sheetName, 
                                    sheetRevisions, styleCache, CompareMode.ESF_INTERFACE);
                            }
                            
                            if (hasChanges) {
                                modifiedInterfaces.incrementAndGet();
                                revisionRecords.addAll(sheetRevisions);
                            } else {
                                // 无变化，直接复制比较版
                                synchronized (resultWorkbook) {
                                    copySheetIfNotExists(compareWorkbook, resultWorkbook, sheetName);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("比对接口失败: {}", sheetName, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // 等待所有比对任务完成
            try {
                latch.await(30, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.error("等待比对任务完成时被中断", e);
                Thread.currentThread().interrupt();
            }
            
            log.info("所有接口比对完成");
            
            // 创建修订记录sheet
            createRevisionSheet(resultWorkbook, revisionRecords, "修订记录", CompareMode.ESF_INTERFACE);
            
            // 保存结果文件
            String fileName = generateFileName(baseFile.getOriginalFilename());
            File resultFile = new File(resultDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                resultWorkbook.write(fos);
            }
            
            log.info("ESF接口比对完成，结果文件: {}", fileName);
            
            // 返回结果（使用标准key名称，前端期望addedTables/deletedTables等）
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("addedTables", addedInterfaces.get());
            result.put("deletedTables", deletedInterfaces.get());
            result.put("modifiedTables", modifiedInterfaces.get());
            result.put("totalTables", allSheetNames.size());
            
            return result;
            
        } finally {
            baseWorkbook.close();
            compareWorkbook.close();
        }
    }
    
    /**
     * 迁移中间表比对（左：XML表定义，右：迁移中间表）
     * 特殊规则：
     * 1. 跳过"表总览"sheet
     * 2. 左边sheet多：标记为"表新增"
     * 3. 右边sheet多：忽略
     * 4. Sheet相同：比对字段，字段映射（XML A/B/C/D/F/H -> 结果 B/C/D/E/F/H，A列从1递增）
     * 5. 字段修改：黄色显示最新值
     * 6. 字段新增（左有右无）：绿色显示
     * 7. 字段删除（左无右有）：修订记录中体现
     * 8. 补充M列往后的列
     */
    private Map<String, Object> compareMigrationTables(MultipartFile xmlFile, MultipartFile migrationFile) throws Exception {
        log.info("开始迁移中间表比对");
        
        // 创建结果目录
        File resultDir = new File(RESULT_DIR);
        if (!resultDir.exists()) {
            resultDir.mkdirs();
        }
        
        // 调整Zip bomb检测阈值
        ZipSecureFile.setMinInflateRatio(0.001);
        
        // 读取Excel文件
        Workbook xmlWorkbook = WorkbookFactory.create(xmlFile.getInputStream());
        Workbook migrationWorkbook = WorkbookFactory.create(migrationFile.getInputStream());
        
        try {
            // 创建结果工作簿
            Workbook resultWorkbook = new XSSFWorkbook();
            
            // 创建样式缓存
            StyleCache styleCache = new StyleCache(resultWorkbook);
            
            // 统计信息
            AtomicInteger addedTables = new AtomicInteger(0);
            AtomicInteger modifiedTables = new AtomicInteger(0);
            
            // 修订记录列表
            List<RevisionRecord> revisionRecords = Collections.synchronizedList(new ArrayList<>());
            
            // 收集所有sheet名称，跳过包含"表总览"字样的sheet
            Set<String> xmlSheets = new LinkedHashSet<>();
            Set<String> migrationSheets = new LinkedHashSet<>();
            
            for (int i = 0; i < xmlWorkbook.getNumberOfSheets(); i++) {
                String sheetName = xmlWorkbook.getSheetAt(i).getSheetName();
                // 跳过所有包含"表总览"字样的sheet
                if (!sheetName.contains("表总览")) {
                    xmlSheets.add(sheetName);
                }
            }
            
            for (int i = 0; i < migrationWorkbook.getNumberOfSheets(); i++) {
                String sheetName = migrationWorkbook.getSheetAt(i).getSheetName();
                migrationSheets.add(sheetName);
            }
            
            log.info("XML表定义表数量: {}, 迁移中间表表数量: {}", xmlSheets.size(), migrationSheets.size());
            
            // 使用CountDownLatch等待所有任务完成
            CountDownLatch latch = new CountDownLatch(xmlSheets.size());
            
            // 并行比较所有sheet（只处理XML中存在的表）
            for (String tableName : xmlSheets) {
                executorService.submit(() -> {
                    try {
                        Sheet xmlSheet = xmlWorkbook.getSheet(tableName);
                        Sheet migrationSheet = migrationWorkbook.getSheet(tableName);
                        
                        if (migrationSheet == null) {
                            // 左边有，右边没有：表新增
                            log.info("表新增: {}", tableName);
                            addedTables.incrementAndGet();
                            revisionRecords.add(new RevisionRecord(
                                tableName, "表", "新增", tableName + " 表新增", tableName, 0
                            ));
                            // 不创建结果sheet
                        } else {
                            // 都存在：比对字段部分
                            List<RevisionRecord> tableRevisions = new ArrayList<>();
                            boolean hasChanges;
                            
                            synchronized (resultWorkbook) {
                                hasChanges = compareMigrationTableSheet(
                                    xmlSheet, migrationSheet, resultWorkbook, tableName,
                                    tableRevisions, styleCache);
                            }
                            
                            if (hasChanges) {
                                modifiedTables.incrementAndGet();
                                revisionRecords.addAll(tableRevisions);
                            }
                        }
                    } catch (Exception e) {
                        log.error("比对表失败: {}", tableName, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // 等待所有比对任务完成
            try {
                latch.await(30, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                log.error("等待比对任务完成时被中断", e);
                Thread.currentThread().interrupt();
            }
            
            log.info("所有表比对完成");
            
            // 创建修订记录sheet（放在第一页）
            createMigrationRevisionSheet(resultWorkbook, revisionRecords);
            
            // 保存结果文件
            String fileName = generateFileName(xmlFile.getOriginalFilename());
            File resultFile = new File(resultDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(resultFile)) {
                resultWorkbook.write(fos);
            }
            
            log.info("迁移中间表比对完成，结果文件: {}", fileName);
            
            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", fileName);
            result.put("addedTables", addedTables.get());
            result.put("deletedTables", 0); // 右边多的表忽略，不统计删除
            result.put("modifiedTables", modifiedTables.get());
            result.put("totalTables", xmlSheets.size());
            
            return result;
            
        } finally {
            xmlWorkbook.close();
            migrationWorkbook.close();
        }
    }
    
    /**
     * 比对迁移中间表的单个sheet（字段部分）
     * 字段映射：XML的A/B/C/D/F/H -> 结果的B/C/D/E/F/H，A列从1递增
     * 字段修改：黄色显示最新值（不显示旧值->新值）
     * 字段新增：绿色显示
     * 字段删除：修订记录中体现
     * 补充M列往后的列
     */
    private boolean compareMigrationTableSheet(Sheet xmlSheet, Sheet migrationSheet,
                                               Workbook resultWorkbook, String tableName,
                                               List<RevisionRecord> revisionRecords,
                                               StyleCache styleCache) {
        boolean hasChanges = false;
        Sheet resultSheet = resultWorkbook.createSheet(tableName);
        
        // 识别XML字段部分：从"字段名"行开始，到"索引ID"行结束
        int xmlFieldStart = -1, xmlFieldEnd = -1;
        for (int i = 0; i <= xmlSheet.getLastRowNum(); i++) {
            Row row = xmlSheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(0);
                String value = getCellValueAsString(cell);
                if (value != null) {
                    if ("字段名".equals(value.trim())) {
                        xmlFieldStart = i + 1; // 跳过标题行
                    } else if ("索引ID".equals(value.trim())) {
                        xmlFieldEnd = i;
                        break;
                    }
                }
            }
        }
        
        // 迁移中间表字段从第11行开始（固定）
        int migrationFieldStart = 10; // 第11行，索引从0开始
        
        // 获取XML字段映射（A列字段名 -> 行号）
        Map<String, Integer> xmlFieldMap = new LinkedHashMap<>();
        if (xmlFieldStart != -1 && xmlFieldEnd != -1) {
            for (int i = xmlFieldStart; i < xmlFieldEnd; i++) {
                Row row = xmlSheet.getRow(i);
                if (row != null) {
                    String fieldName = getCellValueAsString(row.getCell(0));
                    if (fieldName != null && !fieldName.trim().isEmpty()) {
                        xmlFieldMap.put(fieldName.trim(), i);
                    }
                }
            }
        }
        
        // 获取迁移中间表字段映射（B列字段名 -> 行号）
        Map<String, Integer> migrationFieldMap = new LinkedHashMap<>();
        for (int i = migrationFieldStart; i <= migrationSheet.getLastRowNum(); i++) {
            Row row = migrationSheet.getRow(i);
            if (row != null) {
                String fieldName = getCellValueAsString(row.getCell(1)); // B列
                if (fieldName != null && !fieldName.trim().isEmpty()) {
                    migrationFieldMap.put(fieldName.trim(), i);
                    break; // 只读取第一个字段行作为起始
                }
            }
        }
        
        // 重新获取所有迁移中间表字段
        migrationFieldMap.clear();
        for (int i = migrationFieldStart; i <= migrationSheet.getLastRowNum(); i++) {
            Row row = migrationSheet.getRow(i);
            if (row != null) {
                String fieldName = getCellValueAsString(row.getCell(1));
                if (fieldName != null && !fieldName.trim().isEmpty()) {
                    migrationFieldMap.put(fieldName.trim(), i);
                }
            }
        }
        
        // 复制迁移中间表的前10行（标题部分）
        for (int i = 0; i < migrationFieldStart; i++) {
            Row migrationRow = migrationSheet.getRow(i);
            if (migrationRow != null) {
                Row resultRow = resultSheet.createRow(i);
                copyRow(migrationRow, resultRow, migrationSheet.getWorkbook());
            }
        }
        
        // 处理字段部分
        int resultRowNum = migrationFieldStart;
        int seqNum = 1;
        Set<String> processedFields = new HashSet<>();
        
        // 遍历XML字段（保持XML顺序）
        for (String fieldName : xmlFieldMap.keySet()) {
            processedFields.add(fieldName);
            int xmlRowIndex = xmlFieldMap.get(fieldName);
            Integer migrationRowIndex = migrationFieldMap.get(fieldName);
            
            Row xmlRow = xmlSheet.getRow(xmlRowIndex);
            Row resultRow = resultSheet.createRow(resultRowNum);
            
            if (migrationRowIndex == null) {
                // 字段新增（左有右无）：绿色显示
                createMigrationFieldRow(resultRow, xmlRow, seqNum, styleCache.addedBgWithBorder, migrationSheet);
                hasChanges = true;
                
                revisionRecords.add(new RevisionRecord(
                    tableName, "字段", "新增", fieldName + " 字段新增",
                    tableName, resultRowNum
                ));
            } else {
                // 字段存在：比对并映射
                Row migrationRow = migrationSheet.getRow(migrationRowIndex);
                List<String> changeDetails = new ArrayList<>();
                boolean fieldChanged = createMigrationFieldRowWithCompare(
                    resultRow, xmlRow, migrationRow, seqNum, styleCache, migrationSheet, changeDetails);
                
                if (fieldChanged) {
                    hasChanges = true;
                    // 修订明细：字段名 + 每个发生变化的列的 旧值→新值
                    String detail = fieldName + " 字段修改";
                    if (!changeDetails.isEmpty()) {
                        detail += "\n" + String.join("\n", changeDetails);
                    }
                    revisionRecords.add(new RevisionRecord(
                        tableName, "字段", "修改", detail,
                        tableName, resultRowNum
                    ));
                }
            }
            
            resultRowNum++;
            seqNum++;
        }
        
        // 检查删除的字段（左无右有）：添加到sheet页最下面，灰色+删除线
        for (String fieldName : migrationFieldMap.keySet()) {
            if (!processedFields.contains(fieldName)) {
                // 字段删除：在结果sheet中显示（灰色+删除线），并记录到修订记录
                int migrationRowIndex = migrationFieldMap.get(fieldName);
                Row migrationRow = migrationSheet.getRow(migrationRowIndex);
                Row resultRow = resultSheet.createRow(resultRowNum);
                
                // 复制整行，应用删除样式
                copyMigrationRowWithDeleteStyle(resultRow, migrationRow, styleCache.deletedBgWithBorder);
                
                // 记录修订
                revisionRecords.add(new RevisionRecord(
                    tableName, "字段", "删除", fieldName + " 字段删除",
                    tableName, resultRowNum
                ));
                
                resultRowNum++;
                hasChanges = true;
            }
        }
        
        // 复制列宽
        for (int i = 0; i < 20; i++) {
            resultSheet.setColumnWidth(i, migrationSheet.getColumnWidth(i));
        }
        
        return hasChanges;
    }
    
    /**
     * 创建迁移字段行（新增字段，绿色显示）
     * 字段映射：XML A/B/C/D/F/H -> 结果 B/C/D/E/F/H，A列填序号
     */
    private void createMigrationFieldRow(Row resultRow, Row xmlRow, int seqNum, 
                                        CellStyle style, Sheet migrationSheet) {
        // A列：序号
        Cell cell = resultRow.createCell(0);
        cell.setCellValue(seqNum);
        cell.setCellStyle(style);
        
        // B列：字段名（XML A列）
        copyCellWithStyle(xmlRow, 0, resultRow, 1, style);
        
        // C列：中文名（XML B列）
        copyCellWithStyle(xmlRow, 1, resultRow, 2, style);
        
        // D列：数据库类型（XML C列）
        copyCellWithStyle(xmlRow, 2, resultRow, 3, style);
        
        // E列：长度（XML D列）
        copyCellWithStyle(xmlRow, 3, resultRow, 4, style);
        
        // F列：空值（XML F列）
        copyCellWithStyle(xmlRow, 5, resultRow, 5, style);
        
        // H列：默认值（XML H列）
        copyCellWithStyle(xmlRow, 7, resultRow, 7, style);
        
        // G、I、J、K、L列保持空白（使用绿色样式）
        for (int i : new int[]{6, 8, 9, 10, 11}) {
            Cell c = resultRow.createCell(i);
            c.setCellStyle(style);
        }
    }
    
    /**
     * 创建迁移字段行并比对（存在的字段）
     * 字段映射：XML A/B/C/D/F/H -> 结果 B/C/D/E/F/H
     * 修改：黄色显示最新值，changeDetails记录每列的 旧值→新值
     * 未修改：白色显示
     * 补充M列往后的列
     */
    private boolean createMigrationFieldRowWithCompare(Row resultRow, Row xmlRow,
                                                       Row migrationRow, int seqNum,
                                                       StyleCache styleCache, Sheet migrationSheet,
                                                       List<String> changeDetails) {
        boolean hasChanges = false;

        // A列：序号（总是用序号，不比对）
        Cell cell = resultRow.createCell(0);
        cell.setCellValue(seqNum);
        cell.setCellStyle(styleCache.normalWithBorder);

        // 定义映射关系：[XML列, 结果列, 列中文名]
        Object[][] mappings = {
            {0, 1, "字段名"},
            {1, 2, "中文名"},
            {2, 3, "数据库类型"},
            {3, 4, "长度"},
            {5, 5, "空值"},
            {7, 7, "默认值"}
        };

        for (Object[] mapping : mappings) {
            int xmlCol = (int) mapping[0];
            int resultCol = (int) mapping[1];
            String colLabel = (String) mapping[2];

            String xmlValue = getCellValueAsString(xmlRow.getCell(xmlCol));
            String migrationValue = getCellValueAsString(migrationRow.getCell(resultCol));

            Cell resultCell = resultRow.createCell(resultCol);

            if (!Objects.equals(xmlValue, migrationValue)) {
                // 值不同：黄色显示最新值（XML的值），并记录 旧值→新值
                resultCell.setCellValue(xmlValue);
                resultCell.setCellStyle(styleCache.modifiedBgWithBorder);
                hasChanges = true;
                String oldVal = (migrationValue == null || migrationValue.isEmpty()) ? "（空）" : migrationValue;
                String newVal = (xmlValue == null || xmlValue.isEmpty()) ? "（空）" : xmlValue;
                changeDetails.add("  " + colLabel + ": " + oldVal + " → " + newVal);
            } else {
                // 值相同：白色显示
                resultCell.setCellValue(xmlValue);
                resultCell.setCellStyle(styleCache.normalWithBorder);
            }
        }
        
        // G、I、J、K、L列：从迁移中间表复制
        for (int i : new int[]{6, 8, 9, 10, 11}) {
            Cell migrationCell = migrationRow.getCell(i);
            Cell resultCell = resultRow.createCell(i);
            if (migrationCell != null) {
                copyCellValueOnly(migrationCell, resultCell);
            }
            resultCell.setCellStyle(styleCache.normalWithBorder);
        }
        
        // M列往后：从迁移中间表复制
        for (int i = 12; i < migrationRow.getLastCellNum(); i++) {
            Cell migrationCell = migrationRow.getCell(i);
            if (migrationCell != null) {
                Cell resultCell = resultRow.createCell(i);
                copyCellValueOnly(migrationCell, resultCell);
                resultCell.setCellStyle(styleCache.normalWithBorder);
            }
        }
        
        return hasChanges;
    }
    
    /**
     * 复制迁移字段行并应用删除样式（灰色+删除线）
     * 直接复制迁移中间表的整行
     */
    private void copyMigrationRowWithDeleteStyle(Row resultRow, Row migrationRow, CellStyle deleteStyle) {
        if (migrationRow == null) return;
        
        resultRow.setHeight(migrationRow.getHeight());
        
        // 复制所有列
        for (int i = 0; i < migrationRow.getLastCellNum(); i++) {
            Cell migrationCell = migrationRow.getCell(i);
            if (migrationCell != null) {
                Cell resultCell = resultRow.createCell(i);
                copyCellValueOnly(migrationCell, resultCell);
                resultCell.setCellStyle(deleteStyle);
            }
        }
    }
    
    /**
     * 复制单元格并应用样式
     */
    private void copyCellWithStyle(Row sourceRow, int sourceCol, Row targetRow, 
                                   int targetCol, CellStyle style) {
        Cell sourceCell = sourceRow.getCell(sourceCol);
        Cell targetCell = targetRow.createCell(targetCol);
        if (sourceCell != null) {
            copyCellValueOnly(sourceCell, targetCell);
        }
        targetCell.setCellStyle(style);
    }
    
    /**
     * 创建迁移中间表的修订记录sheet
     * 格式：A列-表名、B列-修订级别、C列-修订方式、D列-修订明细
     * 包含双向超链接
     */
    private void createMigrationRevisionSheet(Workbook workbook, List<RevisionRecord> revisionRecords) {
        if (revisionRecords.isEmpty()) {
            log.info("没有修订记录，跳过创建");
            return;
        }
        
        log.info("创建迁移中间表修订记录sheet，共 {} 条记录", revisionRecords.size());
        
        Sheet revisionSheet = workbook.createSheet("修订记录");
        workbook.setSheetOrder("修订记录", 0); // 放在第一页
        
        // 创建标题行
        Row headerRow = revisionSheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        createCellWithStyle(headerRow, 0, "表名", headerStyle);
        createCellWithStyle(headerRow, 1, "修订级别", headerStyle);
        createCellWithStyle(headerRow, 2, "修订方式", headerStyle);
        createCellWithStyle(headerRow, 3, "修订明细", headerStyle);
        
        // 设置列宽
        revisionSheet.setColumnWidth(0, 6000);  // 表名
        revisionSheet.setColumnWidth(1, 3000);  // 修订级别
        revisionSheet.setColumnWidth(2, 3000);  // 修订方式
        revisionSheet.setColumnWidth(3, 20000); // 修订明细（扩大以容纳 A→B 变化内容）

        // 创建样式
        CellStyle addedStyle = createBorderStyle(workbook);
        addedStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        addedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle deletedStyle = createBorderStyle(workbook);
        deletedStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        deletedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle modifiedStyle = createBorderStyle(workbook);
        modifiedStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        modifiedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle borderStyle = createBorderStyle(workbook);
        CellStyle wrapStyle = createBorderStyle(workbook);
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);
        
        // 填充数据
        int rowNum = 1;
        for (RevisionRecord record : revisionRecords) {
            Row row = revisionSheet.createRow(rowNum);
            
            // A列：表名
            createCellWithStyle(row, 0, record.tableName, borderStyle);
            
            // B列：修订级别
            createCellWithStyle(row, 1, record.revisionLevel, borderStyle);
            
            // C列：修订方式（带颜色）
            CellStyle statusStyle = "新增".equals(record.revisionType) ? addedStyle :
                                   "删除".equals(record.revisionType) ? deletedStyle : modifiedStyle;
            createCellWithStyle(row, 2, record.revisionType, statusStyle);
            
            // D列：修订明细（带超链接）
            Cell detailCell = row.createCell(3);
            detailCell.setCellValue(record.revisionDetail);
            detailCell.setCellStyle(wrapStyle);
            
            // 如果不是表级别的变更，创建双向超链接
            if (!"表".equals(record.revisionLevel) && record.targetRow > 0) {
                // 正向超链接：从修订记录 -> 目标sheet
                Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
                String address = "'" + record.targetSheet + "'!A" + (record.targetRow + 1);
                link.setAddress(address);
                detailCell.setHyperlink(link);

                // 修订明细包含多行变化内容时用黑色可读样式，仅首行字段名带超链接下划线蓝色
                boolean hasChangeDetail = record.revisionDetail.contains("\n");
                CellStyle linkStyle = workbook.createCellStyle();
                linkStyle.cloneStyleFrom(wrapStyle);
                Font linkFont = workbook.createFont();
                if (hasChangeDetail) {
                    // 有变化明细：黑色字体，便于多行阅读；靠超链接地址跳转
                    linkFont.setColor(IndexedColors.BLACK1.getIndex());
                } else {
                    linkFont.setColor(IndexedColors.BLUE.getIndex());
                    linkFont.setUnderline(Font.U_SINGLE);
                }
                linkStyle.setFont(linkFont);
                detailCell.setCellStyle(linkStyle);

                // 行高自适应（每行约 300 height units）
                int lineCount = record.revisionDetail.split("\n").length;
                if (lineCount > 1) {
                    row.setHeight((short) Math.min(lineCount * 320, 8000));
                }

                // 反向超链接：在目标sheet的A列添加链接返回修订记录
                addBackLinkToRevision(workbook, record.targetSheet, record.targetRow, rowNum, "修订记录");
            }

            rowNum++;
        }
        
        log.info("迁移中间表修订记录sheet创建完成");
    }
    
    /**
     * 获取表名映射（B列原始表名 -> 行号）
     */
    private Map<String, Integer> getTableMap(Sheet sheet) {
        Map<String, Integer> tableMap = new LinkedHashMap<>();
        
        if (sheet == null) return tableMap;
        
        for (int i = 1; i <= sheet.getLastRowNum(); i++) { // 从第2行开始（跳过标题）
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(COLUMN_TABLE_NAME);
                if (cell != null) {
                    String tableName = getCellValueAsString(cell);
                    // 跳过空值，避免空行被识别为表删除
                    if (tableName != null && !tableName.trim().isEmpty()) {
                        tableMap.put(tableName.trim(), i);
                    }
                }
            }
        }
        
        return tableMap;
    }
    
    /**
     * 规范化sheet名称
     * 1. 首字母大写
     * 2. 如果超过30个字符，截取前28位+后2位
     */
    private String normalizeSheetName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return tableName;
        }
        
        // 首字母大写
        String normalized = tableName.substring(0, 1).toUpperCase() + tableName.substring(1);
        
        // 限制长度（Excel sheet名称最大31个字符）
        if (normalized.length() > 30) {
            String prefix = normalized.substring(0, 28);
            String suffix = normalized.substring(normalized.length() - 2);
            normalized = prefix + suffix;
        }
        
        return normalized;
    }
    
    /**
     * 比对单个表（线程安全方法，优化版）
     */
    private void compareTable(String tableName, 
                             Map<String, Integer> baseTableMap,
                             Map<String, Integer> compareTableMap,
                             Workbook baseWorkbook,
                             Workbook compareWorkbook,
                             Workbook resultWorkbook,
                             Sheet resultOverviewSheet,
                             Map<String, Integer> resultTableRowMap,
                             AtomicInteger addedTables,
                             AtomicInteger deletedTables,
                             AtomicInteger modifiedTables,
                             List<RevisionRecord> revisionRecords,
                             StyleCache styleCache,
                             CompareMode mode) {
        
        boolean inBase = baseTableMap.containsKey(tableName);
        boolean inCompare = compareTableMap.containsKey(tableName);
        
        // 获取实际的sheet名称
        String actualSheetName = findActualSheetName(
            inBase ? baseWorkbook : compareWorkbook, tableName);
        
        if (!inBase && inCompare) {
            // 根据模式获取术语
            String tableTerm = getTerminology(mode, "表");
            String addTerm = getTerminology(mode, "新增");
            log.info("{}新增: {} (实际sheet: {})", tableTerm, tableName, actualSheetName);
            synchronized (resultOverviewSheet) {
                if (resultTableRowMap.containsKey(tableName)) {
                    markTableStatus(resultOverviewSheet, resultTableRowMap.get(tableName), addTerm);
                }
            }
            synchronized (resultWorkbook) {
                copySheetIfNotExists(compareWorkbook, resultWorkbook, actualSheetName);
            }
            addedTables.incrementAndGet();
            
            // 记录修订
            revisionRecords.add(new RevisionRecord(
                tableName, tableTerm, "新增", tableName, actualSheetName, 0
            ));
            
        } else if (inBase && !inCompare) {
            // 根据模式获取术语
            String tableTerm = getTerminology(mode, "表");
            String deleteTerm = getTerminology(mode, "删除");
            log.info("{}删除: {} (实际sheet: {})", tableTerm, tableName, actualSheetName);
            synchronized (resultOverviewSheet) {
                if (resultTableRowMap.containsKey(tableName)) {
                    markTableStatus(resultOverviewSheet, resultTableRowMap.get(tableName), deleteTerm);
                }
            }
            synchronized (resultWorkbook) {
                copySheetIfNotExists(baseWorkbook, resultWorkbook, actualSheetName);
            }
            deletedTables.incrementAndGet();
            
            // 记录修订
            revisionRecords.add(new RevisionRecord(
                tableName, tableTerm, "删除", tableName, actualSheetName, 0
            ));
            
        } else if (inBase && inCompare) {
            // 比较表内容
            Sheet baseSheet = baseWorkbook.getSheet(actualSheetName);
            Sheet compareSheet = compareWorkbook.getSheet(actualSheetName);
            
            if (baseSheet != null && compareSheet != null) {
                // 快速路径：先检查是否完全相同
                boolean quickCheck = quickCompare(baseSheet, compareSheet);
                
                if (quickCheck) {
                    // 完全相同，快速复制
                    synchronized (resultWorkbook) {
                        copySheetIfNotExists(compareWorkbook, resultWorkbook, actualSheetName);
                    }
                } else {
                    // 有差异，进行详细比对
                    List<RevisionRecord> tableRevisions = new ArrayList<>();
                    boolean hasChanges;
                    
                    // 只在创建sheet时同步
                    synchronized (resultWorkbook) {
                        hasChanges = compareAndMergeSheet(
                                baseSheet, compareSheet, resultWorkbook, actualSheetName, tableRevisions, styleCache, mode);
                    }
                    
                    if (hasChanges) {
                        synchronized (resultOverviewSheet) {
                            if (resultTableRowMap.containsKey(tableName)) {
                                String modifyTerm = getTerminology(mode, "修改");
                                markTableStatus(resultOverviewSheet, resultTableRowMap.get(tableName), modifyTerm);
                            }
                        }
                        modifiedTables.incrementAndGet();
                        revisionRecords.addAll(tableRevisions);
                    } else {
                        synchronized (resultWorkbook) {
                            copySheetIfNotExists(compareWorkbook, resultWorkbook, actualSheetName);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 合并两个总览sheet
     */
    private void mergeOverviewSheets(Sheet baseSheet, Sheet compareSheet, Sheet targetSheet, 
                                     Map<String, Integer> resultTableRowMap) {
        // 复制标题行
        if (baseSheet.getRow(0) != null) {
            Row headerRow = baseSheet.getRow(0);
            Row targetRow = targetSheet.createRow(0);
            copyRow(headerRow, targetRow, baseSheet.getWorkbook());
        }
        
        int currentRow = 1;
        Set<String> processedTables = new HashSet<>();
        
        // 先处理基本版的表
        for (int i = 1; i <= baseSheet.getLastRowNum(); i++) {
            Row row = baseSheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(COLUMN_TABLE_NAME);
                if (cell != null) {
                    String tableName = getCellValueAsString(cell);
                    if (tableName != null && !tableName.trim().isEmpty()) {
                        tableName = tableName.trim();
                        processedTables.add(tableName);
                        resultTableRowMap.put(tableName, currentRow);
                        
                        Row targetRow = targetSheet.createRow(currentRow++);
                        copyRow(row, targetRow, baseSheet.getWorkbook());
                    }
                }
            }
        }
        
        // 再处理比较版中新增的表
        for (int i = 1; i <= compareSheet.getLastRowNum(); i++) {
            Row row = compareSheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(COLUMN_TABLE_NAME);
                if (cell != null) {
                    String tableName = getCellValueAsString(cell);
                    if (tableName != null && !tableName.trim().isEmpty()) {
                        tableName = tableName.trim();
                        if (!processedTables.contains(tableName)) {
                            resultTableRowMap.put(tableName, currentRow);
                            
                            Row targetRow = targetSheet.createRow(currentRow++);
                            copyRow(row, targetRow, compareSheet.getWorkbook());
                        }
                    }
                }
            }
        }
        
        // 复制列宽
        for (int i = 0; i < 10; i++) {
            targetSheet.setColumnWidth(i, baseSheet.getColumnWidth(i));
        }
        
        // 复制合并单元格区域
        for (int i = 0; i < baseSheet.getNumMergedRegions(); i++) {
            targetSheet.addMergedRegion(baseSheet.getMergedRegion(i));
        }
    }
    
    /**
     * 标记表状态（使用样式缓存）
     */
    private void markTableStatus(Sheet sheet, int rowIndex, String status) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        
        Cell cell = row.getCell(COLUMN_RESULT);
        if (cell == null) {
            cell = row.createCell(COLUMN_RESULT);
        }
        
        cell.setCellValue(status);
        
        // 简化：不设置特殊样式，避免创建过多样式对象
        // 样式已经在StyleCache中定义好了
    }
    
    /**
     * 复制sheet（如果不存在）
     */
    private void copySheetIfNotExists(Workbook sourceWorkbook, Workbook targetWorkbook, String sheetName) {
        // 检查目标工作簿中是否已存在该sheet
        if (targetWorkbook.getSheet(sheetName) != null) {
            log.debug("Sheet已存在，跳过复制: {}", sheetName);
            return;
        }
        
        Sheet sourceSheet = sourceWorkbook.getSheet(sheetName);
        if (sourceSheet == null) {
            log.warn("源工作簿中不存在Sheet: {}", sheetName);
            return;
        }
        
        Sheet targetSheet = targetWorkbook.createSheet(sheetName);
        
        for (int i = 0; i <= sourceSheet.getLastRowNum(); i++) {
            Row sourceRow = sourceSheet.getRow(i);
            Row targetRow = targetSheet.createRow(i);
            
            if (sourceRow != null) {
                copyRow(sourceRow, targetRow, sourceWorkbook);
            }
        }
        
        // 复制列宽
        if (sourceSheet.getRow(0) != null) {
            for (int i = 0; i < sourceSheet.getRow(0).getLastCellNum(); i++) {
                targetSheet.setColumnWidth(i, sourceSheet.getColumnWidth(i));
            }
        }
        
        // 复制合并单元格区域
        for (int i = 0; i < sourceSheet.getNumMergedRegions(); i++) {
            try {
                targetSheet.addMergedRegion(sourceSheet.getMergedRegion(i));
            } catch (Exception e) {
                log.warn("复制合并单元格失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 比较并合并sheet（按照字段、索引、主键三个部分分别处理）
     * @param revisionRecords 修订记录列表（输出参数）
     * @param styleCache 样式缓存
     * @param mode 比对模式
     * @return true表示有变化，false表示无变化
     */
    private boolean compareAndMergeSheet(Sheet baseSheet, Sheet compareSheet, 
                                        Workbook resultWorkbook, String sheetName,
                                        List<RevisionRecord> revisionRecords,
                                        StyleCache styleCache,
                                        CompareMode mode) {
        boolean hasChanges = false;
        
        // 创建结果sheet
        Sheet resultSheet = resultWorkbook.createSheet(sheetName);
        
        // 识别sheet的部分
        SheetSections baseSections = identifySheetSections(baseSheet);
        SheetSections compareSections = identifySheetSections(compareSheet);
        
        int resultRowNum = 0;
        
        // ESF接口模式：处理输入/输出部分
        if (mode == CompareMode.ESF_INTERFACE) {
            // 1. 复制输入部分之前的所有行
            int copyEnd = Math.min(baseSections.inputStart, compareSections.inputStart);
            for (int i = 0; i < copyEnd; i++) {
                Row sourceRow = compareSheet.getRow(i);
                if (sourceRow != null) {
                    Row resultRow = resultSheet.createRow(resultRowNum++);
                    copyRow(sourceRow, resultRow, compareSheet.getWorkbook());
                }
            }
            
            // 2. 处理输入部分
            SectionCompareResult inputResult = compareSection(
                baseSheet, compareSheet, resultSheet,
                baseSections.inputStart, baseSections.outputStart,
                compareSections.inputStart, compareSections.outputStart,
                resultRowNum, 0, // 使用A列（英文名称）作为唯一标识
                sheetName, "输入",
                baseSheet.getWorkbook(), compareSheet.getWorkbook(), resultWorkbook,
                revisionRecords, styleCache, mode
            );
            resultRowNum += inputResult.addedRows;
            hasChanges = hasChanges || inputResult.hasChanges;
            
            // 3. 处理输出部分
            SectionCompareResult outputResult = compareSection(
                baseSheet, compareSheet, resultSheet,
                baseSections.outputStart, baseSheet.getLastRowNum() + 1,
                compareSections.outputStart, compareSheet.getLastRowNum() + 1,
                resultRowNum, 0, // 使用A列（英文名称）作为唯一标识
                sheetName, "输出",
                baseSheet.getWorkbook(), compareSheet.getWorkbook(), resultWorkbook,
                revisionRecords, styleCache, mode
            );
            resultRowNum += outputResult.addedRows;
            hasChanges = hasChanges || outputResult.hasChanges;
        } else {
            // 数据库表模式：处理字段/索引/主键部分
            // 1. 复制标题行（字段部分之前的所有行）
            for (int i = 0; i < Math.min(baseSections.fieldStart, compareSections.fieldStart); i++) {
                Row sourceRow = compareSheet.getRow(i);
                if (sourceRow != null) {
                    Row resultRow = resultSheet.createRow(resultRowNum++);
                    copyRow(sourceRow, resultRow, compareSheet.getWorkbook());
                }
            }
            
            // 2. 处理字段部分
            SectionCompareResult fieldResult = compareSection(
                baseSheet, compareSheet, resultSheet, 
                baseSections.fieldStart, baseSections.indexStart,
                compareSections.fieldStart, compareSections.indexStart,
                resultRowNum, 0, // 使用A列（索引0）作为唯一标识
                sheetName, "字段",
                baseSheet.getWorkbook(), compareSheet.getWorkbook(), resultWorkbook,
                revisionRecords, styleCache, mode
            );
            resultRowNum += fieldResult.addedRows;
            hasChanges = hasChanges || fieldResult.hasChanges;
            
            // 3. 处理索引部分
            SectionCompareResult indexResult = compareSection(
                baseSheet, compareSheet, resultSheet,
                baseSections.indexStart, baseSections.pkStart,
                compareSections.indexStart, compareSections.pkStart,
                resultRowNum, 0, // 使用A列（索引ID）作为唯一标识
                sheetName, "索引",
                baseSheet.getWorkbook(), compareSheet.getWorkbook(), resultWorkbook,
                revisionRecords, styleCache, mode
            );
            resultRowNum += indexResult.addedRows;
            hasChanges = hasChanges || indexResult.hasChanges;
            
            // 4. 处理主键部分
            SectionCompareResult pkResult = compareSection(
                baseSheet, compareSheet, resultSheet,
                baseSections.pkStart, baseSheet.getLastRowNum() + 1,
                compareSections.pkStart, compareSheet.getLastRowNum() + 1,
                resultRowNum, 0, // 使用A列作为唯一标识
                sheetName, "主键",
                baseSheet.getWorkbook(), compareSheet.getWorkbook(), resultWorkbook,
                revisionRecords, styleCache, mode
            );
            resultRowNum += pkResult.addedRows;
            hasChanges = hasChanges || pkResult.hasChanges;
        }
        
        // 复制列宽
        if (compareSheet.getRow(0) != null) {
            for (int i = 0; i < compareSheet.getRow(0).getLastCellNum(); i++) {
                resultSheet.setColumnWidth(i, compareSheet.getColumnWidth(i));
            }
        }
        
        // 复制合并单元格区域
        for (int i = 0; i < compareSheet.getNumMergedRegions(); i++) {
            try {
                resultSheet.addMergedRegion(compareSheet.getMergedRegion(i));
            } catch (Exception e) {
                log.warn("复制合并单元格失败: {}", e.getMessage());
            }
        }
        
        return hasChanges;
    }
    
    /**
     * Sheet的部分起始行号（支持数据库表和ESF接口两种格式）
     */
    private static class SheetSections {
        int fieldStart = -1;  // 字段部分起始行（数据库表）
        int indexStart = -1;  // 索引部分起始行（数据库表）
        int pkStart = -1;     // 主键部分起始行（数据库表）
        int inputStart = -1;  // 输入部分起始行（ESF接口）
        int outputStart = -1; // 输出部分起始行（ESF接口）
    }
    
    /**
     * 部分比较结果
     */
    private static class SectionCompareResult {
        int addedRows;      // 添加的行数
        boolean hasChanges; // 是否有变化
        
        SectionCompareResult(int addedRows, boolean hasChanges) {
            this.addedRows = addedRows;
            this.hasChanges = hasChanges;
        }
    }
    
    /**
     * 修订记录
     */
    private static class RevisionRecord {
        String tableName;       // 表名
        String revisionLevel;   // 修订级别（表/字段/索引/主键）
        String revisionType;    // 修订方式（新增/删除/修改）
        String revisionDetail;  // 修订明细
        String targetSheet;     // 目标sheet名称
        int targetRow;          // 目标行号（从0开始）
        
        RevisionRecord(String tableName, String revisionLevel, String revisionType, 
                      String revisionDetail, String targetSheet, int targetRow) {
            this.tableName = tableName;
            this.revisionLevel = revisionLevel;
            this.revisionType = revisionType;
            this.revisionDetail = revisionDetail;
            this.targetSheet = targetSheet;
            this.targetRow = targetRow;
        }
    }
    
    
    /**
     * 快速比对两个sheet（只比较关键指标，快速判断是否相同）
     * @return true表示完全相同，false表示有差异
     */
    private boolean quickCompare(Sheet baseSheet, Sheet compareSheet) {
        // 1. 比较行数
        if (baseSheet.getLastRowNum() != compareSheet.getLastRowNum()) {
            return false;
        }
        
        // 2. 比较列数（抽查第一行）
        Row baseFirstRow = baseSheet.getRow(0);
        Row compareFirstRow = compareSheet.getRow(0);
        if (baseFirstRow != null && compareFirstRow != null) {
            if (baseFirstRow.getLastCellNum() != compareFirstRow.getLastCellNum()) {
                return false;
            }
        }
        
        // 3. 比较所有行（改为全量比较，避免遗漏差异）
        // 使用快速的字符串比较而不是详细的单元格比较
        for (int i = 0; i <= baseSheet.getLastRowNum(); i++) {
            Row baseRow = baseSheet.getRow(i);
            Row compareRow = compareSheet.getRow(i);
            
            if (!rowsEqualFull(baseRow, compareRow)) {
                return false;
            }
        }
        
        // 通过完整检查，确认相同
        return true;
    }
    
    /**
     * 完整判断两行是否相等（检查所有列）
     */
    private boolean rowsEqualFull(Row row1, Row row2) {
        if (row1 == null && row2 == null) return true;
        if (row1 == null || row2 == null) return false;
        
        if (row1.getLastCellNum() != row2.getLastCellNum()) {
            return false;
        }
        
        // 检查所有列
        for (int i = 0; i < row1.getLastCellNum(); i++) {
            String val1 = getCellValueAsString(row1.getCell(i));
            String val2 = getCellValueAsString(row2.getCell(i));
            if (!Objects.equals(val1, val2)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 快速判断两行是否相等
     */
    private boolean rowsEqual(Row row1, Row row2) {
        if (row1 == null && row2 == null) return true;
        if (row1 == null || row2 == null) return false;
        
        if (row1.getLastCellNum() != row2.getLastCellNum()) {
            return false;
        }
        
        // 抽查几个关键列
        int checkColumns = Math.min(5, row1.getLastCellNum());
        for (int i = 0; i < checkColumns; i++) {
            String val1 = getCellValueAsString(row1.getCell(i));
            String val2 = getCellValueAsString(row2.getCell(i));
            if (!Objects.equals(val1, val2)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 识别sheet的部分（字段/索引/主键 或 输入/输出）
     */
    private SheetSections identifySheetSections(Sheet sheet) {
        SheetSections sections = new SheetSections();
        
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(0); // A列
                if (cell != null) {
                    String value = getCellValueAsString(cell);
                    if (value != null) {
                        value = value.trim();
                        
                        // 数据库表格式
                        if ("字段名".equals(value) && sections.fieldStart == -1) {
                            sections.fieldStart = i;
                        } else if ("索引ID".equals(value) && sections.indexStart == -1) {
                            sections.indexStart = i;
                        } else if ("主键".equals(value) && sections.pkStart == -1) {
                            sections.pkStart = i;
                        }
                        
                        // ESF接口格式
                        else if ("输入".equals(value) && sections.inputStart == -1) {
                            sections.inputStart = i;
                        } else if ("输出".equals(value) && sections.outputStart == -1) {
                            sections.outputStart = i;
                        }
                    }
                }
            }
        }
        
        // 如果没有找到分隔行，设置默认值
        if (sections.fieldStart == -1) sections.fieldStart = 1;
        if (sections.indexStart == -1) sections.indexStart = sheet.getLastRowNum() + 1;
        if (sections.pkStart == -1) sections.pkStart = sheet.getLastRowNum() + 1;
        if (sections.inputStart == -1) sections.inputStart = 1;
        if (sections.outputStart == -1) sections.outputStart = sheet.getLastRowNum() + 1;
        
        log.debug("Sheet sections: field={}, index={}, pk={}, input={}, output={}", 
                 sections.fieldStart, sections.indexStart, sections.pkStart,
                 sections.inputStart, sections.outputStart);
        
        return sections;
    }
    
    /**
     * 比较并合并一个部分（字段/索引/主键）
     * @param revisionRecords 修订记录列表（输出参数）
     * @param styleCache 样式缓存
     * @param mode 比对模式
     * @return 比较结果（添加的行数和是否有变化）
     */
    private SectionCompareResult compareSection(
            Sheet baseSheet, Sheet compareSheet, Sheet resultSheet,
            int baseStart, int baseEnd, int compareStart, int compareEnd,
            int resultRowNum, int keyColumnIndex,
            String tableName, String sectionType,
            Workbook baseWorkbook, Workbook compareWorkbook, Workbook resultWorkbook,
            List<RevisionRecord> revisionRecords,
            StyleCache styleCache,
            CompareMode mode) {
        
        int addedRows = 0;
        boolean hasChanges = false;
        
        // 获取该部分的数据映射
        Map<String, Integer> baseMap = getSectionMap(baseSheet, baseStart, baseEnd, keyColumnIndex);
        Map<String, Integer> compareMap = getSectionMap(compareSheet, compareStart, compareEnd, keyColumnIndex);
        
        Set<String> processedKeys = new HashSet<>();
        List<Row> deletedRows = new ArrayList<>();
        
        // 1. 复制标题行（部分起始行）
        if (compareStart < compareEnd) {
            Row compareHeaderRow = compareSheet.getRow(compareStart);
            if (compareHeaderRow != null) {
                Row resultRow = resultSheet.createRow(resultRowNum++);
                copyRow(compareHeaderRow, resultRow, compareWorkbook);
                addedRows++;
            }
        }
        
        // 2. 处理比较版中的数据行
        for (String key : compareMap.keySet()) {
            // 跳过标题行（忽略大小写）
            String lowerKey = key.toLowerCase();
            boolean shouldSkip = false;
            if (mode == CompareMode.ESF_INTERFACE) {
                // ESF模式：跳过"输入"和"输出"
                shouldSkip = key.isEmpty() || "输入".equals(lowerKey) || "输出".equals(lowerKey);
            } else {
                // 数据库表模式：跳过"字段名"、"索引id"、"主键"
                shouldSkip = key.isEmpty() || "字段名".equals(lowerKey) || "索引id".equals(lowerKey) || "主键".equals(lowerKey);
            }
            if (shouldSkip) {
                continue;
            }
            
            processedKeys.add(key);
            int compareRowIndex = compareMap.get(key);
            Row compareRow = compareSheet.getRow(compareRowIndex);
            
            if (!baseMap.containsKey(key)) {
                // 新增行（绿色背景+边框）
                int currentResultRow = resultRowNum;
                Row resultRow = resultSheet.createRow(resultRowNum++);
                copyRowWithStyle(compareRow, resultRow, compareWorkbook, styleCache.addedBgWithBorder);
                addedRows++;
                hasChanges = true; // 有新增，标记为有变化
                
                // 记录修订
                String revisionDetail = key;
                if (mode == CompareMode.ESF_INTERFACE) {
                    revisionDetail = key + "字段 新增";
                }
                revisionRecords.add(new RevisionRecord(
                    tableName, sectionType, "新增", revisionDetail, 
                    resultSheet.getSheetName(), currentResultRow
                ));
                
            } else {
                // 比较行内容
                int baseRowIndex = baseMap.get(key);
                Row baseRow = baseSheet.getRow(baseRowIndex);
                int currentResultRow = resultRowNum;
                Row resultRow = resultSheet.createRow(resultRowNum++);
                
                List<String> changes = new ArrayList<>();
                boolean rowChanged = compareAndMergeRow(baseRow, compareRow, resultRow, 
                                 baseWorkbook, compareWorkbook, resultWorkbook, changes, styleCache, 
                                 sectionType, mode);
                addedRows++;
                if (rowChanged) {
                    hasChanges = true; // 行有变化，标记为有变化
                    
                    // 记录修订（用换行符连接不同列的修改）
                    String changeDetail;
                    if (mode == CompareMode.ESF_INTERFACE) {
                        changeDetail = key + "字段 " + String.join(" ", changes);
                    } else {
                        changeDetail = key + ":\n" + String.join("\n", changes);
                    }
                    revisionRecords.add(new RevisionRecord(
                        tableName, sectionType, "修改", 
                        changeDetail,
                        resultSheet.getSheetName(), currentResultRow
                    ));
                    
                    log.debug("记录字段修改: 表={}, 部分={}, 字段={}, 变化数={}", 
                             tableName, sectionType, key, changes.size());
                }
            }
        }
        
        // 3. 收集基本版中有但比较版中没有的行（删除的行）
        for (String key : baseMap.keySet()) {
            String lowerKey = key.toLowerCase();
            boolean shouldSkip = false;
            if (mode == CompareMode.ESF_INTERFACE) {
                // ESF模式：跳过"输入"和"输出"
                shouldSkip = "输入".equals(lowerKey) || "输出".equals(lowerKey);
            } else {
                // 数据库表模式：跳过"字段名"、"索引id"、"主键"
                shouldSkip = "字段名".equals(lowerKey) || "索引id".equals(lowerKey) || "主键".equals(lowerKey);
            }
            if (!key.isEmpty() && !processedKeys.contains(key) && !shouldSkip) {
                int baseRowIndex = baseMap.get(key);
                Row baseRow = baseSheet.getRow(baseRowIndex);
                if (baseRow != null) {
                    deletedRows.add(baseRow);
                }
            }
        }
        
        // 4. 将删除的行添加到该部分末尾，使用灰色背景和删除线
            if (!deletedRows.isEmpty()) {
            hasChanges = true; // 有删除，标记为有变化
            for (Row deletedRow : deletedRows) {
                Cell keyCell = deletedRow.getCell(keyColumnIndex);
                String deletedKey = getCellValueAsString(keyCell);
                
                int currentResultRow = resultRowNum;
                Row resultRow = resultSheet.createRow(resultRowNum++);
                copyRowWithStyle(deletedRow, resultRow, baseWorkbook, styleCache.deletedBgWithBorder);
                addedRows++;
                
                // 记录修订
                String revisionDetail = deletedKey;
                if (mode == CompareMode.ESF_INTERFACE) {
                    revisionDetail = deletedKey + "字段 删除";
                }
                revisionRecords.add(new RevisionRecord(
                    tableName, sectionType, "删除", revisionDetail,
                    resultSheet.getSheetName(), currentResultRow
                ));
            }
        }
        
        return new SectionCompareResult(addedRows, hasChanges);
    }
    
    /**
     * 获取某个部分的数据映射（key -> 行号）
     * 对于索引和主键，忽略大小写
     */
    private Map<String, Integer> getSectionMap(Sheet sheet, int startRow, int endRow, int keyColumnIndex) {
        Map<String, Integer> map = new LinkedHashMap<>();
        
        for (int i = startRow; i < endRow && i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(keyColumnIndex);
                if (cell != null) {
                    String key = getCellValueAsString(cell);
                    if (key != null && !key.trim().isEmpty()) {
                        // 使用小写作为key（忽略大小写）
                        map.put(key.trim().toLowerCase(), i);
                    }
                }
            }
        }
        
        return map;
    }
    
    /**
     * 复制行并应用样式（通用方法，替代copyRowWithStrikethrough和copyRowWithHighlight）
     */
    private void copyRowWithStyle(Row sourceRow, Row targetRow, Workbook sourceWorkbook, CellStyle style) {
        if (sourceRow == null) return;
        
        targetRow.setHeight(sourceRow.getHeight());
        
        for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
            Cell sourceCell = sourceRow.getCell(i);
            if (sourceCell != null) {
                Cell targetCell = targetRow.createCell(i);
                copyCellValueOnly(sourceCell, targetCell);
                targetCell.setCellStyle(style);
            }
        }
    }
    
    /**
     * 复制行并添加删除线（用于删除的行）- 已废弃，使用copyRowWithStyle
     */
    private void copyRowWithStrikethrough(Row sourceRow, Row targetRow, 
                                         Workbook sourceWorkbook, Workbook targetWorkbook) {
        if (sourceRow == null) return;
        
        targetRow.setHeight(sourceRow.getHeight());
        
        for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
            Cell sourceCell = sourceRow.getCell(i);
            if (sourceCell != null) {
                Cell targetCell = targetRow.createCell(i);
                
                // 只复制值
                copyCellValueOnly(sourceCell, targetCell);
                
                // 设置删除线样式（使用缓存的样式）
                // 注意：这里需要使用StyleCache中预定义的deletedBg样式
                // 暂时简化处理
            }
        }
    }
    
    
    /**
     * 比较并合并行
     * @param changes 变更详情列表（输出参数）
     * @param styleCache 样式缓存
     * @param sectionType 部分类型（字段/索引/主键）
     * @param mode 比对模式
     * @return true表示有变化，false表示无变化
     */
    private boolean compareAndMergeRow(Row baseRow, Row compareRow, Row resultRow, 
                                      Workbook baseWorkbook, Workbook compareWorkbook, 
                                      Workbook resultWorkbook, List<String> changes,
                                      StyleCache styleCache,
                                      String sectionType,
                                      CompareMode mode) {
        boolean hasChanges = false;
        
        if (compareRow != null) {
            resultRow.setHeight(compareRow.getHeight());
        }
        
        int maxCells = Math.max(
                baseRow != null ? baseRow.getLastCellNum() : 0,
                compareRow != null ? compareRow.getLastCellNum() : 0
        );
        
        for (int i = 0; i < maxCells; i++) {
            Cell baseCell = baseRow != null ? baseRow.getCell(i) : null;
            Cell compareCell = compareRow != null ? compareRow.getCell(i) : null;
            Cell resultCell = resultRow.createCell(i);
            
            String baseValue = getCellValueAsString(baseCell);
            String compareValue = getCellValueAsString(compareCell);
            
            // 根据比对模式决定是否比对此列
            boolean shouldCompare = shouldCompareColumn(i, sectionType, mode);
            
            if (!shouldCompare) {
                // 不比对的列，直接复制比较版的值
                if (compareCell != null) {
                    copyCellValueOnly(compareCell, resultCell);
                    resultCell.setCellStyle(styleCache.normalWithBorder);
                } else {
                    resultCell.setCellStyle(styleCache.normalWithBorder);
                }
                continue;
            }
            
            // 判断值是否相等
            boolean valuesEqual = areValuesEqual(baseValue, compareValue, i, mode, sectionType);
            
            if (!valuesEqual) {
                // 值有变化，显示"旧值 ——> 新值"
                hasChanges = true;
                
                // 处理空值显示
                String oldVal = (baseValue == null || baseValue.trim().isEmpty()) ? "空" : baseValue;
                String newVal = (compareValue == null || compareValue.trim().isEmpty()) ? "空" : compareValue;
                String diffValue = oldVal + " ——> " + newVal;
                
                resultCell.setCellValue(diffValue);
                
                // 记录变更详情（如果提供了changes列表）
                if (changes != null) {
                    if (mode == CompareMode.ESF_INTERFACE) {
                        // ESF模式：使用特定的列名格式
                        String columnLabel;
                        if (i == 0) {
                            columnLabel = "英文名称";
                        } else if (i == 1) {
                            columnLabel = "中文名称";
                        } else if (i == 2) {
                            columnLabel = "数据类型";
                        } else if (i == 3) {
                            columnLabel = "是否必输";
                        } else {
                            columnLabel = getColumnName(i);
                        }
                        changes.add("【" + columnLabel + "】：" + oldVal + " --> " + newVal);
                    } else {
                        // 普通模式：使用列名
                        String columnLabel = getColumnName(i);
                        changes.add(columnLabel + "列: " + oldVal + " --> " + newVal);
                    }
                }
                
                // 设置黄色背景（使用缓存的样式）
                resultCell.setCellStyle(styleCache.modifiedBgWithBorder);
                
                log.debug("字段变化检测: 列{}, 旧值={}, 新值={}", i, baseValue, compareValue);
                
            } else {
                // 无变化，复制值并设置边框
                if (compareCell != null) {
                    copyCellValueOnly(compareCell, resultCell);
                    resultCell.setCellStyle(styleCache.normalWithBorder);
                } else {
                    resultCell.setCellStyle(styleCache.normalWithBorder);
                }
            }
        }
        
        return hasChanges;
    }
    
    /**
     * 复制行并高亮
     */
    private void copyRowWithHighlight(Row sourceRow, Row targetRow, Workbook sourceWorkbook, 
                                     Workbook targetWorkbook, IndexedColors color) {
        if (sourceRow == null) return;
        
        targetRow.setHeight(sourceRow.getHeight());
        
        for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
            Cell sourceCell = sourceRow.getCell(i);
            if (sourceCell != null) {
                Cell targetCell = targetRow.createCell(i);
                
                // 先复制完整的单元格信息
                copyCell(sourceCell, targetCell, sourceWorkbook, targetWorkbook);
                
                // 然后修改背景色为高亮色
                CellStyle originalStyle = targetCell.getCellStyle();
                CellStyle newStyle = targetWorkbook.createCellStyle();
                
                try {
                    newStyle.cloneStyleFrom(originalStyle);
                } catch (IllegalArgumentException e) {
                    // 如果克隆失败，手动复制样式
                    copyCellStyle(originalStyle, newStyle, targetWorkbook, targetWorkbook);
                }
                
                newStyle.setFillForegroundColor(color.getIndex());
                newStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                targetCell.setCellStyle(newStyle);
            }
        }
    }
    
    /**
     * 复制行（包括样式和超链接）
     */
    private void copyRow(Row sourceRow, Row targetRow, Workbook sourceWorkbook) {
        if (sourceRow == null) return;
        
        targetRow.setHeight(sourceRow.getHeight());
        
        for (int i = 0; i < sourceRow.getLastCellNum(); i++) {
            Cell sourceCell = sourceRow.getCell(i);
            if (sourceCell != null) {
                Cell targetCell = targetRow.createCell(i);
                copyCell(sourceCell, targetCell, sourceWorkbook, targetRow.getSheet().getWorkbook());
            }
        }
    }
    
    /**
     * 只复制单元格值（性能优化：不复制样式）
     * 处理所有单元格类型，包括ERROR
     */
    private void copyCellValueOnly(Cell sourceCell, Cell targetCell) {
        if (sourceCell == null || targetCell == null) return;
        
        try {
            switch (sourceCell.getCellType()) {
                case STRING:
                    targetCell.setCellValue(sourceCell.getStringCellValue());
                    break;
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(sourceCell)) {
                        targetCell.setCellValue(sourceCell.getDateCellValue());
                    } else {
                        targetCell.setCellValue(sourceCell.getNumericCellValue());
                    }
                    break;
                case BOOLEAN:
                    targetCell.setCellValue(sourceCell.getBooleanCellValue());
                    break;
                case FORMULA:
                    try {
                        // 尝试复制公式
                        targetCell.setCellFormula(sourceCell.getCellFormula());
                    } catch (Exception e) {
                        // 如果公式复制失败，尝试获取计算后的值
                        try {
                            // 尝试作为数值读取
                            targetCell.setCellValue(sourceCell.getNumericCellValue());
                        } catch (Exception ex1) {
                            try {
                                // 尝试作为字符串读取
                                targetCell.setCellValue(sourceCell.getStringCellValue());
                            } catch (Exception ex2) {
                                // 如果都失败，设置为错误标记
                                targetCell.setCellValue("#ERROR#");
                            }
                        }
                    }
                    break;
                case ERROR:
                    // ERROR类型单元格，设置错误标记
                    targetCell.setCellValue("#ERROR#");
                    break;
                case BLANK:
                    targetCell.setBlank();
                    break;
                default:
                    targetCell.setBlank();
                    break;
            }
        } catch (Exception e) {
            // 任何异常都设置为错误标记，避免中断整个比对过程
            log.warn("复制单元格值失败，设置为#ERROR#: {}", e.getMessage());
            targetCell.setCellValue("#ERROR#");
        }
    }
    
    /**
     * 完整复制单元格（包括值、样式、超链接）- 仅在必要时使用
     */
    private void copyCell(Cell sourceCell, Cell targetCell, Workbook sourceWorkbook, Workbook targetWorkbook) {
        if (sourceCell == null || targetCell == null) return;
        
        // 不复制样式，只复制值（性能优化）
        copyCellValueOnly(sourceCell, targetCell);
        
        /* 性能优化：禁用样式复制
        CellStyle newStyle = targetWorkbook.createCellStyle();
        CellStyle sourceStyle = sourceCell.getCellStyle();
        
        try {
            // 尝试直接克隆（同类型工作簿）
            newStyle.cloneStyleFrom(sourceStyle);
        } catch (IllegalArgumentException e) {
            // 如果是不同类型的工作簿，手动复制样式属性
            copyCellStyle(sourceStyle, newStyle, sourceWorkbook, targetWorkbook);
        }
        
        targetCell.setCellStyle(newStyle);
        */
        
        // 复制超链接
        if (sourceCell.getHyperlink() != null) {
            Hyperlink sourceLink = sourceCell.getHyperlink();
            Hyperlink newLink = targetWorkbook.getCreationHelper().createHyperlink(sourceLink.getType());
            
            // 处理超链接地址
            String address = sourceLink.getAddress();
            if (address != null) {
                // 如果是内部链接（sheet跳转），保持原样
                // 格式通常是：'SheetName'!A1 或 SheetName!A1
                newLink.setAddress(address);
            }
            
            if (sourceLink.getLabel() != null) {
                newLink.setLabel(sourceLink.getLabel());
            }
            targetCell.setHyperlink(newLink);
        }
        
        // 复制注释
        if (sourceCell.getCellComment() != null) {
            Comment sourceComment = sourceCell.getCellComment();
            Drawing<?> drawing = targetCell.getSheet().createDrawingPatriarch();
            CreationHelper factory = targetWorkbook.getCreationHelper();
            ClientAnchor anchor = factory.createClientAnchor();
            anchor.setCol1(targetCell.getColumnIndex());
            anchor.setCol2(targetCell.getColumnIndex() + 1);
            anchor.setRow1(targetCell.getRowIndex());
            anchor.setRow2(targetCell.getRowIndex() + 3);
            Comment newComment = drawing.createCellComment(anchor);
            newComment.setString(factory.createRichTextString(sourceComment.getString().getString()));
            targetCell.setCellComment(newComment);
        }
    }
    
    /**
     * 复制单元格样式属性（兼容不同类型的工作簿）
     */
    private void copyCellStyle(CellStyle sourceStyle, CellStyle targetStyle, 
                              Workbook sourceWorkbook, Workbook targetWorkbook) {
        // 复制对齐方式
        targetStyle.setAlignment(sourceStyle.getAlignment());
        targetStyle.setVerticalAlignment(sourceStyle.getVerticalAlignment());
        
        // 复制边框
        targetStyle.setBorderTop(sourceStyle.getBorderTop());
        targetStyle.setBorderBottom(sourceStyle.getBorderBottom());
        targetStyle.setBorderLeft(sourceStyle.getBorderLeft());
        targetStyle.setBorderRight(sourceStyle.getBorderRight());
        
        // 复制边框颜色
        targetStyle.setTopBorderColor(sourceStyle.getTopBorderColor());
        targetStyle.setBottomBorderColor(sourceStyle.getBottomBorderColor());
        targetStyle.setLeftBorderColor(sourceStyle.getLeftBorderColor());
        targetStyle.setRightBorderColor(sourceStyle.getRightBorderColor());
        
        // 不复制填充色，保持默认（避免黑色填充）
        // targetStyle.setFillPattern(sourceStyle.getFillPattern());
        // targetStyle.setFillForegroundColor(sourceStyle.getFillForegroundColor());
        // targetStyle.setFillBackgroundColor(sourceStyle.getFillBackgroundColor());
        
        // 复制字体
        Font sourceFont = sourceWorkbook.getFontAt(sourceStyle.getFontIndex());
        Font targetFont = findOrCreateFont(targetWorkbook, sourceFont);
        targetStyle.setFont(targetFont);
        
        // 复制其他属性
        targetStyle.setHidden(sourceStyle.getHidden());
        targetStyle.setLocked(sourceStyle.getLocked());
        targetStyle.setWrapText(sourceStyle.getWrapText());
        targetStyle.setIndention(sourceStyle.getIndention());
        targetStyle.setRotation(sourceStyle.getRotation());
        
        // 复制数据格式
        targetStyle.setDataFormat(
            targetWorkbook.createDataFormat().getFormat(
                sourceWorkbook.createDataFormat().getFormat(sourceStyle.getDataFormat())
            )
        );
    }
    
    /**
     * 查找或创建匹配的字体
     */
    private Font findOrCreateFont(Workbook workbook, Font sourceFont) {
        // 尝试查找已存在的匹配字体
        for (int i = 0; i < workbook.getNumberOfFonts(); i++) {
            Font font = workbook.getFontAt(i);
            if (fontsMatch(font, sourceFont)) {
                return font;
            }
        }
        
        // 创建新字体
        Font newFont = workbook.createFont();
        newFont.setFontName(sourceFont.getFontName());
        newFont.setFontHeightInPoints(sourceFont.getFontHeightInPoints());
        newFont.setBold(sourceFont.getBold());
        newFont.setItalic(sourceFont.getItalic());
        newFont.setStrikeout(sourceFont.getStrikeout());
        newFont.setColor(sourceFont.getColor());
        newFont.setUnderline(sourceFont.getUnderline());
        newFont.setTypeOffset(sourceFont.getTypeOffset());
        
        return newFont;
    }
    
    /**
     * 判断两个字体是否匹配
     */
    private boolean fontsMatch(Font font1, Font font2) {
        return font1.getFontName().equals(font2.getFontName()) &&
               font1.getFontHeightInPoints() == font2.getFontHeightInPoints() &&
               font1.getBold() == font2.getBold() &&
               font1.getItalic() == font2.getItalic() &&
               font1.getColor() == font2.getColor();
    }
    
    /**
     * 获取单元格值（字符串）
     * 安全处理所有类型，包括ERROR
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        try {
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
                        // 对于公式单元格，尝试获取计算后的值
                        CellType cachedType = cell.getCachedFormulaResultType();
                        if (cachedType == CellType.NUMERIC) {
                            double value = cell.getNumericCellValue();
                            if (value == (long) value) {
                                return String.valueOf((long) value);
                            } else {
                                return String.valueOf(value);
                            }
                        } else if (cachedType == CellType.STRING) {
                            return cell.getStringCellValue();
                        } else {
                            return cell.getCellFormula();
                        }
                    } catch (Exception e) {
                        // 如果公式计算失败，返回公式本身
                        try {
                            return cell.getCellFormula();
                        } catch (Exception ex) {
                            return "#ERROR#";
                        }
                    }
                case ERROR:
                    // ERROR类型单元格
                    return "#ERROR#";
                case BLANK:
                    return "";
                default:
                    return "";
            }
        } catch (Exception e) {
            // 任何异常都返回错误标记
            log.warn("获取单元格值失败: {}", e.getMessage());
            return "#ERROR#";
        }
    }
    
    /**
     * 判断是否应该比对某一列（根据模式只比对特定列）
     */
    private boolean shouldCompareColumn(int columnIndex, String sectionType, CompareMode mode) {
        if (mode == CompareMode.NORMAL) {
            return true; // 普通模式比对所有列
        }
        
        if (mode == CompareMode.XML_DB) {
            if ("字段".equals(sectionType)) {
                // 字段部分只比对A、B、D、F、G列
                return columnIndex == 0 ||  // A列：字段名
                       columnIndex == 1 ||  // B列：中文名
                       columnIndex == 3 ||  // D列：数据库类型
                       columnIndex == 5 ||  // F列：空值
                       columnIndex == 6;    // G列：默认值
            } else if ("索引".equals(sectionType)) {
                // 索引部分只比对A、B、C列
                return columnIndex == 0 ||  // A列：索引ID
                       columnIndex == 1 ||  // B列：字段
                       columnIndex == 2;    // C列：索引类型
            } else if ("主键".equals(sectionType)) {
                // 主键部分只比对A、B列
                return columnIndex == 0 ||  // A列：主键名称
                       columnIndex == 1;    // B列：字段
            }
        }
        
        if (mode == CompareMode.ESF_INTERFACE) {
            // ESF接口比对：只比对A、B、C、D列
            return columnIndex >= 0 && columnIndex <= 3;
            // A列：英文名
            // B列：中文名
            // C列：数据类型
            // D列：是否必输
        }
        
        // 默认比对所有列
        return true;
    }
    
    /**
     * 判断两个值是否相等（根据模式应用不同规则）
     */
    private boolean areValuesEqual(String value1, String value2, int columnIndex, CompareMode mode, String sectionType) {
        if (mode == CompareMode.NORMAL) {
            return Objects.equals(value1, value2); // 普通模式：完全匹配
        }
        
        if (mode == CompareMode.ESF_INTERFACE) {
            // ESF接口比对模式
            // C列（columnIndex == 2）：数据类型，忽略大小写比较
            if (columnIndex == 2) {
                if (value1 == null && value2 == null) return true;
                if (value1 == null || value2 == null) return false;
                return value1.trim().equalsIgnoreCase(value2.trim());
            }
            // 其他列：完全匹配
            return Objects.equals(value1, value2);
        }
        
        if (mode == CompareMode.XML_DB) {
            // 索引和主键部分：所有列都忽略大小写
            if ("索引".equals(sectionType) || "主键".equals(sectionType)) {
                if (value1 == null && value2 == null) return true;
                if (value1 == null || value2 == null) return false;
                return value1.trim().equalsIgnoreCase(value2.trim());
            }
            
            // 字段部分的特殊规则
            if ("字段".equals(sectionType)) {
                // B列：中文名，去除括号内容后比对
                if (columnIndex == 1) {
                    String normalized1 = removeParentheses(value1);
                    String normalized2 = removeParentheses(value2);
                    return Objects.equals(normalized1, normalized2);
                }
                
                // D列：数据库类型，应用等价规则
                if (columnIndex == 3) {
                    return isTypeEquivalent(value1, value2);
                }
                
                // G列：默认值，去除::后缀和括号后比对
                if (columnIndex == 6) {
                    String normalized1 = normalizeDefaultValue(value1);
                    String normalized2 = normalizeDefaultValue(value2);
                    return Objects.equals(normalized1, normalized2);
                }
            }
        }
        
        // 其他列：完全匹配
        return Objects.equals(value1, value2);
    }
    
    /**
     * 去除字符串中的括号及括号内的内容
     * 例如：集中代收(1-是,2-否) → 集中代收
     */
    private String removeParentheses(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        
        String result = value.trim();
        
        // 去除所有括号及括号内的内容
        // 支持中英文括号：()、（）、[]、【】
        result = result.replaceAll("\\([^)]*\\)", "");     // 英文圆括号
        result = result.replaceAll("（[^）]*）", "");       // 中文圆括号
        result = result.replaceAll("\\[[^\\]]*\\]", "");   // 英文方括号
        result = result.replaceAll("【[^】]*】", "");       // 中文方括号
        
        return result.trim();
    }
    
    /**
     * 判断两个数据库类型是否等价
     */
    private boolean isTypeEquivalent(String type1, String type2) {
        if (Objects.equals(type1, type2)) {
            return true; // 完全相同
        }
        
        if (type1 == null || type2 == null) {
            return false;
        }
        
        String t1 = type1.toLowerCase().trim();
        String t2 = type2.toLowerCase().trim();
        
        // date 和 timestamp 等价
        if ((t1.contains("date") && t2.contains("timestamp")) ||
            (t1.contains("timestamp") && t2.contains("date"))) {
            return true;
        }
        
        // numeric 和 decimal 等价
        if ((t1.contains("numeric") && t2.contains("decimal")) ||
            (t1.contains("decimal") && t2.contains("numeric"))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 规范化默认值（去除::类型后缀和括号）
     * 支持：0, 0::numeric, (0)::numeric 都认为是 0
     */
    private String normalizeDefaultValue(String defaultValue) {
        if (defaultValue == null || defaultValue.trim().isEmpty()) {
            return "";
        }
        
        String normalized = defaultValue.trim();
        
        // 先去除外层所有括号（例如：((0))::numeric → 0::numeric）
        while (normalized.startsWith("(") && normalized.contains(")::")) {
            int closeIndex = normalized.indexOf(")");
            if (closeIndex > 0) {
                String inner = normalized.substring(1, closeIndex);
                String after = normalized.substring(closeIndex + 1);
                normalized = inner + after;
                normalized = normalized.trim();
            } else {
                break;
            }
        }
        
        // 处理没有::的括号情况（例如：(0) → 0）
        if (normalized.startsWith("(") && normalized.endsWith(")") && !normalized.contains("::")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        
        // 去除 ::类型 后缀
        // 例如：0::bigint → 0, NULL::character varying → NULL
        if (normalized.contains("::")) {
            normalized = normalized.substring(0, normalized.indexOf("::")).trim();
        }
        
        return normalized;
    }
    
    /**
     * 创建修订记录sheet（支持不同模式的格式）
     */
    private void createRevisionSheet(Workbook workbook, List<RevisionRecord> revisionRecords, String sheetName, CompareMode mode) {
        if (mode == CompareMode.ESF_INTERFACE) {
            createEsfRevisionSheet(workbook, revisionRecords, sheetName);
        } else {
            createNormalRevisionSheet(workbook, revisionRecords, sheetName);
        }
    }
    
    /**
     * 创建ESF格式的修订记录sheet
     */
    private void createEsfRevisionSheet(Workbook workbook, List<RevisionRecord> revisionRecords, String sheetName) {
        if (revisionRecords.isEmpty()) {
            log.info("没有修订记录，跳过创建");
            return;
        }
        
        log.info("创建ESF修订记录sheet，共 {} 条记录", revisionRecords.size());
        
        // 按交易码（表名）分组汇总
        Map<String, EsfTransactionChange> transactionChanges = groupByTransaction(revisionRecords);
        
        Sheet revisionSheet = workbook.createSheet(sheetName);
        // ESF模式下，修订记录sheet放在第一页
        workbook.setSheetOrder(sheetName, 0);
        
        // 创建标题行
        Row headerRow = revisionSheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        createCellWithStyle(headerRow, 0, "序号", headerStyle);
        createCellWithStyle(headerRow, 1, "交易码", headerStyle);
        createCellWithStyle(headerRow, 2, "新增/删除/修改", headerStyle);
        createCellWithStyle(headerRow, 3, "修订内容", headerStyle);
        
        // 设置列宽
        revisionSheet.setColumnWidth(0, 2000);   // 序号
        revisionSheet.setColumnWidth(1, 6000);   // 交易码
        revisionSheet.setColumnWidth(2, 3000);   // 状态
        revisionSheet.setColumnWidth(3, 20000);  // 修订内容
        
        // 创建样式
        CellStyle addedStyle = createBorderStyle(workbook);
        addedStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        addedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        CellStyle deletedStyle = createBorderStyle(workbook);
        deletedStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        deletedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        CellStyle modifiedStyle = createBorderStyle(workbook);
        modifiedStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        modifiedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        CellStyle borderStyle = createBorderStyle(workbook);
        CellStyle wrapStyle = createBorderStyle(workbook);
        wrapStyle.setWrapText(true);
        wrapStyle.setVerticalAlignment(VerticalAlignment.TOP);
        
        // 填充数据
        int rowNum = 1;
        int seq = 1;
        for (Map.Entry<String, EsfTransactionChange> entry : transactionChanges.entrySet()) {
            String transactionCode = entry.getKey();
            EsfTransactionChange change = entry.getValue();
            
            Row row = revisionSheet.createRow(rowNum++);
            
            // A列：序号
            createCellWithStyle(row, 0, String.valueOf(seq++), borderStyle);
            
            // B列：交易码
            createCellWithStyle(row, 1, transactionCode, borderStyle);
            
            // C列：状态
            CellStyle statusStyle = change.status.equals("新增") ? addedStyle :
                                   change.status.equals("删除") ? deletedStyle : modifiedStyle;
            createCellWithStyle(row, 2, change.status, statusStyle);
            
            // D列：修订内容
            createCellWithStyle(row, 3, change.content, wrapStyle);
            
            // 自动调整行高
            int lineCount = change.content.split("\n").length;
            row.setHeightInPoints(Math.max(20, lineCount * 15));
        }
        
        log.info("ESF修订记录sheet创建完成");
    }
    
    /**
     * ESF交易变更信息
     */
    private static class EsfTransactionChange {
        String status;   // 新增/删除/修改
        String content;  // 修订内容
        
        EsfTransactionChange(String status, String content) {
            this.status = status;
            this.content = content;
        }
    }
    
    /**
     * 创建标题样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        
        return style;
    }
    
    /**
     * 创建带边框的样式
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
     * 创建带样式的单元格
     */
    private Cell createCellWithStyle(Row row, int columnIndex, String value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return cell;
    }
    
    /**
     * 按交易码分组汇总修订记录
     */
    private Map<String, EsfTransactionChange> groupByTransaction(List<RevisionRecord> records) {
        Map<String, EsfTransactionChange> map = new LinkedHashMap<>();
        
        for (RevisionRecord record : records) {
            String transactionCode = record.tableName;  // 表名即交易码（sheet名称）
            
            // ESF模式下，接口级别的变更（新增/删除）
            if ("接口".equals(record.revisionLevel)) {
                String status = record.revisionType;  // 新增/删除/修改
                // 修订内容格式：交易码 + " 接口" + 状态
                String content = transactionCode + " 接口" + status;
                map.put(transactionCode, new EsfTransactionChange(status, content));
            } else {
                // 字段级别的变更
                EsfTransactionChange existing = map.get(transactionCode);
                if (existing == null) {
                    existing = new EsfTransactionChange("修改", "");
                    map.put(transactionCode, existing);
                }
                
                // 追加修订内容
                if (!existing.content.isEmpty()) {
                    existing.content += "\n";
                }
                
                // 格式化修订内容：添加"输入："或"输出："前缀
                String sectionPrefix = "";
                if ("输入".equals(record.revisionLevel)) {
                    // 检查是否已经有"输入："前缀
                    if (!existing.content.contains("输入：")) {
                        sectionPrefix = "输入：\n";
                    }
                } else if ("输出".equals(record.revisionLevel)) {
                    // 检查是否已经有"输出："前缀
                    if (!existing.content.contains("输出：")) {
                        // 如果已经有"输入："部分，先添加换行
                        if (existing.content.contains("输入：")) {
                            sectionPrefix = "\n输出：\n";
                        } else {
                            sectionPrefix = "输出：\n";
                        }
                    }
                }
                
                existing.content += sectionPrefix + record.revisionDetail;
            }
        }
        
        return map;
    }
    
    /**
     * 创建普通格式的修订记录sheet
     */
    private void createNormalRevisionSheet(Workbook workbook, List<RevisionRecord> revisionRecords, String sheetName) {
        if (revisionRecords.isEmpty()) {
            log.info("没有修订记录，跳过创建修订记录sheet");
            return;
        }
        
        log.info("创建修订记录sheet，共 {} 条记录", revisionRecords.size());
        
        // 创建修订记录sheet（放在第二个位置）
        Sheet revisionSheet = workbook.createSheet(sheetName);
        workbook.setSheetOrder(sheetName, 1);
        
        // 创建标题行
        Row headerRow = revisionSheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setFontHeightInPoints((short) 12);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        // 添加边框
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        
        String[] headers = {"表名", "修订级别", "修订方式", "修订明细"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        
        // 设置列宽
        revisionSheet.setColumnWidth(0, 6000);  // 表名
        revisionSheet.setColumnWidth(1, 3000);  // 修订级别
        revisionSheet.setColumnWidth(2, 3000);  // 修订方式
        revisionSheet.setColumnWidth(3, 15000); // 修订明细
        
        // 创建基础单元格样式（带边框）
        CellStyle baseCellStyle = workbook.createCellStyle();
        baseCellStyle.setBorderTop(BorderStyle.THIN);
        baseCellStyle.setBorderBottom(BorderStyle.THIN);
        baseCellStyle.setBorderLeft(BorderStyle.THIN);
        baseCellStyle.setBorderRight(BorderStyle.THIN);
        baseCellStyle.setVerticalAlignment(VerticalAlignment.TOP);
        
        // 填充修订记录
        int rowNum = 1;
        for (RevisionRecord record : revisionRecords) {
            Row row = revisionSheet.createRow(rowNum);
            
            // A列：表名
            Cell tableCell = row.createCell(0);
            tableCell.setCellValue(record.tableName);
            CellStyle tableStyle = workbook.createCellStyle();
            tableStyle.cloneStyleFrom(baseCellStyle);
            tableCell.setCellStyle(tableStyle);
            
            // B列：修订级别
            Cell levelCell = row.createCell(1);
            levelCell.setCellValue(record.revisionLevel);
            CellStyle levelStyle = workbook.createCellStyle();
            levelStyle.cloneStyleFrom(baseCellStyle);
            levelCell.setCellStyle(levelStyle);
            
            // C列：修订方式（带颜色）
            Cell typeCell = row.createCell(2);
            typeCell.setCellValue(record.revisionType);
            
            CellStyle typeStyle = workbook.createCellStyle();
            typeStyle.cloneStyleFrom(baseCellStyle);
            if ("新增".equals(record.revisionType)) {
                typeStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            } else if ("删除".equals(record.revisionType)) {
                typeStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            } else if ("修改".equals(record.revisionType)) {
                typeStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            }
            typeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            typeCell.setCellStyle(typeStyle);
            
            // D列：修订明细（带超链接，自动换行）
            Cell detailCell = row.createCell(3);
            detailCell.setCellValue(record.revisionDetail);
            
            CellStyle detailStyle = workbook.createCellStyle();
            detailStyle.cloneStyleFrom(baseCellStyle);
            detailStyle.setWrapText(true); // 自动换行
            
            // 创建超链接到目标sheet的目标行
            if (record.targetSheet != null && record.targetRow >= 0) {
                Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
                String address = "'" + record.targetSheet + "'!A" + (record.targetRow + 1);
                link.setAddress(address);
                link.setLabel(record.revisionDetail);
                detailCell.setHyperlink(link);
                
                // 设置超链接样式（保留边框，添加蓝色字体）
                Font linkFont = workbook.createFont();
                linkFont.setColor(IndexedColors.BLUE.getIndex());
                linkFont.setUnderline(Font.U_SINGLE);
                detailStyle.setFont(linkFont);
                
                // 在目标sheet的A列添加反向超链接
                addBackLinkToRevision(workbook, record.targetSheet, record.targetRow, rowNum, sheetName);
            }
            
            detailCell.setCellStyle(detailStyle);
            
            // 根据内容计算行高（每个换行符增加一行的高度）
            int lineCount = record.revisionDetail.split("\n").length;
            row.setHeightInPoints(Math.max(20, lineCount * 15)); // 每行15磅，最小20磅
            
            rowNum++;
        }
        
        // 自动调整列宽（根据内容）
        for (int i = 0; i < 4; i++) {
            try {
                revisionSheet.autoSizeColumn(i);
                // 在自动调整的基础上再加一点空间
                int currentWidth = revisionSheet.getColumnWidth(i);
                revisionSheet.setColumnWidth(i, currentWidth + 500);
            } catch (Exception e) {
                log.warn("自动调整列宽失败: {}", e.getMessage());
            }
        }
        
        // 确保D列有足够的宽度显示修订明细
        if (revisionSheet.getColumnWidth(3) < 15000) {
            revisionSheet.setColumnWidth(3, 15000);
        }
        
        log.info("修订记录sheet创建完成，共 {} 条记录", revisionRecords.size());
    }
    
    /**
     * 在目标sheet的A列添加反向超链接到修订记录/差异结果
     */
    private void addBackLinkToRevision(Workbook workbook, String targetSheetName, 
                                      int targetRowIndex, int revisionRowNum, String revisionSheetName) {
        Sheet targetSheet = workbook.getSheet(targetSheetName);
        if (targetSheet == null) {
            log.warn("目标sheet不存在: {}", targetSheetName);
            return;
        }
        
        Row targetRow = targetSheet.getRow(targetRowIndex);
        if (targetRow == null) {
            log.warn("目标行不存在: sheet={}, row={}", targetSheetName, targetRowIndex);
            return;
        }
        
        Cell aCell = targetRow.getCell(0);
        if (aCell == null) {
            aCell = targetRow.createCell(0);
        }
        
        // 创建超链接到修订记录/差异结果sheet
        Hyperlink link = workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
        String address = "'" + revisionSheetName + "'!A" + (revisionRowNum + 1);
        link.setAddress(address);
        link.setLabel("→" + revisionSheetName);
        
        // 如果A列已经有内容，将超链接添加到现有内容
        String originalValue = getCellValueAsString(aCell);
        if (originalValue != null && !originalValue.isEmpty() && 
            !originalValue.startsWith("→") && !originalValue.contains("→" + revisionSheetName)) {
            // 保留原有值，不添加超链接文本
            aCell.setHyperlink(link);
        } else {
            aCell.setCellValue(originalValue != null && !originalValue.isEmpty() ? originalValue : "→" + revisionSheetName);
            aCell.setHyperlink(link);
        }
    }
    
    /**
     * 在工作簿中查找实际的sheet名称
     * 考虑首字母大写和长度截取的情况
     */
    private String findActualSheetName(Workbook workbook, String tableName) {
        // 先尝试直接查找
        if (workbook.getSheet(tableName) != null) {
            return tableName;
        }
        
        // 尝试规范化后的名称（首字母大写）
        String normalized = normalizeSheetName(tableName);
        if (workbook.getSheet(normalized) != null) {
            return normalized;
        }
        
        // 遍历所有sheet，忽略大小写匹配
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            String sheetName = workbook.getSheetAt(i).getSheetName();
            if (sheetName.equalsIgnoreCase(tableName) || 
                sheetName.equalsIgnoreCase(normalized)) {
                return sheetName;
            }
        }
        
        // 如果还是找不到，返回规范化的名称
        return normalized;
    }
    
    /**
     * 修复各个sheet中指向总览的反向超链接
     */
    private void fixBackLinksToOverview(Workbook workbook, Map<String, Integer> tableRowMap, String overviewSheetName) {
        Sheet overviewSheet = workbook.getSheet(overviewSheetName);
        if (overviewSheet == null) {
            log.warn("未找到{}sheet，跳过反向超链接修复", overviewSheetName);
            return;
        }
        
        int fixedCount = 0;
        
        // 遍历所有sheet（除了总览）
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName();
            
            if (overviewSheetName.equals(sheetName)) {
                continue;
            }
            
            // 检查该sheet在总览中的行号
            Integer rowNum = tableRowMap.get(sheetName);
            if (rowNum == null) {
                log.debug("Sheet {} 在表总览映射中未找到", sheetName);
                continue;
            }
            
            // 遍历该sheet的所有行和所有列，查找指向表总览的超链接
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row != null) {
                    for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                        Cell cell = row.getCell(colIndex);
                        if (cell != null && cell.getHyperlink() != null) {
                            Hyperlink link = cell.getHyperlink();
                            String address = link.getAddress();
                            
                            // 检查是否是文档内部链接，且指向表总览
                            if (address != null && link.getType() == HyperlinkType.DOCUMENT) {
                            // 检查是否包含总览关键字，或者以单引号包裹的sheet名称
                            if (address.contains(overviewSheetName) || 
                                address.contains("'" + overviewSheetName + "'") ||
                                address.toLowerCase().contains("overview")) {
                                
                                // 创建新的超链接，指向总览的正确行
                                Hyperlink newLink = workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
                                // 格式：'总览'!B行号
                                String newAddress = "'" + overviewSheetName + "'!B" + (rowNum + 1); // Excel行号从1开始
                                    newLink.setAddress(newAddress);
                                    
                                    if (link.getLabel() != null) {
                                        newLink.setLabel(link.getLabel());
                                    } else {
                                        newLink.setLabel("返回" + overviewSheetName);
                                    }
                                    
                                    cell.setHyperlink(newLink);
                                    fixedCount++;
                                    log.debug("修复超链接：Sheet={}, Cell={}{} -> {}", 
                                            sheetName, getColumnName(colIndex), rowIndex + 1, newAddress);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        log.info("完成反向超链接修复，共修复 {} 个超链接", fixedCount);
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
     * 生成文件名
     */
    private String generateFileName(String originalFileName) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String baseName = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        return baseName + "_比对结果_" + timestamp + ".xlsx";
    }
    
    @Override
    public File getResultFile(String fileName) {
        return new File(RESULT_DIR, fileName);
    }
}



