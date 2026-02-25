package com.sunline.dict.controller;

import com.sunline.dict.common.ImportProgress;
import com.sunline.dict.common.Result;
import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.dto.MultiSheetChangeReport;
import com.sunline.dict.dto.MultiSheetData;
import com.sunline.dict.entity.CodeExtensionData;
import com.sunline.dict.entity.CodeExtensionDataIng;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.entity.DictDataIng;
import com.sunline.dict.entity.DomainData;
import com.sunline.dict.entity.DomainDataIng;
import com.sunline.dict.dto.ValidationResult;
import com.sunline.dict.entity.ImportHistory;
import com.sunline.dict.service.ChangeDetector;
import com.sunline.dict.service.CodeExtensionChangeLogService;
import com.sunline.dict.service.CodeExtensionDataIngService;
import com.sunline.dict.service.CodeExtensionDataService;
import com.sunline.dict.service.DictChangeLogService;
import com.sunline.dict.service.DictDataIngService;
import com.sunline.dict.service.DictDataService;
import com.sunline.dict.service.DomainDataIngService;
import com.sunline.dict.service.DomainChangeLogService;
import com.sunline.dict.service.DomainDataService;
import com.sunline.dict.service.EnumMappingService;
import com.sunline.dict.service.ExcelValidationService;
import com.sunline.dict.service.ExportService;
import com.sunline.dict.service.ImportHistoryService;
import com.sunline.dict.service.ProgressService;
import com.sunline.dict.util.PinyinUtils;
import com.sunline.dict.config.ExcelImportConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 多Sheet数据导入Controller
 */
@RestController
@RequestMapping("/api/multi-sheet")
@CrossOrigin
public class MultiSheetController {
    
    private static final Logger log = LoggerFactory.getLogger(MultiSheetController.class);
    
    @Autowired
    private DictDataService dictDataService;
    
    @Autowired
    private DomainDataService domainDataService;
    
    @Autowired
    private CodeExtensionDataService codeExtensionDataService;
    
    @Autowired
    private ChangeDetector changeDetector;
    
    @Autowired
    private ProgressService progressService;
    
    @Autowired
    private ExcelValidationService validationService;
    
    @Autowired
    private DictChangeLogService changeLogService;
    
    @Autowired
    private ImportHistoryService importHistoryService;
    
    @Autowired
    private DomainChangeLogService domainChangeLogService;
    
    @Autowired
    private CodeExtensionChangeLogService codeExtensionChangeLogService;
    
    @Autowired
    private EnumMappingService enumMappingService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    @Autowired
    private ExportService exportService;
    
    @Autowired
    private DictDataIngService dictDataIngService;
    
    @Autowired
    private DomainDataIngService domainDataIngService;
    
    @Autowired
    private CodeExtensionDataIngService codeExtensionDataIngService;
    
    /**
     * SSE进度推送端点
     */
    @GetMapping("/progress/{clientId}")
    public SseEmitter progress(@PathVariable String clientId) {
        log.info("客户端连接进度推送: {}", clientId);
        return progressService.createEmitter(clientId);
    }
    
    /**
     * 预览Excel中的所有Sheet数据
     */
    @PostMapping("/preview")
    public Result<MultiSheetData> previewMultiSheet(@RequestParam("file") MultipartFile file) {
        try {
            log.info("======================================");
            log.info("开始预览多Sheet Excel文件: {}", file.getOriginalFilename());
            log.info("文件大小: {} bytes", file.getSize());
            
            MultiSheetData multiSheetData = parseMultiSheetExcel(file);
            
            log.info("预览完成: {}", multiSheetData);
            log.info("======================================");
            
            return Result.success("预览成功", multiSheetData);
            
        } catch (Exception e) {
            log.error("预览失败", e);
            return Result.error("预览失败: " + e.getMessage());
        }
    }
    
