package com.sunline.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.entity.DictChangeLog;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.entity.DictVersion;
import com.sunline.dict.service.*;
import com.sunline.dict.util.DataHashUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据变更追踪控制器
 */
@RestController
@RequestMapping("/api/dict/change")
public class DictChangeController {
    
    private static final Logger log = LoggerFactory.getLogger(DictChangeController.class);
    
    @Autowired
    private DictDataService dictDataService;
    
    @Autowired
    private DictVersionService versionService;
    
    @Autowired
    private DictChangeLogService changeLogService;
    
    @Autowired
    private ChangeDetector changeDetector;
    
    @Autowired
    private ProgressService progressService;
    
    /**
     * 预览导入变更（不实际导入）
     */
    @PostMapping("/preview")
    public Result<ChangeReport> previewImport(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
                return Result.error("只支持Excel文件(.xlsx或.xls)");
            }
            
            log.info("开始预览Excel变更: {}", filename);
            
            // 1. 解析Excel
            List<DictData> newDataList = parseExcel(file);
            log.info("解析到{}条Excel数据", newDataList.size());
            
            // 2. 获取现有数据（不包括已删除的）
            List<DictData> oldDataList = dictDataService.getAllActiveData();
            log.info("数据库中有{}条现有数据", oldDataList.size());
            
            // 3. 对比变更
            ChangeReport report = changeDetector.detectChanges(newDataList, oldDataList);
            
            log.info("变更统计: 新增={}, 修改={}, 删除={}, 不变={}", 
                     report.getSummary().getNewCount(),
                     report.getSummary().getUpdateCount(),
                     report.getSummary().getDeleteCount(),
                     report.getSummary().getUnchangedCount());
            