    /**
     * 变更追踪预览 - 分析多Sheet数据变更
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/change-preview")
    public Result<MultiSheetChangeReport> changePreview(@RequestParam("file") MultipartFile file) {
        try {
            log.info("======================================");
            log.info("开始分析多Sheet变更: {}", file.getOriginalFilename());
            
            // 1. 解析Excel
            MultiSheetData newData = parseMultiSheetExcel(file);
            
            // 2. 数据校验
            log.info("开始数据校验...");
            ValidationResult validationResult = validateMultiSheetData(newData);
            
            if (validationResult.hasErrors()) {
                log.warn("数据校验失败，发现 {} 个错误", validationResult.getErrors().size());
                Result<ValidationResult> errorResult = new Result<>(500, "数据校验失败", validationResult);
                return (Result<MultiSheetChangeReport>) (Result<?>) errorResult;
            }
            log.info("✓ 数据校验通过");
            
            // 3. 变更分析
            MultiSheetChangeReport multiSheetChangeReport = new MultiSheetChangeReport();
            
            // 2. 分析字典技术衍生表变更
            if (newData.getDictDataList() != null && !newData.getDictDataList().isEmpty()) {
                log.info("分析字典技术衍生表变更...");
                List<DictData> oldDictData = dictDataService.getAllActiveData();
                ChangeReport dictChangeReport = changeDetector.detectChanges(
                    newData.getDictDataList(), oldDictData);
                multiSheetChangeReport.setDictChangeReport(dictChangeReport);
                log.info("✓ 字典表变更: 新增={}, 修改={}, 删除={}, 不变={}", 
                    dictChangeReport.getSummary().getNewCount(),
                    dictChangeReport.getSummary().getUpdateCount(),
                    dictChangeReport.getSummary().getDeleteCount(),
                    dictChangeReport.getSummary().getUnchangedCount());
            }
            
            // 3. 分析域清单变更
            if (newData.getDomainDataList() != null && !newData.getDomainDataList().isEmpty()) {
                log.info("分析域清单变更...");
                List<DomainData> oldDomainData = domainDataService.list();
                ChangeReport domainChangeReport = detectDomainDataChanges(
                    newData.getDomainDataList(), oldDomainData);
                multiSheetChangeReport.setDomainChangeReport(domainChangeReport);
                log.info("✓ 域清单变更: 新增={}, 修改={}, 删除={}, 不变={}", 
                    domainChangeReport.getSummary().getNewCount(),
                    domainChangeReport.getSummary().getUpdateCount(),
                    domainChangeReport.getSummary().getDeleteCount(),
                    domainChangeReport.getSummary().getUnchangedCount());
            }
            
            // 4. 分析代码扩展清单变更
            if (newData.getCodeExtensionDataList() != null && !newData.getCodeExtensionDataList().isEmpty()) {
                log.info("分析代码扩展清单变更...");
                List<CodeExtensionData> oldCodeData = codeExtensionDataService.list();
                ChangeReport codeChangeReport = detectCodeExtensionDataChanges(
                    newData.getCodeExtensionDataList(), oldCodeData);
                multiSheetChangeReport.setCodeExtensionChangeReport(codeChangeReport);
                log.info("✓ 代码扩展清单变更: 新增={}, 修改={}, 删除={}, 不变={}", 
                    codeChangeReport.getSummary().getNewCount(),
                    codeChangeReport.getSummary().getUpdateCount(),
                    codeChangeReport.getSummary().getDeleteCount(),
                    codeChangeReport.getSummary().getUnchangedCount());
            }
            
            log.info("======================================");
            log.info("多Sheet变更分析完成");
            
            return Result.success("变更分析成功", multiSheetChangeReport);
            
        } catch (Exception e) {
            log.error("变更分析失败", e);
            return Result.error("变更分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 导出分析报告
     */
    @PostMapping("/export-analysis-report")
    public ResponseEntity<byte[]> exportAnalysisReport(@RequestParam("file") MultipartFile file) {
        try {
            log.info("开始导出分析报告");
            
            // 重新分析Excel文件以获取变更报告
            MultiSheetData newData = parseMultiSheetExcel(file);
            ValidationResult validationResult = validateMultiSheetData(newData);
            
            if (validationResult.hasErrors()) {
                log.warn("数据校验失败，无法导出分析报告");
                return ResponseEntity.status(400)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(("{\"error\":\"数据校验失败，无法导出分析报告\"}").getBytes("UTF-8"));
            }
            
            // 重新分析变更
            MultiSheetChangeReport multiSheetChangeReport = new MultiSheetChangeReport();
            
            // 分析字典技术衍生表变更
            if (newData.getDictDataList() != null && !newData.getDictDataList().isEmpty()) {
                List<DictData> oldDictData = dictDataService.getAllActiveData();
                ChangeReport dictChangeReport = changeDetector.detectChanges(
                    newData.getDictDataList(), oldDictData);
                multiSheetChangeReport.setDictChangeReport(dictChangeReport);
            }
            
            // 分析域清单变更
            if (newData.getDomainDataList() != null && !newData.getDomainDataList().isEmpty()) {
                List<DomainData> oldDomainData = domainDataService.list();
                ChangeReport domainChangeReport = detectDomainDataChanges(
                    newData.getDomainDataList(), oldDomainData);
                multiSheetChangeReport.setDomainChangeReport(domainChangeReport);
            }
            
            // 分析代码扩展清单变更
            if (newData.getCodeExtensionDataList() != null && !newData.getCodeExtensionDataList().isEmpty()) {
                List<CodeExtensionData> oldCodeData = codeExtensionDataService.list();
                ChangeReport codeChangeReport = detectCodeExtensionDataChanges(
                    newData.getCodeExtensionDataList(), oldCodeData);
                multiSheetChangeReport.setCodeExtensionChangeReport(codeChangeReport);
            }
            
            byte[] excelBytes = exportService.exportChangeAnalysisReport(multiSheetChangeReport);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "分析报告.xlsx");
            
            log.info("分析报告导出完成，文件大小: {} bytes", excelBytes.length);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excelBytes);
                    
        } catch (Exception e) {
            log.error("导出分析报告失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 确认导入 - 将数据保存到数据库（带进度条，单一事务）
     */
    @PostMapping("/confirm")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> confirmImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clientId", required = false) String clientId,
            @RequestParam(value = "changeDescription", required = false) String changeDescription,
            @RequestParam(value = "password", required = false) String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("Excel导入失败：口令验证失败");
                return Result.error("导入失败：口令错误，请输入正确的6位数字口令");
            }
            
            log.info("======================================");
            log.info("开始确认导入多Sheet Excel文件: {}, clientId: {}", file.getOriginalFilename(), clientId);
            
            // 0. 发送初始进度，并等待SSE连接完全建立
            sendProgress(clientId, 0, "preparing", "正在准备导入...");
            try {
                Thread.sleep(800); // 等待800ms，确保前端SSE连接已建立
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 1. 解析Excel
            sendProgress(clientId, 10, "parsing", "正在解析Excel文件...");
            MultiSheetData multiSheetData = parseMultiSheetExcel(file);
            
            // 2. 变更检测（用于记录变更日志）
            sendProgress(clientId, 15, "analyzing", "正在分析数据变更...");
            MultiSheetChangeReport changeReport = new MultiSheetChangeReport();
            
            // 检测字典变更
            if (multiSheetData.getDictDataList() != null && !multiSheetData.getDictDataList().isEmpty()) {
                List<DictData> oldDictData = dictDataService.getAllActiveData();
                ChangeReport dictChangeReport = changeDetector.detectChanges(
                    multiSheetData.getDictDataList(), oldDictData);
                changeReport.setDictChangeReport(dictChangeReport);
                log.info("✓ 字典变更: 新增={}, 修改={}, 删除={}", 
                    dictChangeReport.getSummary().getNewCount(),
                    dictChangeReport.getSummary().getUpdateCount(),
                    dictChangeReport.getSummary().getDeleteCount());
            }
            
            // 检测域清单变更
            if (multiSheetData.getDomainDataList() != null && !multiSheetData.getDomainDataList().isEmpty()) {
                List<DomainData> oldDomainData = domainDataService.list();
                ChangeReport domainChangeReport = detectDomainDataChanges(
                    multiSheetData.getDomainDataList(), oldDomainData);
                changeReport.setDomainChangeReport(domainChangeReport);
                log.info("✓ 域清单变更: 新增={}, 修改={}, 删除={}", 
                    domainChangeReport.getSummary().getNewCount(),
                    domainChangeReport.getSummary().getUpdateCount(),
                    domainChangeReport.getSummary().getDeleteCount());
            }
            
            // 检测代码扩展清单变更
            if (multiSheetData.getCodeExtensionDataList() != null && !multiSheetData.getCodeExtensionDataList().isEmpty()) {
                List<CodeExtensionData> oldCodeData = codeExtensionDataService.list();
                ChangeReport codeChangeReport = detectCodeExtensionDataChanges(
                    multiSheetData.getCodeExtensionDataList(), oldCodeData);
                changeReport.setCodeExtensionChangeReport(codeChangeReport);
                log.info("✓ 代码扩展清单变更: 新增={}, 修改={}, 删除={}", 
                    codeChangeReport.getSummary().getNewCount(),
                    codeChangeReport.getSummary().getUpdateCount(),
                    codeChangeReport.getSummary().getDeleteCount());
            }
            
            // 计算总记录数
            int totalRecords = multiSheetData.getDictDataCount() + 
                             multiSheetData.getDomainDataCount() + 
                             multiSheetData.getCodeExtensionDataCount();
            
            log.info("总记录数: {}", totalRecords);
            sendProgress(clientId, 20, "importing", String.format("解析完成，准备导入 %d 条数据...", totalRecords));
            
            Map<String, Object> result = new HashMap<>();
            int processedRecords = 0;
            int batchSize = 500;
            
            // 2. 导入字典技术衍生表数据
            if (multiSheetData.getDictDataList() != null && !multiSheetData.getDictDataList().isEmpty()) {
                log.info("开始导入字典技术衍生表数据: {} 条", multiSheetData.getDictDataCount());
                sendProgress(clientId, 25, "importing", "正在清空字典表旧数据...");
                
                // 清空原有数据
                dictDataService.remove(null);
                
                // 批量插入（500条一批，但在同一个事务中）
                int dictCount = 0;
                List<DictData> dictList = multiSheetData.getDictDataList();
                int totalBatches = (int) Math.ceil((double) dictList.size() / batchSize);
                
                for (int i = 0; i < dictList.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, dictList.size());
                    List<DictData> batch = dictList.subList(i, end);
                    dictDataService.saveBatch(batch);
                    dictCount += batch.size();
                    processedRecords += batch.size();
                    
                    int currentBatch = (i / batchSize) + 1;
                    int progress = 25 + (int)((double)processedRecords / totalRecords * 50);
                    sendProgress(clientId, progress, "importing", 
                        String.format("正在导入字典表: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                            currentBatch, totalBatches, processedRecords, totalRecords,
                            (int)((double)processedRecords / totalRecords * 100)));
                    
                    // 添加小延迟，让进度更新更明显
                    try {
                        Thread.sleep(100); // 每批次延迟100ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                result.put("dictDataCount", dictCount);
                log.info("✓ 字典技术衍生表导入完成: {} 条", dictCount);
            } else {
                result.put("dictDataCount", 0);
            }
            
            // 3. 导入域清单数据
            if (multiSheetData.getDomainDataList() != null && !multiSheetData.getDomainDataList().isEmpty()) {
                log.info("开始导入域清单数据: {} 条", multiSheetData.getDomainDataCount());
                
                // 应用枚举映射：自动填充域英文简称
                List<DomainData> domainList = multiSheetData.getDomainDataList();
                for (DomainData domain : domainList) {
                    // 只有域组为"代码类"且域英文简称为空时才应用映射
                    if ("代码类".equals(domain.getDomainGroup()) && 
                        (domain.getEnglishAbbr() == null || domain.getEnglishAbbr().trim().isEmpty())) {
                        String chineseName = domain.getChineseName();
                        if (chineseName != null && !chineseName.trim().isEmpty()) {
                            String mappedAbbr = enumMappingService.getDomainEnglishAbbrByChineseName(chineseName);
                            if (mappedAbbr != null && !mappedAbbr.trim().isEmpty()) {
                                domain.setEnglishAbbr(mappedAbbr);
                                log.debug("域清单映射: {} -> {}", chineseName, mappedAbbr);
                            }
                        }
                    }
                }
                
                // 清空原有数据
                domainDataService.remove(null);
                
                // 批量插入（500条一批）
                int domainCount = 0;
                int totalBatches = (int) Math.ceil((double) domainList.size() / batchSize);
                
                for (int i = 0; i < domainList.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, domainList.size());
                    List<DomainData> batch = domainList.subList(i, end);
                    domainDataService.saveBatch(batch);
                    domainCount += batch.size();
                    processedRecords += batch.size();
                    
                    int currentBatch = (i / batchSize) + 1;
                    int progress = 25 + (int)((double)processedRecords / totalRecords * 50);
                    sendProgress(clientId, progress, "importing", 
                        String.format("正在导入域清单: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                            currentBatch, totalBatches, processedRecords, totalRecords,
                            (int)((double)processedRecords / totalRecords * 100)));
                    
                    // 添加小延迟，让进度更新更明显
                    try {
                        Thread.sleep(100); // 每批次延迟100ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                result.put("domainDataCount", domainCount);
                log.info("✓ 域清单导入完成: {} 条", domainCount);
            } else {
                result.put("domainDataCount", 0);
            }
            
            // 4. 导入代码扩展清单数据
            if (multiSheetData.getCodeExtensionDataList() != null && !multiSheetData.getCodeExtensionDataList().isEmpty()) {
                log.info("开始导入代码扩展清单数据: {} 条", multiSheetData.getCodeExtensionDataCount());
                
                // 应用枚举映射：自动填充代码含义英文简称
                List<CodeExtensionData> codeList = multiSheetData.getCodeExtensionDataList();
                for (CodeExtensionData code : codeList) {
                    // 只有代码含义英文简称为空时才应用映射
                    if (code.getCodeEnglishAbbr() == null || code.getCodeEnglishAbbr().trim().isEmpty()) {
                        String domainChineseName = code.getCodeDomainChineseName();
                        String codeValue = code.getCodeValue();
                        if (domainChineseName != null && !domainChineseName.trim().isEmpty() &&
                            codeValue != null && !codeValue.trim().isEmpty()) {
                            String mappedAbbr = enumMappingService.getEnumFieldIdByDomainAndCodeValue(
                                domainChineseName, codeValue);
                            if (mappedAbbr != null && !mappedAbbr.trim().isEmpty()) {
                                code.setCodeEnglishAbbr(mappedAbbr);
                                log.debug("代码扩展清单映射: {}+{} -> {}",
                                    domainChineseName, codeValue, mappedAbbr);
                            }
                        }
                    }
                }
                
                // 清空原有数据
                codeExtensionDataService.remove(null);
                
                // 批量插入（500条一批）
                int codeCount = 0;
                int totalBatches = (int) Math.ceil((double) codeList.size() / batchSize);
                
                for (int i = 0; i < codeList.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, codeList.size());
                    List<CodeExtensionData> batch = codeList.subList(i, end);
                    codeExtensionDataService.saveBatch(batch);
                    codeCount += batch.size();
                    processedRecords += batch.size();
                    
                    int currentBatch = (i / batchSize) + 1;
                    int progress = 25 + (int)((double)processedRecords / totalRecords * 50);
                    sendProgress(clientId, progress, "importing", 
                        String.format("正在导入代码扩展清单: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                            currentBatch, totalBatches, processedRecords, totalRecords,
                            (int)((double)processedRecords / totalRecords * 100)));
                    
                    // 添加小延迟，让进度更新更明显
                    try {
                        Thread.sleep(100); // 每批次延迟100ms
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                result.put("codeExtensionDataCount", codeCount);
                log.info("✓ 代码扩展清单导入完成: {} 条", codeCount);
            } else {
                result.put("codeExtensionDataCount", 0);
            }
            
            int totalCount = result.values().stream()
                .filter(v -> v instanceof Integer)
                .mapToInt(v -> ((Integer) v).intValue())
                .sum();
            result.put("totalCount", totalCount);
            
            // 5. 保存导入历史记录
            sendProgress(clientId, 90, "saving", "正在保存导入历史记录...");
            log.info("======================================");
            log.info("开始保存导入历史记录");
            
            ImportHistory history = new ImportHistory();
            history.setImportTime(LocalDateTime.now());
            history.setFileName(file.getOriginalFilename());
            history.setOperator("system"); // TODO: 从登录信息获取
            // 如果没有提供变更说明，使用默认值
            String finalDescription = changeDescription != null && !changeDescription.trim().isEmpty() 
                ? changeDescription.trim() 
                : "数据导入";
            history.setChangeDescription(finalDescription);
            log.info("变更说明: {}", finalDescription);
            
            // 字典统计
            if (changeReport.getDictChangeReport() != null) {
                history.setDictNewCount(changeReport.getDictChangeReport().getSummary().getNewCount());
                history.setDictUpdateCount(changeReport.getDictChangeReport().getSummary().getUpdateCount());
                history.setDictDeleteCount(changeReport.getDictChangeReport().getSummary().getDeleteCount());
            } else {
                history.setDictNewCount(0);
                history.setDictUpdateCount(0);
                history.setDictDeleteCount(0);
            }
            log.info("字典统计 - 新增:{}, 修改:{}, 删除:{}", 
                history.getDictNewCount(), history.getDictUpdateCount(), history.getDictDeleteCount());
            
            // 域清单统计
            if (changeReport.getDomainChangeReport() != null) {
                history.setDomainNewCount(changeReport.getDomainChangeReport().getSummary().getNewCount());
                history.setDomainUpdateCount(changeReport.getDomainChangeReport().getSummary().getUpdateCount());
                history.setDomainDeleteCount(changeReport.getDomainChangeReport().getSummary().getDeleteCount());
            } else {
                history.setDomainNewCount(0);
                history.setDomainUpdateCount(0);
                history.setDomainDeleteCount(0);
            }
            log.info("域清单统计 - 新增:{}, 修改:{}, 删除:{}", 
                history.getDomainNewCount(), history.getDomainUpdateCount(), history.getDomainDeleteCount());
            
            // 代码扩展清单统计
            if (changeReport.getCodeExtensionChangeReport() != null) {
                history.setCodeNewCount(changeReport.getCodeExtensionChangeReport().getSummary().getNewCount());
                history.setCodeUpdateCount(changeReport.getCodeExtensionChangeReport().getSummary().getUpdateCount());
                history.setCodeDeleteCount(changeReport.getCodeExtensionChangeReport().getSummary().getDeleteCount());
            } else {
                history.setCodeNewCount(0);
                history.setCodeUpdateCount(0);
                history.setCodeDeleteCount(0);
            }
            log.info("代码扩展清单统计 - 新增:{}, 修改:{}, 删除:{}", 
                history.getCodeNewCount(), history.getCodeUpdateCount(), history.getCodeDeleteCount());
            
            history.setTotalCount(totalCount);
            history.setStatus("SUCCESS");
            history.setCreateTime(LocalDateTime.now());
            
            log.info("准备保存导入历史到数据库...");
            boolean saved = importHistoryService.save(history);
            log.info("保存结果: {}, 导入历史ID: {}", saved ? "成功" : "失败", history.getId());
            
            if (!saved || history.getId() == null) {
                throw new RuntimeException("保存导入历史失败！");
            }
            
            log.info("✓ 导入历史记录已保存，ID: {}", history.getId());
            
            // 保存字典变更日志（关联导入历史ID）
            if (changeReport.getDictChangeReport() != null) {
                log.info("开始保存字典变更日志，关联导入历史ID: {}", history.getId());
                changeLogService.saveChangeLogs(history.getId(), changeReport.getDictChangeReport());
                log.info("✓ 字典变更日志已保存");
            }
            
            // 保存域清单变更日志
            if (changeReport.getDomainChangeReport() != null) {
                log.info("开始保存域清单变更日志，关联导入历史ID: {}", history.getId());
                domainChangeLogService.saveChangeLogs(history.getId(), changeReport.getDomainChangeReport());
                log.info("✓ 域清单变更日志已保存");
            }
            
            // 保存代码扩展清单变更日志
            if (changeReport.getCodeExtensionChangeReport() != null) {
                log.info("开始保存代码扩展清单变更日志，关联导入历史ID: {}", history.getId());
                codeExtensionChangeLogService.saveChangeLogs(history.getId(), changeReport.getCodeExtensionChangeReport());
                log.info("✓ 代码扩展清单变更日志已保存");
            }
            
            log.info("======================================");
            
            // 6. 根据最新导入的贯标数据剔除在途数据中的匹配项
            sendProgress(clientId, 92, "cleaning", "正在剔除在途数据中的匹配项...");
            RemovedIngData removedData = removeMatchingIngData(multiSheetData);
            int removedCount = removedData.getTotalCount();
            log.info("✓ 已剔除在途数据中的匹配项: {} 条", removedCount);
            
            // 7. 如果有剔除的数据，生成Excel文件
            String removedExcelPath = null;
            if (removedCount > 0) {
                sendProgress(clientId, 93, "exporting", "正在生成剔除数据Excel文件...");
                try {
                    byte[] excelBytes = generateRemovedDataExcel(removedData);
                    // 将Excel文件保存到静态资源目录
                    String fileName = "剔除在途数据清单_" + System.currentTimeMillis() + ".xlsx";
                    java.nio.file.Path staticPath = java.nio.file.Paths.get(
                        System.getProperty("user.dir"), 
                        "src", "main", "resources", "static", "removed", fileName
                    );
                    // 确保目录存在
                    java.nio.file.Files.createDirectories(staticPath.getParent());
                    java.nio.file.Files.write(staticPath, excelBytes);
                    removedExcelPath = "/removed/" + fileName;
                    log.info("✓ 剔除数据Excel文件已生成: {}", removedExcelPath);
                    sendProgress(clientId, 94, "exporting", "剔除数据Excel文件已生成，文件路径: " + removedExcelPath);
                } catch (Exception e) {
                    log.error("生成剔除数据Excel文件失败", e);
                    // 不影响主流程，只记录错误
                }
            }
            
            sendProgress(clientId, 95, "importing", "数据导入完成，正在提交事务...");
            
            log.info("======================================");
            log.info("多Sheet数据导入完成！");
            log.info("字典技术衍生表: {} 条", result.get("dictDataCount"));
            log.info("域清单: {} 条", result.get("domainDataCount"));
            log.info("代码扩展清单: {} 条", result.get("codeExtensionDataCount"));
            log.info("剔除在途数据: {} 条", removedCount);
            log.info("总计: {} 条", totalCount);
            log.info("======================================");
            
            String finalMessage = String.format("导入成功！共导入 %d 条数据", totalCount);
            if (removedCount > 0) {
                finalMessage += String.format("，剔除在途数据 %d 条", removedCount);
                if (removedExcelPath != null) {
                    finalMessage += "，已生成剔除数据Excel文件";
                }
            }
            sendProgress(clientId, 100, "completed", finalMessage);
            
            // 在返回结果中包含剔除数据Excel文件路径
            if (removedExcelPath != null) {
                result.put("removedExcelPath", removedExcelPath);
                result.put("removedCount", removedCount);
            }
            
            return Result.success("导入成功", result);
            
        } catch (Exception e) {
            log.error("确认导入失败", e);
            sendProgress(clientId, 0, "error", "导入失败: " + e.getMessage());
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 发送进度信息
     */
    private void sendProgress(String clientId, int progress, String status, String message) {
        if (clientId != null && !clientId.isEmpty()) {
            try {
                log.info("发送进度 - clientId: {}, progress: {}%, status: {}, message: {}", 
                    clientId, progress, status, message);
                ImportProgress importProgress = new ImportProgress();
                importProgress.setPercentage(progress);
                importProgress.setStatus(status);
                importProgress.setMessage(message);
                progressService.sendProgress(clientId, importProgress);
                log.debug("进度发送成功");
            } catch (Exception e) {
                log.error("发送进度失败 - clientId: {}, error: {}", clientId, e.getMessage(), e);
            }
        } else {
            log.warn("clientId为空，无法发送进度");
        }
    }
    
    /**
     * 检测域清单数据变更
     * 使用域中文名称（D列）作为唯一标识
     * 只校验域中文名称和数据格式（H列）
     * - 如果域中文名称变化：处理为新增+删除
     * - 如果域中文名称不变，但数据格式变化：处理为修改
     */
    private ChangeReport detectDomainDataChanges(List<DomainData> newDataList, List<DomainData> oldDataList) {
        ChangeReport report = new ChangeReport();
        
        // 1. 构建旧数据Map（以域中文名称为Key）
        Map<String, DomainData> oldDataMap = oldDataList.stream()
            .filter(data -> data.getChineseName() != null && !data.getChineseName().trim().isEmpty())
            .collect(Collectors.toMap(
                data -> data.getChineseName().trim(),
                Function.identity(),
                (existing, replacement) -> existing
            ));
        
        // 2. 构建新数据Map（以域中文名称为Key）
        Map<String, DomainData> newDataMap = newDataList.stream()
            .filter(data -> data.getChineseName() != null && !data.getChineseName().trim().isEmpty())
            .collect(Collectors.toMap(
                data -> data.getChineseName().trim(),
                Function.identity(),
                (existing, replacement) -> existing
            ));
        
        // 3. 检测新增和修改（遍历新数据）
        for (DomainData newData : newDataList) {
            String chineseName = newData.getChineseName();
            if (chineseName == null || chineseName.trim().isEmpty()) {
                continue;
            }
            
            String chineseNameTrimmed = chineseName.trim();
            DomainData oldData = oldDataMap.get(chineseNameTrimmed);
            
            if (oldData == null) {
                // 域中文名称在旧数据中不存在 -> 新增
                report.addNew(newData);
            } else {
                // 域中文名称存在，只检查数据格式（H列）是否变化
                String newDataFormat = newData.getDataFormat() != null ? newData.getDataFormat().trim() : "";
                String oldDataFormat = oldData.getDataFormat() != null ? oldData.getDataFormat().trim() : "";
                
                if (!Objects.equals(newDataFormat, oldDataFormat)) {
                    // 数据格式变化 -> 修改
                    newData.setId(oldData.getId()); // 保留原ID
                    report.addUpdate(newData, oldData);
                } else {
                    // 数据格式未变化 -> 不变
                    newData.setId(oldData.getId()); // 保留原ID
                    report.addUnchanged(newData);
                }
            }
        }
        
        // 4. 检测删除（在旧数据中但不在新数据中）
        for (DomainData oldData : oldDataList) {
            String chineseName = oldData.getChineseName();
            if (chineseName != null && !chineseName.trim().isEmpty()) {
                String chineseNameTrimmed = chineseName.trim();
                if (!newDataMap.containsKey(chineseNameTrimmed)) {
                    // 域中文名称在新数据中不存在 -> 删除
                    report.addDelete(oldData);
                }
            }
        }
        
        // 5. 生成摘要
        report.generateSummary();
        
        return report;
    }
    
    /**
     * 检测代码扩展清单数据变更
     * 使用 B列-代码域中文名称 + C列-代码取值 作为唯一键
     * 只校验以下字段：
     * - B列-代码域中文名称
     * - C列-代码取值
     * - D列-代码含义中文名称
     * - G列-代码描述
     * 如果联合主键（B列+C列）发生变化，处理为新增+删除
     * 如果联合主键不变，但其他校验字段变化，处理为修改
     */
    private ChangeReport detectCodeExtensionDataChanges(List<CodeExtensionData> newDataList, List<CodeExtensionData> oldDataList) {
        ChangeReport report = new ChangeReport();
        
        // 1. 构建旧数据Map（以"代码域中文名称+代码取值"为Key）
        Map<String, CodeExtensionData> oldDataMap = oldDataList.stream()
            .filter(data -> data.getCodeDomainChineseName() != null && !data.getCodeDomainChineseName().trim().isEmpty() &&
                           data.getCodeValue() != null && !data.getCodeValue().trim().isEmpty())
            .collect(Collectors.toMap(
                data -> (data.getCodeDomainChineseName().trim() + "|" + data.getCodeValue().trim()),
                Function.identity(),
                (existing, replacement) -> existing
            ));
        
        // 2. 构建新数据Map（以"代码域中文名称+代码取值"为Key）
        Map<String, CodeExtensionData> newDataMap = newDataList.stream()
            .filter(data -> data.getCodeDomainChineseName() != null && !data.getCodeDomainChineseName().trim().isEmpty() &&
                           data.getCodeValue() != null && !data.getCodeValue().trim().isEmpty())
            .collect(Collectors.toMap(
                data -> (data.getCodeDomainChineseName().trim() + "|" + data.getCodeValue().trim()),
                Function.identity(),
                (existing, replacement) -> existing
            ));
        
        // 3. 检测新增和修改（遍历新数据）
        for (CodeExtensionData newData : newDataList) {
            String domainChineseName = newData.getCodeDomainChineseName();
            String codeValue = newData.getCodeValue();
            
            if (domainChineseName == null || domainChineseName.trim().isEmpty() ||
                codeValue == null || codeValue.trim().isEmpty()) {
                continue;
            }
            
            String key = domainChineseName.trim() + "|" + codeValue.trim();
            CodeExtensionData oldData = oldDataMap.get(key);
            
            if (oldData == null) {
                // 联合主键在旧数据中不存在 -> 新增
                report.addNew(newData);
            } else {
                // 联合主键存在，检查需要校验的字段是否变化
                // 只校验：B列-代码域中文名称、C列-代码取值、D列-代码含义中文名称、G列-代码描述
                String newDomainChineseName = newData.getCodeDomainChineseName() != null ? newData.getCodeDomainChineseName().trim() : "";
                String oldDomainChineseName = oldData.getCodeDomainChineseName() != null ? oldData.getCodeDomainChineseName().trim() : "";
                String newCodeValue = newData.getCodeValue() != null ? newData.getCodeValue().trim() : "";
                String oldCodeValue = oldData.getCodeValue() != null ? oldData.getCodeValue().trim() : "";
                String newValueChineseName = newData.getValueChineseName() != null ? newData.getValueChineseName().trim() : "";
                String oldValueChineseName = oldData.getValueChineseName() != null ? oldData.getValueChineseName().trim() : "";
                String newCodeDescription = newData.getCodeDescription() != null ? newData.getCodeDescription().trim() : "";
                String oldCodeDescription = oldData.getCodeDescription() != null ? oldData.getCodeDescription().trim() : "";
                
                boolean changed = !Objects.equals(newDomainChineseName, oldDomainChineseName) ||
                                !Objects.equals(newCodeValue, oldCodeValue) ||
                                !Objects.equals(newValueChineseName, oldValueChineseName) ||
                                !Objects.equals(newCodeDescription, oldCodeDescription);
                
                if (changed) {
                    // 校验字段变化 -> 修改
                    newData.setId(oldData.getId()); // 保留原ID
                    report.addUpdate(newData, oldData);
                } else {
                    // 校验字段未变化 -> 不变
                    newData.setId(oldData.getId()); // 保留原ID
                    report.addUnchanged(newData);
                }
            }
        }
        
        // 4. 检测删除（在旧数据中但不在新数据中）
        for (CodeExtensionData oldData : oldDataList) {
            String domainChineseName = oldData.getCodeDomainChineseName();
            String codeValue = oldData.getCodeValue();
            
            if (domainChineseName != null && !domainChineseName.trim().isEmpty() &&
                codeValue != null && !codeValue.trim().isEmpty()) {
                String key = domainChineseName.trim() + "|" + codeValue.trim();
                if (!newDataMap.containsKey(key)) {
                    // 联合主键在新数据中不存在 -> 删除
                    report.addDelete(oldData);
                }
            }
        }
        
        // 5. 生成摘要
        report.generateSummary();
        
        return report;
    }
    
    /**
     * 解析多Sheet Excel文件
     */
    private MultiSheetData parseMultiSheetExcel(MultipartFile file) throws Exception {
        List<DictData> dictDataList = new ArrayList<>();
        List<DomainData> domainDataList = new ArrayList<>();
        List<CodeExtensionData> codeExtensionDataList = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            int sheetCount = workbook.getNumberOfSheets();
            log.info("Excel包含 {} 个工作表", sheetCount);
            
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                log.info("正在解析工作表 [{}]: {}", i + 1, sheetName);
                
                if (sheetName.contains("字典技术衍生表") || sheetName.contains("字典") && !sheetName.contains("域")) {
                    dictDataList = parseDictDataSheet(sheet);
                    log.info("✓ 解析字典技术衍生表: {} 条数据", dictDataList.size());
                } else if (sheetName.contains("域清单") || sheetName.equals("域清单")) {
                    domainDataList = parseDomainDataSheet(sheet);
                    log.info("✓ 解析域清单: {} 条数据", domainDataList.size());
                } else if (sheetName.contains("代码扩展清单") || sheetName.contains("代码扩展")) {
                    codeExtensionDataList = parseCodeExtensionDataSheet(sheet);
                    log.info("✓ 解析代码扩展清单: {} 条数据", codeExtensionDataList.size());
                } else {
                    log.warn("未识别的工作表: {}", sheetName);
                }
            }
        }
        
        return new MultiSheetData(dictDataList, domainDataList, codeExtensionDataList);
    }
    
    /**
     * 解析字典技术衍生表
     */
    private List<DictData> parseDictDataSheet(Sheet sheet) {
        List<DictData> dataList = new ArrayList<>();
        int rowCount = sheet.getPhysicalNumberOfRows();
        
        // 跳过表头，从第二行开始
        for (int i = 1; i < rowCount; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            DictData dictData = new DictData();
            dictData.setSortOrder(i);
            dictData.setDataItemCode(getCellValue(row.getCell(0)));
            dictData.setEnglishAbbr(getCellValue(row.getCell(1)));
            dictData.setEnglishName(getCellValue(row.getCell(2)));
            dictData.setChineseName(getCellValue(row.getCell(3)));
            dictData.setDictAttr(getCellValue(row.getCell(4)));
            dictData.setDomainChineseName(getCellValue(row.getCell(5)));
            dictData.setDataType(getCellValue(row.getCell(6)));
            dictData.setDataFormat(getCellValue(row.getCell(7)));
            dictData.setValueRange(getCellValue(row.getCell(8)));
            dictData.setJavaEsfName(getCellValue(row.getCell(9)));
            dictData.setEsfDataFormat(getCellValue(row.getCell(10)));
            dictData.setGaussdbDataFormat(getCellValue(row.getCell(11)));
            dictData.setGoldendbDataFormat(getCellValue(row.getCell(12)));
            dictData.setCreateTime(LocalDateTime.now());
            dictData.setUpdateTime(LocalDateTime.now());
            
            dataList.add(dictData);
        }
        
        return dataList;
    }
    
    /**
     * 解析域清单
     * 严格13个字段：域编号、域类型、域组、域中文名称、域英文名称、域英文简称、域定义、数据格式、域规则、取值范围、域来源、来源编号、备注
     */
    private List<DomainData> parseDomainDataSheet(Sheet sheet) {
        List<DomainData> dataList = new ArrayList<>();
        int rowCount = sheet.getPhysicalNumberOfRows();
        
        // 跳过表头，从第二行开始
        for (int i = 1; i < rowCount; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            DomainData domainData = new DomainData();
            
            // 1. 域编号 (A列)
            String domainNumberStr = getCellValue(row.getCell(0));
            if (domainNumberStr != null && !domainNumberStr.isEmpty()) {
                try {
                    domainData.setDomainNumber(Integer.parseInt(domainNumberStr));
                } catch (NumberFormatException e) {
                    log.warn("域编号格式错误: {}", domainNumberStr);
                }
            }
            
            // 2. 域类型 (B列)
            domainData.setDomainType(getCellValue(row.getCell(1)));
            
            // 3. 域组 (C列)
            domainData.setDomainGroup(getCellValue(row.getCell(2)));
            
            // 4. 域中文名称 (D列)
            String chineseName = getCellValue(row.getCell(3));
            domainData.setChineseName(chineseName);
            
            // 5. 域英文名称 (E列) - 自动生成拼音
            String generatedEnglishName = PinyinUtils.toUpperCaseWithUnderscore(chineseName);
            if (generatedEnglishName != null && !generatedEnglishName.isEmpty()) {
                domainData.setEnglishName(generatedEnglishName);
            } else {
                domainData.setEnglishName(getCellValue(row.getCell(4)));
            }
            
            // 6. 域英文简称 (F列)
            domainData.setEnglishAbbr(getCellValue(row.getCell(5)));
            
            // 7. 域定义 (G列)
            domainData.setDomainDefinition(getCellValue(row.getCell(6)));
            
            // 8. 数据格式 (H列)
            domainData.setDataFormat(getCellValue(row.getCell(7)));
            
            // 9. 域规则 (I列)
            domainData.setDomainRule(getCellValue(row.getCell(8)));
            
            // 10. 取值范围 (J列)
            domainData.setValueRange(getCellValue(row.getCell(9)));
            
            // 11. 域来源 (K列)
            domainData.setDomainSource(getCellValue(row.getCell(10)));
            
            // 12. 来源编号 (L列)
            domainData.setSourceNumber(getCellValue(row.getCell(11)));
            
            // 13. 备注 (M列)
            domainData.setRemark(getCellValue(row.getCell(12)));
            
            domainData.setCreateTime(LocalDateTime.now());
            domainData.setUpdateTime(LocalDateTime.now());
            
            dataList.add(domainData);
        }
        
        return dataList;
    }
    
    /**
     * 解析代码扩展清单
     */
    private List<CodeExtensionData> parseCodeExtensionDataSheet(Sheet sheet) {
        List<CodeExtensionData> dataList = new ArrayList<>();
        int rowCount = sheet.getPhysicalNumberOfRows();
        
        // 跳过表头，从第二行开始
        for (int i = 1; i < rowCount; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            CodeExtensionData codeData = new CodeExtensionData();
            codeData.setCodeDomainNumber(getCellValue(row.getCell(0)));
            codeData.setCodeDomainChineseName(getCellValue(row.getCell(1)));
            codeData.setCodeValue(getCellValue(row.getCell(2)));
            codeData.setValueChineseName(getCellValue(row.getCell(3)));
            codeData.setCodeEnglishName(getCellValue(row.getCell(4)));
            codeData.setCodeEnglishAbbr(getCellValue(row.getCell(5)));
            codeData.setCodeDescription(getCellValue(row.getCell(6)));
            codeData.setDomainRule(getCellValue(row.getCell(7)));
            codeData.setCodeDomainSource(getCellValue(row.getCell(8)));
            codeData.setSourceNumber(getCellValue(row.getCell(9)));
            codeData.setRemark(getCellValue(row.getCell(10)));
            codeData.setCreateTime(LocalDateTime.now());
            codeData.setUpdateTime(LocalDateTime.now());
            
            dataList.add(codeData);
        }
        
        return dataList;
    }
    
    /**
     * 获取单元格值
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
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
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
    
    /**
     * 校验多Sheet数据
     */
    private ValidationResult validateMultiSheetData(MultiSheetData data) {
        ValidationResult result = new ValidationResult();
        
        // 1. 校验字典数据
        if (data.getDictDataList() != null && !data.getDictDataList().isEmpty()) {
            log.info("校验字典数据...");
            ValidationResult dictResult = validationService.validateDictData(data.getDictDataList());
            if (dictResult.hasErrors()) {
                result.getErrors().addAll(dictResult.getErrors());
                result.setValid(false);
            }
        }
        
        // 2. 校验域清单数据
        if (data.getDomainDataList() != null && !data.getDomainDataList().isEmpty()) {
            log.info("校验域清单数据...");
            ValidationResult domainResult = validationService.validateDomainData(data.getDomainDataList());
            if (domainResult.hasErrors()) {
                result.getErrors().addAll(domainResult.getErrors());
                result.setValid(false);
            }
        }
        
        // 3. 校验代码扩展清单数据
        if (data.getCodeExtensionDataList() != null && !data.getCodeExtensionDataList().isEmpty()) {
            log.info("校验代码扩展清单数据...");
            ValidationResult codeResult = validationService.validateCodeExtensionData(data.getCodeExtensionDataList());
            if (codeResult.hasErrors()) {
                result.getErrors().addAll(codeResult.getErrors());
                result.setValid(false);
            }
        }
        
        return result;
    }
    
    /**
     * 被剔除的在途数据
     */
    private static class RemovedIngData {
        private List<DictDataIng> dictDataList = new ArrayList<>();
        private List<DomainDataIng> domainDataList = new ArrayList<>();
        private List<CodeExtensionDataIng> codeExtensionDataList = new ArrayList<>();
        
        public int getTotalCount() {
            return dictDataList.size() + domainDataList.size() + codeExtensionDataList.size();
        }
        
        public List<DictDataIng> getDictDataList() {
            return dictDataList;
        }
        
        public List<DomainDataIng> getDomainDataList() {
            return domainDataList;
        }
        
        public List<CodeExtensionDataIng> getCodeExtensionDataList() {
            return codeExtensionDataList;
        }
    }
    
    /**
     * 根据最新导入的贯标数据剔除在途数据中的匹配项
     * 剔除逻辑：
     * 1. 字典技术衍生表：用中文名称匹配，匹配上则删除在途数据中的对应行
     * 2. 域清单：用域中文名称匹配，匹配上则删除在途数据中的对应行
     * 3. 代码扩展清单：用代码域中文名称匹配，匹配上则删除在途数据中所有代码域中文名称相同的数据（可能多行）
     * 
     * @return 被剔除的数据
     */
    private RemovedIngData removeMatchingIngData(MultiSheetData multiSheetData) {
        RemovedIngData removedData = new RemovedIngData();
        
        // 1. 剔除字典技术衍生表（在途）
        if (multiSheetData.getDictDataList() != null && !multiSheetData.getDictDataList().isEmpty()) {
            // 构建新导入的贯标数据的中文名称集合
            Set<String> standardChineseNameSet = multiSheetData.getDictDataList().stream()
                .map(DictData::getChineseName)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toSet());
            
            if (!standardChineseNameSet.isEmpty()) {
                // 查询在途数据中匹配的记录
                List<DictDataIng> ingDataList = dictDataIngService.list();
                List<Long> idsToRemove = new ArrayList<>();
                
                for (DictDataIng ing : ingDataList) {
                    if (ing.getChineseName() != null && !ing.getChineseName().trim().isEmpty()) {
                        String chineseName = ing.getChineseName().trim();
                        if (standardChineseNameSet.contains(chineseName)) {
                            removedData.getDictDataList().add(ing); // 保存被剔除的数据
                            idsToRemove.add(ing.getId());
                        }
                    }
                }
                
                if (!idsToRemove.isEmpty()) {
                    dictDataIngService.removeByIds(idsToRemove);
                    log.info("字典技术衍生表（在途）剔除: {} 条", idsToRemove.size());
                }
            }
        }
        
        // 2. 剔除域清单（在途）
        if (multiSheetData.getDomainDataList() != null && !multiSheetData.getDomainDataList().isEmpty()) {
            // 构建新导入的贯标数据的域中文名称集合
            Set<String> standardChineseNameSet = multiSheetData.getDomainDataList().stream()
                .map(DomainData::getChineseName)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toSet());
            
            if (!standardChineseNameSet.isEmpty()) {
                // 查询在途数据中匹配的记录
                List<DomainDataIng> ingDataList = domainDataIngService.list();
                List<Long> idsToRemove = new ArrayList<>();
                
                for (DomainDataIng ing : ingDataList) {
                    if (ing.getChineseName() != null && !ing.getChineseName().trim().isEmpty()) {
                        String chineseName = ing.getChineseName().trim();
                        if (standardChineseNameSet.contains(chineseName)) {
                            removedData.getDomainDataList().add(ing); // 保存被剔除的数据
                            idsToRemove.add(ing.getId());
                        }
                    }
                }
                
                if (!idsToRemove.isEmpty()) {
                    domainDataIngService.removeByIds(idsToRemove);
                    log.info("域清单（在途）剔除: {} 条", idsToRemove.size());
                }
            }
        }
        
        // 3. 剔除代码扩展清单（在途）
        if (multiSheetData.getCodeExtensionDataList() != null && !multiSheetData.getCodeExtensionDataList().isEmpty()) {
            // 构建新导入的贯标数据的代码域中文名称集合
            Set<String> standardCodeDomainChineseNameSet = multiSheetData.getCodeExtensionDataList().stream()
                .map(CodeExtensionData::getCodeDomainChineseName)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toSet());
            
            if (!standardCodeDomainChineseNameSet.isEmpty()) {
                // 查询在途数据中匹配的记录
                List<CodeExtensionDataIng> ingDataList = codeExtensionDataIngService.list();
                List<Long> idsToRemove = new ArrayList<>();
                
                for (CodeExtensionDataIng ing : ingDataList) {
                    if (ing.getCodeDomainChineseName() != null && !ing.getCodeDomainChineseName().trim().isEmpty()) {
                        String codeDomainChineseName = ing.getCodeDomainChineseName().trim();
                        if (standardCodeDomainChineseNameSet.contains(codeDomainChineseName)) {
                            removedData.getCodeExtensionDataList().add(ing); // 保存被剔除的数据
                            idsToRemove.add(ing.getId());
                        }
                    }
                }
                
                if (!idsToRemove.isEmpty()) {
                    codeExtensionDataIngService.removeByIds(idsToRemove);
                    log.info("代码扩展清单（在途）剔除: {} 条", idsToRemove.size());
                }
            }
        }
        
        return removedData;
    }
    
    /**
     * 生成剔除在途数据的Excel文件
     */
    private byte[] generateRemovedDataExcel(RemovedIngData removedData) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        
        // 创建样式
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        
        // 转换在途数据为标准数据格式
        List<DictData> dictDataList = new ArrayList<>();
        for (DictDataIng ing : removedData.getDictDataList()) {
            dictDataList.add(convertDictDataIngToDictData(ing));
        }
        
        List<DomainData> domainDataList = new ArrayList<>();
        for (DomainDataIng ing : removedData.getDomainDataList()) {
            domainDataList.add(convertDomainDataIngToDomainData(ing));
        }
        
        List<CodeExtensionData> codeDataList = new ArrayList<>();
        for (CodeExtensionDataIng ing : removedData.getCodeExtensionDataList()) {
            codeDataList.add(convertCodeExtensionDataIngToCodeExtensionData(ing));
        }
        
        // 创建三个Sheet
        if (!dictDataList.isEmpty()) {
            createDictSheet(workbook, dictDataList, headerStyle, dataStyle, "字典技术衍生表");
        }
        if (!domainDataList.isEmpty()) {
            createDomainSheet(workbook, domainDataList, headerStyle, dataStyle, "域清单");
        }
        if (!codeDataList.isEmpty()) {
            createCodeExtensionSheet(workbook, codeDataList, headerStyle, dataStyle, "代码扩展清单");
        }
        
        // 写入字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        return outputStream.toByteArray();
    }
    
    /**
     * 转换DictDataIng到DictData
     */
    private DictData convertDictDataIngToDictData(DictDataIng ing) {
        DictData dict = new DictData();
        dict.setDataItemCode(ing.getDataItemCode());
        dict.setEnglishAbbr(ing.getEnglishAbbr());
        dict.setEnglishName(ing.getEnglishName());
        dict.setChineseName(ing.getChineseName());
        dict.setDictAttr(ing.getDictAttr());
        dict.setDomainChineseName(ing.getDomainChineseName());
        dict.setDataType(ing.getDataType());
        dict.setDataFormat(ing.getDataFormat());
        dict.setValueRange(ing.getValueRange());
        dict.setJavaEsfName(ing.getJavaEsfName());
        dict.setEsfDataFormat(ing.getEsfDataFormat());
        dict.setGaussdbDataFormat(ing.getGaussdbDataFormat());
        dict.setGoldendbDataFormat(ing.getGoldendbDataFormat());
        return dict;
    }
    
    /**
     * 转换DomainDataIng到DomainData
     */
    private DomainData convertDomainDataIngToDomainData(DomainDataIng ing) {
        DomainData domain = new DomainData();
        domain.setDomainNumber(ing.getDomainNumber());
        domain.setDomainType(ing.getDomainType());
        domain.setDomainGroup(ing.getDomainGroup());
        domain.setChineseName(ing.getChineseName());
        domain.setEnglishName(ing.getEnglishName());
        domain.setEnglishAbbr(ing.getEnglishAbbr());
        domain.setDomainDefinition(ing.getDomainDefinition());
        domain.setDataFormat(ing.getDataFormat());
        domain.setDomainRule(ing.getDomainRule());
        domain.setValueRange(ing.getValueRange());
        domain.setDomainSource(ing.getDomainSource());
        domain.setSourceNumber(ing.getSourceNumber());
        domain.setRemark(ing.getRemark());
        return domain;
    }
    
    /**
     * 转换CodeExtensionDataIng到CodeExtensionData
     */
    private CodeExtensionData convertCodeExtensionDataIngToCodeExtensionData(CodeExtensionDataIng ing) {
        CodeExtensionData code = new CodeExtensionData();
        code.setCodeDomainNumber(ing.getCodeDomainNumber());
        code.setCodeDomainChineseName(ing.getCodeDomainChineseName());
        code.setCodeValue(ing.getCodeValue());
        code.setValueChineseName(ing.getValueChineseName());
        code.setCodeEnglishName(ing.getCodeEnglishName());
        code.setCodeEnglishAbbr(ing.getCodeEnglishAbbr());
        code.setCodeDescription(ing.getCodeDescription());
        code.setDomainRule(ing.getDomainRule());
        code.setCodeDomainSource(ing.getCodeDomainSource());
        code.setSourceNumber(ing.getSourceNumber());
        code.setRemark(ing.getRemark());
        return code;
    }
    
    /**
     * 创建字典技术衍生表Sheet
     */
    private void createDictSheet(Workbook workbook, List<DictData> dataList,
                                 CellStyle headerStyle, CellStyle dataStyle, String sheetName) {
        if (sheetName == null || sheetName.isEmpty()) {
            sheetName = "字典技术衍生表";
        }
        Sheet sheet = workbook.createSheet(sheetName);
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "数据项编号", "英文简称", "英文名称", "中文名称", "字典属性", "域中文名称",
            "数据类型", "数据格式", "取值范围", "JAVA/ESF规范命名", "ESF数据格式",
            "GaussDB数据格式", "GoldenDB数据格式"
        };
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 4000);
        }
        
        // 填充数据
        int rowNum = 1;
        for (DictData dict : dataList) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            
            createCell(row, colNum++, dict.getDataItemCode(), dataStyle);
            createCell(row, colNum++, dict.getEnglishAbbr(), dataStyle);
            createCell(row, colNum++, dict.getEnglishName(), dataStyle);
            createCell(row, colNum++, dict.getChineseName(), dataStyle);
            createCell(row, colNum++, dict.getDictAttr(), dataStyle);
            createCell(row, colNum++, dict.getDomainChineseName(), dataStyle);
            String dataType = dict.getDataType();
            if (dataType != null && "标志类".equals(dataType.trim())) {
                dataType = "代码类";
            }
            createCell(row, colNum++, dataType, dataStyle);
            createCell(row, colNum++, dict.getDataFormat(), dataStyle);
            createCell(row, colNum++, dict.getValueRange(), dataStyle);
            createCell(row, colNum++, dict.getJavaEsfName(), dataStyle);
            createCell(row, colNum++, dict.getEsfDataFormat(), dataStyle);
            createCell(row, colNum++, dict.getGaussdbDataFormat(), dataStyle);
            createCell(row, colNum++, dict.getGoldendbDataFormat(), dataStyle);
        }
    }
    
    /**
     * 创建域清单Sheet
     */
    private void createDomainSheet(Workbook workbook, List<DomainData> dataList,
                                   CellStyle headerStyle, CellStyle dataStyle, String sheetName) {
        if (sheetName == null || sheetName.isEmpty()) {
            sheetName = "域清单";
        }
        Sheet sheet = workbook.createSheet(sheetName);
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "域编号", "域类型", "域组", "域中文名称", "域英文名称", "域英文简称",
            "域定义", "数据格式", "域规则", "取值范围", "域来源", "来源编号", "备注"
        };
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 4000);
        }
        
        // 填充数据
        int rowNum = 1;
        for (DomainData domain : dataList) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            
            createCell(row, colNum++, domain.getDomainNumber(), dataStyle);
            createCell(row, colNum++, domain.getDomainType(), dataStyle);
            createCell(row, colNum++, domain.getDomainGroup(), dataStyle);
            createCell(row, colNum++, domain.getChineseName(), dataStyle);
            createCell(row, colNum++, domain.getEnglishName(), dataStyle);
            createCell(row, colNum++, domain.getEnglishAbbr(), dataStyle);
            createCell(row, colNum++, domain.getDomainDefinition(), dataStyle);
            createCell(row, colNum++, domain.getDataFormat(), dataStyle);
            createCell(row, colNum++, domain.getDomainRule(), dataStyle);
            createCell(row, colNum++, domain.getValueRange(), dataStyle);
            createCell(row, colNum++, domain.getDomainSource(), dataStyle);
            createCell(row, colNum++, domain.getSourceNumber(), dataStyle);
            createCell(row, colNum++, domain.getRemark(), dataStyle);
        }
    }
    
    /**
     * 创建代码扩展清单Sheet
     */
    private void createCodeExtensionSheet(Workbook workbook, List<CodeExtensionData> dataList,
                                         CellStyle headerStyle, CellStyle dataStyle, String sheetName) {
        if (sheetName == null || sheetName.isEmpty()) {
            sheetName = "代码扩展清单";
        }
        Sheet sheet = workbook.createSheet(sheetName);
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "代码域编号", "代码域中文名称", "代码取值", "取值含义中文名称", "代码含义英文名称",
            "代码含义英文简称", "代码描述", "域规则", "代码域来源", "来源编号", "备注"
        };
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 4000);
        }
        
        // 填充数据
        int rowNum = 1;
        for (CodeExtensionData code : dataList) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            
            createCell(row, colNum++, code.getCodeDomainNumber(), dataStyle);
            createCell(row, colNum++, code.getCodeDomainChineseName(), dataStyle);
            createCell(row, colNum++, code.getCodeValue(), dataStyle);
            createCell(row, colNum++, code.getValueChineseName(), dataStyle);
            createCell(row, colNum++, code.getCodeEnglishName(), dataStyle);
            createCell(row, colNum++, code.getCodeEnglishAbbr(), dataStyle);
            createCell(row, colNum++, code.getCodeDescription(), dataStyle);
            createCell(row, colNum++, code.getDomainRule(), dataStyle);
            createCell(row, colNum++, code.getCodeDomainSource(), dataStyle);
            createCell(row, colNum++, code.getSourceNumber(), dataStyle);
            createCell(row, colNum++, code.getRemark(), dataStyle);
        }
    }
    
    /**
     * 创建单元格
     */
    private void createCell(Row row, int colNum, Object value, CellStyle style) {
        Cell cell = row.createCell(colNum);
        if (value != null) {
            if (value instanceof String) {
                cell.setCellValue((String) value);
            } else if (value instanceof Number) {
                cell.setCellValue(((Number) value).doubleValue());
            } else {
                cell.setCellValue(value.toString());
            }
        }
        cell.setCellStyle(style);
    }
    
    /**
     * 创建表头样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * 创建数据样式
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
}