            return Result.success("预览成功", report);
            
        } catch (Exception e) {
            log.error("预览导入失败", e);
            return Result.error("预览失败: " + e.getMessage());
        }
    }
    
    /**
     * 确认导入（实际执行）
     */
    @PostMapping("/confirm")
    @Transactional(rollbackFor = Exception.class)
    public Result<DictVersion> confirmImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clientId", required = false) String clientId) {
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            log.info("开始确认导入: {}, clientId: {}", file.getOriginalFilename(), clientId);
            
            // 发送进度：开始
            sendProgress(clientId, 0, "parsing", "开始解析Excel...");
            
            // 1. 创建新版本
            DictVersion version = versionService.createVersion(file);
            
            // 2. 解析Excel
            sendProgress(clientId, 10, "parsing", "正在解析Excel文件...");
            List<DictData> newDataList = parseExcel(file);
            
            // 3. 获取现有数据
            sendProgress(clientId, 30, "comparing", "正在对比数据变更...");
            List<DictData> oldDataList = dictDataService.getAllActiveData();
            
            // 4. 检测变更
            sendProgress(clientId, 40, "comparing", "正在检测变更...");
            ChangeReport report = changeDetector.detectChanges(newDataList, oldDataList);
            
            // 5. 应用变更
            sendProgress(clientId, 50, "importing", "正在应用变更...");
            applyChangesWithProgress(report, version.getId(), clientId);
            
            // 6. 保存变更日志
            sendProgress(clientId, 90, "saving", "正在保存变更日志...");
            changeLogService.saveChangeLogs(version.getId(), report);
            
            // 7. 更新版本统计
            sendProgress(clientId, 95, "finishing", "正在完成导入...");
            versionService.updateVersionStats(
                version.getId(),
                report.getSummary().getNewCount(),
                report.getSummary().getUpdateCount(),
                report.getSummary().getDeleteCount(),
                report.getSummary().getUnchangedCount()
            );
            
            log.info("导入完成: 版本={}, 新增={}, 修改={}, 删除={}", 
                     version.getVersionNumber(),
                     report.getSummary().getNewCount(),
                     report.getSummary().getUpdateCount(),
                     report.getSummary().getDeleteCount());
            
            // 发送完成
            sendProgress(clientId, 100, "completed", "✓ 导入成功！");
            
            // 返回最新的版本信息
            version = versionService.getById(version.getId());
            return Result.success("导入成功", version);
            
        } catch (Exception e) {
            log.error("确认导入失败", e);
            sendProgress(clientId, 0, "error", "✗ 导入失败: " + e.getMessage());
            return Result.error("导入失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送进度信息
     */
    private void sendProgress(String clientId, int percentage, String status, String message) {
        if (clientId != null && progressService != null) {
            com.sunline.dict.common.ImportProgress progress = 
                new com.sunline.dict.common.ImportProgress(100, percentage, status, message);
            progressService.sendProgress(clientId, progress);
        }
    }
    
    /**
     * 应用变更（带进度）- 分批处理
     */
    private void applyChangesWithProgress(ChangeReport report, Long versionId, String clientId) {
        LocalDateTime now = LocalDateTime.now();
        int batchSize = 500; // 每批500条
        
        // 计算总数据量和各阶段占比
        int totalRecords = report.getNewList().size() + 
                          report.getUpdateList().size() + 
                          report.getDeleteList().size() + 
                          report.getUnchangedList().size();
        
        int processedRecords = 0;
        int baseProgress = 50; // 基础进度50%，前面是解析和对比
        int totalProgress = 40; // 导入阶段占40%进度
        
        // 1. 处理新增 - 分批
        if (!report.getNewList().isEmpty()) {
            log.info("开始新增{}条数据", report.getNewList().size());
            @SuppressWarnings("unchecked")
            List<DictData> newList = (List<DictData>) (List<?>) report.getNewList();
            
            for (DictData data : newList) {
                data.setVersionId(versionId);
                data.setDataHash(DataHashUtils.calculateHash(data));
                data.setChangeType("NEW");
                data.setIsDeleted(0);
                data.setCreateTime(now);
                data.setUpdateTime(now);
            }
            
            // 分批插入
            int totalBatches = (int) Math.ceil((double) newList.size() / batchSize);
            for (int i = 0; i < newList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, newList.size());
                List<DictData> batch = newList.subList(i, end);
                dictDataService.saveBatch(batch);
                
                processedRecords += batch.size();
                int currentProgress = baseProgress + (int)((double)processedRecords / totalRecords * totalProgress);
                int currentBatch = (i / batchSize) + 1;
                
                sendProgress(clientId, currentProgress, "importing", 
                    String.format("正在新增数据: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                        currentBatch, totalBatches, processedRecords, totalRecords,
                        (int)((double)processedRecords / totalRecords * 100)));
                
                log.info("新增第{}/{}批数据，本批{}条", currentBatch, totalBatches, batch.size());
            }
        }
        
        // 2. 处理修改 - 分批
        if (!report.getUpdateList().isEmpty()) {
            log.info("开始更新{}条数据", report.getUpdateList().size());
            @SuppressWarnings("unchecked")
            List<DictData> updateList = (List<DictData>) (List<?>) report.getUpdateList();
            
            for (DictData data : updateList) {
                data.setVersionId(versionId);
                data.setDataHash(DataHashUtils.calculateHash(data));
                data.setChangeType("UPDATE");
                data.setUpdateTime(now);
            }
            
            // 分批更新
            int totalBatches = (int) Math.ceil((double) updateList.size() / batchSize);
            for (int i = 0; i < updateList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, updateList.size());
                List<DictData> batch = updateList.subList(i, end);
                dictDataService.updateBatchById(batch);
                
                processedRecords += batch.size();
                int currentProgress = baseProgress + (int)((double)processedRecords / totalRecords * totalProgress);
                int currentBatch = (i / batchSize) + 1;
                
                sendProgress(clientId, currentProgress, "importing", 
                    String.format("正在更新数据: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                        currentBatch, totalBatches, processedRecords, totalRecords,
                        (int)((double)processedRecords / totalRecords * 100)));
                
                log.info("更新第{}/{}批数据，本批{}条", currentBatch, totalBatches, batch.size());
            }
        }
        
        // 3. 处理删除（软删除）- 分批
        if (!report.getDeleteList().isEmpty()) {
            log.info("开始删除{}条数据", report.getDeleteList().size());
            @SuppressWarnings("unchecked")
            List<DictData> deleteList = (List<DictData>) (List<?>) report.getDeleteList();
            
            for (DictData data : deleteList) {
                data.setVersionId(versionId);
                data.setChangeType("DELETE");
                data.setIsDeleted(1);
                data.setDeletedAt(now);
                data.setUpdateTime(now);
            }
            
            // 分批删除
            int totalBatches = (int) Math.ceil((double) deleteList.size() / batchSize);
            for (int i = 0; i < deleteList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, deleteList.size());
                List<DictData> batch = deleteList.subList(i, end);
                dictDataService.updateBatchById(batch);
                
                processedRecords += batch.size();
                int currentProgress = baseProgress + (int)((double)processedRecords / totalRecords * totalProgress);
                int currentBatch = (i / batchSize) + 1;
                
                sendProgress(clientId, currentProgress, "importing", 
                    String.format("正在删除数据: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                        currentBatch, totalBatches, processedRecords, totalRecords,
                        (int)((double)processedRecords / totalRecords * 100)));
                
                log.info("删除第{}/{}批数据，本批{}条", currentBatch, totalBatches, batch.size());
            }
        }
        
        // 4. 处理不变的数据（更新版本ID）- 分批
        if (!report.getUnchangedList().isEmpty()) {
            log.info("开始处理{}条不变数据", report.getUnchangedList().size());
            @SuppressWarnings("unchecked")
            List<DictData> unchangedList = (List<DictData>) (List<?>) report.getUnchangedList();
            
            for (DictData data : unchangedList) {
                data.setVersionId(versionId);
                data.setChangeType("UNCHANGED");
                data.setUpdateTime(now);
            }
            
            // 分批更新
            int totalBatches = (int) Math.ceil((double) unchangedList.size() / batchSize);
            for (int i = 0; i < unchangedList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, unchangedList.size());
                List<DictData> batch = unchangedList.subList(i, end);
                dictDataService.updateBatchById(batch);
                
                processedRecords += batch.size();
                int currentProgress = baseProgress + (int)((double)processedRecords / totalRecords * totalProgress);
                int currentBatch = (i / batchSize) + 1;
                
                sendProgress(clientId, currentProgress, "importing", 
                    String.format("正在处理不变数据: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                        currentBatch, totalBatches, processedRecords, totalRecords,
                        (int)((double)processedRecords / totalRecords * 100)));
                
                log.info("处理第{}/{}批不变数据，本批{}条", currentBatch, totalBatches, batch.size());
            }
        }
        
        log.info("数据导入完成，共处理{}条记录", processedRecords);
    }
    
    /**
     * 解析Excel文件
     */
    private List<DictData> parseExcel(MultipartFile file) throws Exception {
        List<DictData> dataList = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            
            for (int i = 1; i < rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                
                try {
                    DictData dictData = new DictData();
                    dictData.setSortOrder(i);
                    
                    // 读取各列数据
                    dictData.setDataItemCode(getCellValue(row.getCell(0)));
                    dictData.setEnglishAbbr(getCellValue(row.getCell(1)));
                    dictData.setChineseName(getCellValue(row.getCell(2)));
                    dictData.setDictAttr(getCellValue(row.getCell(3)));
                    dictData.setDomainChineseName(getCellValue(row.getCell(4)));
                    dictData.setDataType(getCellValue(row.getCell(5)));
                    dictData.setDataFormat(getCellValue(row.getCell(6)));
                    dictData.setJavaEsfName(getCellValue(row.getCell(7)));
                    dictData.setEsfDataFormat(getCellValue(row.getCell(8)));
                    dictData.setGaussdbDataFormat(getCellValue(row.getCell(9)));
                    dictData.setGoldendbDataFormat(getCellValue(row.getCell(10)));
                    
                    // 只添加有效数据（数据项编号不为空）
                    if (StringUtils.hasText(dictData.getDataItemCode())) {
                        dataList.add(dictData);
                    }
                } catch (Exception e) {
                    log.error("解析第{}行数据失败: {}", i + 1, e.getMessage());
                }
            }
        }
        
        return dataList;
    }
    
    /**
     * 获取单元格值
     */
    private String getCellValue(Cell cell) {
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
     * 获取版本历史
     */
    @GetMapping("/versions")
    public Result<Page<DictVersion>> getVersionHistory(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size) {
        try {
            Page<DictVersion> page = versionService.getVersionHistory(current, size);
            return Result.success(page);
        } catch (Exception e) {
            log.error("查询版本历史失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据版本ID查询变更日志
     */
    @GetMapping("/logs/{versionId}")
    public Result<List<DictChangeLog>> getChangeLogs(@PathVariable Long versionId) {
        try {
            List<DictChangeLog> logs = changeLogService.getByVersionId(versionId);
            return Result.success(logs);
        } catch (Exception e) {
            log.error("查询变更日志失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据数据项编号查询变更历史
     * @deprecated 请使用 ChangeLogController 中的相同方法
     */
    @Deprecated
    @GetMapping("/history/{dataItemCode}")
    public Result<List<DictChangeLog>> getChangeHistory(@PathVariable String dataItemCode) {
        try {
            List<DictChangeLog> logs = changeLogService.getByDataItemCode(dataItemCode);
            return Result.success(logs);
        } catch (Exception e) {
            log.error("查询变更历史失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
}

