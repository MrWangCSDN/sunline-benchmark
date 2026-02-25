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
import com.sunline.dict.service.ChangeDetector;
import com.sunline.dict.service.CodeExtensionDataIngService;
import com.sunline.dict.service.CodeExtensionDataService;
import com.sunline.dict.service.DictDataIngService;
import com.sunline.dict.service.DictDataService;
import com.sunline.dict.service.DomainDataIngService;
import com.sunline.dict.service.DomainDataService;
import com.sunline.dict.service.EnumMappingService;
import com.sunline.dict.service.ExcelValidationService;
import com.sunline.dict.service.ProgressService;
import com.sunline.dict.util.PinyinUtils;
import com.sunline.dict.config.ExcelImportConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 多Sheet数据导入Controller（在途）
 */
@RestController
@RequestMapping("/api/multi-sheet-ing")
@CrossOrigin
public class MultiSheetIngController {
    
    private static final Logger log = LoggerFactory.getLogger(MultiSheetIngController.class);
    
    @Autowired
    private DictDataIngService dictDataIngService;
    
    @Autowired
    private DomainDataIngService domainDataIngService;
    
    @Autowired
    private CodeExtensionDataIngService codeExtensionDataIngService;
    
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
    private EnumMappingService enumMappingService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    /**
     * SSE进度推送端点
     */
    @GetMapping("/progress/{clientId}")
    public SseEmitter progress(@PathVariable String clientId) {
        log.info("客户端连接进度推送（在途）: {}", clientId);
        return progressService.createEmitter(clientId);
    }
    
    /**
     * 变更追踪预览 - 分析多Sheet数据变更（在途）
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/change-preview")
    public Result<MultiSheetChangeReport> changePreview(@RequestParam("file") MultipartFile file) {
        try {
            log.info("======================================");
            log.info("开始分析多Sheet变更（在途）: {}", file.getOriginalFilename());
            
            // 1. 解析Excel
            MultiSheetData newData = parseMultiSheetExcel(file);
            
            // 2. 数据校验（包括与在途数据和贯标数据的重复性检查）
            log.info("开始数据校验（在途）...");
            ValidationResult validationResult = validateIngDataWithStandard(newData);
            
            if (validationResult.hasErrors()) {
                log.warn("数据校验失败（在途），发现 {} 个错误", validationResult.getErrors().size());
                Result<ValidationResult> errorResult = new Result<>(500, "数据校验失败", validationResult);
                return (Result<MultiSheetChangeReport>) (Result<?>) errorResult;
            }
            log.info("✓ 数据校验通过（在途）");
            
            // 3. 变更分析（与在途数据对比）
            MultiSheetChangeReport multiSheetChangeReport = new MultiSheetChangeReport();
            
            // 分析字典技术衍生表变更
            if (newData.getDictDataList() != null && !newData.getDictDataList().isEmpty()) {
                log.info("分析字典技术衍生表变更（在途）...");
                List<DictDataIng> oldDictData = dictDataIngService.getAllActiveData();
                // 转换为DictData进行比较
                List<DictData> oldDictDataConverted = convertDictDataIngToDictData(oldDictData);
                ChangeReport dictChangeReport = changeDetector.detectChanges(
                    newData.getDictDataList(), oldDictDataConverted);
                multiSheetChangeReport.setDictChangeReport(dictChangeReport);
                log.info("✓ 字典表变更（在途）: 新增={}, 修改={}, 删除={}, 不变={}", 
                    dictChangeReport.getSummary().getNewCount(),
                    dictChangeReport.getSummary().getUpdateCount(),
                    dictChangeReport.getSummary().getDeleteCount(),
                    dictChangeReport.getSummary().getUnchangedCount());
            }
            
            // 分析域清单变更
            if (newData.getDomainDataList() != null && !newData.getDomainDataList().isEmpty()) {
                log.info("分析域清单变更（在途）...");
                List<DomainDataIng> oldDomainData = domainDataIngService.list();
                List<DomainData> oldDomainDataConverted = convertDomainDataIngToDomainData(oldDomainData);
                ChangeReport domainChangeReport = detectDomainDataChanges(
                    newData.getDomainDataList(), oldDomainDataConverted);
                multiSheetChangeReport.setDomainChangeReport(domainChangeReport);
                log.info("✓ 域清单变更（在途）: 新增={}, 修改={}, 删除={}, 不变={}", 
                    domainChangeReport.getSummary().getNewCount(),
                    domainChangeReport.getSummary().getUpdateCount(),
                    domainChangeReport.getSummary().getDeleteCount(),
                    domainChangeReport.getSummary().getUnchangedCount());
            }
            
            // 分析代码扩展清单变更
            if (newData.getCodeExtensionDataList() != null && !newData.getCodeExtensionDataList().isEmpty()) {
                log.info("分析代码扩展清单变更（在途）...");
                List<CodeExtensionDataIng> oldCodeData = codeExtensionDataIngService.list();
                List<CodeExtensionData> oldCodeDataConverted = convertCodeExtensionDataIngToCodeExtensionData(oldCodeData);
                ChangeReport codeChangeReport = detectCodeExtensionDataChanges(
                    newData.getCodeExtensionDataList(), oldCodeDataConverted);
                multiSheetChangeReport.setCodeExtensionChangeReport(codeChangeReport);
                log.info("✓ 代码扩展清单变更（在途）: 新增={}, 修改={}, 删除={}, 不变={}", 
                    codeChangeReport.getSummary().getNewCount(),
                    codeChangeReport.getSummary().getUpdateCount(),
                    codeChangeReport.getSummary().getDeleteCount(),
                    codeChangeReport.getSummary().getUnchangedCount());
            }
            
            log.info("======================================");
            log.info("多Sheet变更分析完成（在途）");
            
            return Result.success("变更分析成功", multiSheetChangeReport);
            
        } catch (Exception e) {
            log.error("变更分析失败（在途）", e);
            return Result.error("变更分析失败: " + e.getMessage());
        }
    }
    
    /**
     * 确认导入 - 将数据保存到在途数据表（带进度条，单一事务）
     */
    @PostMapping("/confirm")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Integer>> confirmImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "clientId", required = false) String clientId,
            @RequestParam(value = "changeDescription", required = false) String changeDescription,
            @RequestParam(value = "password", required = false) String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("Excel导入失败（在途）：口令验证失败");
                return Result.error("导入失败：口令错误，请输入正确的6位数字口令");
            }
            
            log.info("======================================");
            log.info("开始确认导入多Sheet Excel文件（在途）: {}, clientId: {}", file.getOriginalFilename(), clientId);
            
            // 0. 发送初始进度
            sendProgress(clientId, 0, "preparing", "正在准备导入...");
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 1. 解析Excel
            sendProgress(clientId, 10, "parsing", "正在解析Excel文件...");
            MultiSheetData multiSheetData = parseMultiSheetExcel(file);
            
            // 计算总记录数
            int totalRecords = multiSheetData.getDictDataCount() + 
                             multiSheetData.getDomainDataCount() + 
                             multiSheetData.getCodeExtensionDataCount();
            
            log.info("总记录数: {}", totalRecords);
            sendProgress(clientId, 20, "importing", String.format("解析完成，准备导入 %d 条数据...", totalRecords));
            
            Map<String, Integer> result = new HashMap<>();
            int processedRecords = 0;
            int batchSize = 500;
            
            // 2. 导入字典技术衍生表数据（在途）
            if (multiSheetData.getDictDataList() != null && !multiSheetData.getDictDataList().isEmpty()) {
                log.info("开始导入字典技术衍生表数据（在途）: {} 条", multiSheetData.getDictDataCount());
                sendProgress(clientId, 25, "importing", "正在清空字典表旧数据（在途）...");
                
                // 清空原有数据
                dictDataIngService.remove(null);
                
                // 转换为在途数据并批量插入
                List<DictDataIng> dictIngList = convertDictDataToDictDataIng(multiSheetData.getDictDataList());
                int dictCount = 0;
                int totalBatches = (int) Math.ceil((double) dictIngList.size() / batchSize);
                
                for (int i = 0; i < dictIngList.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, dictIngList.size());
                    List<DictDataIng> batch = dictIngList.subList(i, end);
                    dictDataIngService.saveBatch(batch);
                    dictCount += batch.size();
                    processedRecords += batch.size();
                    
                    int currentBatch = (i / batchSize) + 1;
                    int progress = 25 + (int)((double)processedRecords / totalRecords * 50);
                    sendProgress(clientId, progress, "importing", 
                        String.format("正在导入字典表（在途）: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                            currentBatch, totalBatches, processedRecords, totalRecords,
                            (int)((double)processedRecords / totalRecords * 100)));
                    
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                result.put("dictDataCount", dictCount);
                log.info("✓ 字典技术衍生表导入完成（在途）: {} 条", dictCount);
            } else {
                result.put("dictDataCount", 0);
            }
            
            // 3. 导入域清单数据（在途）
            if (multiSheetData.getDomainDataList() != null && !multiSheetData.getDomainDataList().isEmpty()) {
                log.info("开始导入域清单数据（在途）: {} 条", multiSheetData.getDomainDataCount());
                
                // 应用枚举映射
                List<DomainData> domainList = multiSheetData.getDomainDataList();
                for (DomainData domain : domainList) {
                    if ("代码类".equals(domain.getDomainGroup()) && 
                        (domain.getEnglishAbbr() == null || domain.getEnglishAbbr().trim().isEmpty())) {
                        String chineseName = domain.getChineseName();
                        if (chineseName != null && !chineseName.trim().isEmpty()) {
                            String mappedAbbr = enumMappingService.getDomainEnglishAbbrByChineseName(chineseName);
                            if (mappedAbbr != null && !mappedAbbr.trim().isEmpty()) {
                                domain.setEnglishAbbr(mappedAbbr);
                            }
                        }
                    }
                }
                
                // 清空原有数据
                domainDataIngService.remove(null);
                
                // 转换为在途数据并批量插入
                List<DomainDataIng> domainIngList = convertDomainDataToDomainDataIng(domainList);
                int domainCount = 0;
                int totalBatches = (int) Math.ceil((double) domainIngList.size() / batchSize);
                
                for (int i = 0; i < domainIngList.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, domainIngList.size());
                    List<DomainDataIng> batch = domainIngList.subList(i, end);
                    domainDataIngService.saveBatch(batch);
                    domainCount += batch.size();
                    processedRecords += batch.size();
                    
                    int currentBatch = (i / batchSize) + 1;
                    int progress = 25 + (int)((double)processedRecords / totalRecords * 50);
                    sendProgress(clientId, progress, "importing", 
                        String.format("正在导入域清单（在途）: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                            currentBatch, totalBatches, processedRecords, totalRecords,
                            (int)((double)processedRecords / totalRecords * 100)));
                    
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                result.put("domainDataCount", domainCount);
                log.info("✓ 域清单导入完成（在途）: {} 条", domainCount);
            } else {
                result.put("domainDataCount", 0);
            }
            
            // 4. 导入代码扩展清单数据（在途）
            if (multiSheetData.getCodeExtensionDataList() != null && !multiSheetData.getCodeExtensionDataList().isEmpty()) {
                log.info("开始导入代码扩展清单数据（在途）: {} 条", multiSheetData.getCodeExtensionDataCount());
                
                // 应用枚举映射
                List<CodeExtensionData> codeList = multiSheetData.getCodeExtensionDataList();
                for (CodeExtensionData code : codeList) {
                    if (code.getCodeEnglishAbbr() == null || code.getCodeEnglishAbbr().trim().isEmpty()) {
                        String domainChineseName = code.getCodeDomainChineseName();
                        String codeValue = code.getCodeValue();
                        if (domainChineseName != null && !domainChineseName.trim().isEmpty() &&
                            codeValue != null && !codeValue.trim().isEmpty()) {
                            String mappedAbbr = enumMappingService.getEnumFieldIdByDomainAndCodeValue(
                                domainChineseName, codeValue);
                            if (mappedAbbr != null && !mappedAbbr.trim().isEmpty()) {
                                code.setCodeEnglishAbbr(mappedAbbr);
                            }
                        }
                    }
                }
                
                // 清空原有数据
                codeExtensionDataIngService.remove(null);
                
                // 转换为在途数据并批量插入
                List<CodeExtensionDataIng> codeIngList = convertCodeExtensionDataToCodeExtensionDataIng(codeList);
                int codeCount = 0;
                int totalBatches = (int) Math.ceil((double) codeIngList.size() / batchSize);
                
                for (int i = 0; i < codeIngList.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, codeIngList.size());
                    List<CodeExtensionDataIng> batch = codeIngList.subList(i, end);
                    codeExtensionDataIngService.saveBatch(batch);
                    codeCount += batch.size();
                    processedRecords += batch.size();
                    
                    int currentBatch = (i / batchSize) + 1;
                    int progress = 25 + (int)((double)processedRecords / totalRecords * 50);
                    sendProgress(clientId, progress, "importing", 
                        String.format("正在导入代码扩展清单（在途）: 第%d/%d批，已处理%d/%d条 (%d%%)", 
                            currentBatch, totalBatches, processedRecords, totalRecords,
                            (int)((double)processedRecords / totalRecords * 100)));
                    
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                result.put("codeExtensionDataCount", codeCount);
                log.info("✓ 代码扩展清单导入完成（在途）: {} 条", codeCount);
            } else {
                result.put("codeExtensionDataCount", 0);
            }
            
            int totalCount = result.values().stream().mapToInt(Integer::intValue).sum();
            result.put("totalCount", totalCount);
            
            sendProgress(clientId, 100, "completed", String.format("导入成功！共导入 %d 条数据（在途）", totalCount));
            
            log.info("======================================");
            log.info("多Sheet数据导入完成（在途）！");
            log.info("字典技术衍生表: {} 条", result.get("dictDataCount"));
            log.info("域清单: {} 条", result.get("domainDataCount"));
            log.info("代码扩展清单: {} 条", result.get("codeExtensionDataCount"));
            log.info("总计: {} 条", totalCount);
            log.info("======================================");
            
            return Result.success("导入成功", result);
            
        } catch (Exception e) {
            log.error("确认导入失败（在途）", e);
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
                ImportProgress importProgress = new ImportProgress();
                importProgress.setPercentage(progress);
                importProgress.setStatus(status);
                importProgress.setMessage(message);
                progressService.sendProgress(clientId, importProgress);
            } catch (Exception e) {
                log.error("发送进度失败 - clientId: {}, error: {}", clientId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * 转换DictData到DictDataIng
     */
    private List<DictDataIng> convertDictDataToDictDataIng(List<DictData> dictDataList) {
        return dictDataList.stream().map(dict -> {
            DictDataIng ing = new DictDataIng();
            ing.setSortOrder(dict.getSortOrder());
            ing.setVersionId(dict.getVersionId());
            ing.setDataHash(dict.getDataHash());
            ing.setChangeType(dict.getChangeType());
            ing.setIsDeleted(dict.getIsDeleted());
            ing.setDeletedAt(dict.getDeletedAt());
            ing.setDataItemCode(dict.getDataItemCode());
            ing.setEnglishAbbr(dict.getEnglishAbbr());
            ing.setEnglishName(dict.getEnglishName());
            ing.setChineseName(dict.getChineseName());
            ing.setDictAttr(dict.getDictAttr());
            ing.setDomainChineseName(dict.getDomainChineseName());
            ing.setDataType(dict.getDataType());
            ing.setDataFormat(dict.getDataFormat());
            ing.setValueRange(dict.getValueRange());
            ing.setJavaEsfName(dict.getJavaEsfName());
            ing.setEsfDataFormat(dict.getEsfDataFormat());
            ing.setGaussdbDataFormat(dict.getGaussdbDataFormat());
            ing.setGoldendbDataFormat(dict.getGoldendbDataFormat());
            ing.setCreateTime(LocalDateTime.now());
            ing.setUpdateTime(LocalDateTime.now());
            return ing;
        }).collect(Collectors.toList());
    }
    
    /**
     * 转换DictDataIng到DictData
     */
    private List<DictData> convertDictDataIngToDictData(List<DictDataIng> dictDataIngList) {
        return dictDataIngList.stream().map(ing -> {
            DictData dict = new DictData();
            dict.setSortOrder(ing.getSortOrder());
            dict.setVersionId(ing.getVersionId());
            dict.setDataHash(ing.getDataHash());
            dict.setChangeType(ing.getChangeType());
            dict.setIsDeleted(ing.getIsDeleted());
            dict.setDeletedAt(ing.getDeletedAt());
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
            dict.setCreateTime(ing.getCreateTime());
            dict.setUpdateTime(ing.getUpdateTime());
            return dict;
        }).collect(Collectors.toList());
    }
    
    /**
     * 转换DomainData到DomainDataIng
     */
    private List<DomainDataIng> convertDomainDataToDomainDataIng(List<DomainData> domainDataList) {
        return domainDataList.stream().map(domain -> {
            DomainDataIng ing = new DomainDataIng();
            ing.setDomainNumber(domain.getDomainNumber());
            ing.setDomainType(domain.getDomainType());
            ing.setDomainGroup(domain.getDomainGroup());
            ing.setChineseName(domain.getChineseName());
            ing.setEnglishName(domain.getEnglishName());
            ing.setEnglishAbbr(domain.getEnglishAbbr());
            ing.setDomainDefinition(domain.getDomainDefinition());
            ing.setDataFormat(domain.getDataFormat());
            ing.setDomainRule(domain.getDomainRule());
            ing.setValueRange(domain.getValueRange());
            ing.setDomainSource(domain.getDomainSource());
            ing.setSourceNumber(domain.getSourceNumber());
            ing.setRemark(domain.getRemark());
            ing.setCreateTime(LocalDateTime.now());
            ing.setUpdateTime(LocalDateTime.now());
            return ing;
        }).collect(Collectors.toList());
    }
    
    /**
     * 转换DomainDataIng到DomainData
     */
    private List<DomainData> convertDomainDataIngToDomainData(List<DomainDataIng> domainDataIngList) {
        return domainDataIngList.stream().map(ing -> {
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
            domain.setCreateTime(ing.getCreateTime());
            domain.setUpdateTime(ing.getUpdateTime());
            return domain;
        }).collect(Collectors.toList());
    }
    
    /**
     * 转换CodeExtensionData到CodeExtensionDataIng
     */
    private List<CodeExtensionDataIng> convertCodeExtensionDataToCodeExtensionDataIng(List<CodeExtensionData> codeDataList) {
        return codeDataList.stream().map(code -> {
            CodeExtensionDataIng ing = new CodeExtensionDataIng();
            ing.setCodeDomainNumber(code.getCodeDomainNumber());
            ing.setCodeDomainChineseName(code.getCodeDomainChineseName());
            ing.setCodeValue(code.getCodeValue());
            ing.setValueChineseName(code.getValueChineseName());
            ing.setCodeEnglishName(code.getCodeEnglishName());
            ing.setCodeEnglishAbbr(code.getCodeEnglishAbbr());
            ing.setCodeDescription(code.getCodeDescription());
            ing.setDomainRule(code.getDomainRule());
            ing.setCodeDomainSource(code.getCodeDomainSource());
            ing.setSourceNumber(code.getSourceNumber());
            ing.setRemark(code.getRemark());
            ing.setCreateTime(LocalDateTime.now());
            ing.setUpdateTime(LocalDateTime.now());
            return ing;
        }).collect(Collectors.toList());
    }
    
    /**
     * 转换CodeExtensionDataIng到CodeExtensionData
     */
    private List<CodeExtensionData> convertCodeExtensionDataIngToCodeExtensionData(List<CodeExtensionDataIng> codeDataIngList) {
        return codeDataIngList.stream().map(ing -> {
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
            code.setCreateTime(ing.getCreateTime());
            code.setUpdateTime(ing.getUpdateTime());
            return code;
        }).collect(Collectors.toList());
    }
    
    /**
     * 检测域清单数据变更（复用MultiSheetController的逻辑）
     */
    private ChangeReport detectDomainDataChanges(List<DomainData> newDataList, List<DomainData> oldDataList) {
        ChangeReport report = new ChangeReport();
        
        Map<String, DomainData> oldDataMap = oldDataList.stream()
            .filter(data -> data.getChineseName() != null && !data.getChineseName().trim().isEmpty())
            .collect(Collectors.toMap(
                data -> data.getChineseName().trim(),
                Function.identity(),
                (existing, replacement) -> existing
            ));
        
        Map<String, DomainData> newDataMap = newDataList.stream()
            .filter(data -> data.getChineseName() != null && !data.getChineseName().trim().isEmpty())
            .collect(Collectors.toMap(
                data -> data.getChineseName().trim(),
                Function.identity(),
                (existing, replacement) -> existing
            ));
        
        for (DomainData newData : newDataList) {
            String chineseName = newData.getChineseName();
            if (chineseName == null || chineseName.trim().isEmpty()) {
                continue;
            }
            
            String chineseNameTrimmed = chineseName.trim();
            DomainData oldData = oldDataMap.get(chineseNameTrimmed);
            
            if (oldData == null) {
                report.addNew(newData);
            } else {
                String newDataFormat = newData.getDataFormat() != null ? newData.getDataFormat().trim() : "";
                String oldDataFormat = oldData.getDataFormat() != null ? oldData.getDataFormat().trim() : "";
                
                if (!Objects.equals(newDataFormat, oldDataFormat)) {
                    newData.setId(oldData.getId());
                    report.addUpdate(newData, oldData);
                } else {
                    newData.setId(oldData.getId());
                    report.addUnchanged(newData);
                }
            }
        }
        
        for (DomainData oldData : oldDataList) {
            String chineseName = oldData.getChineseName();
            if (chineseName != null && !chineseName.trim().isEmpty()) {
                String chineseNameTrimmed = chineseName.trim();
                if (!newDataMap.containsKey(chineseNameTrimmed)) {
                    report.addDelete(oldData);
                }
            }
        }
        
        report.generateSummary();
        return report;
    }
    
    /**
     * 检测代码扩展清单数据变更（复用MultiSheetController的逻辑）
     */
    private ChangeReport detectCodeExtensionDataChanges(List<CodeExtensionData> newDataList, List<CodeExtensionData> oldDataList) {
        ChangeReport report = new ChangeReport();
        
        Map<String, CodeExtensionData> oldDataMap = oldDataList.stream()
            .filter(data -> data.getCodeDomainChineseName() != null && !data.getCodeDomainChineseName().trim().isEmpty() &&
                           data.getCodeValue() != null && !data.getCodeValue().trim().isEmpty())
            .collect(Collectors.toMap(
                data -> (data.getCodeDomainChineseName().trim() + "|" + data.getCodeValue().trim()),
                Function.identity(),
                (existing, replacement) -> existing
            ));
        
        Map<String, CodeExtensionData> newDataMap = newDataList.stream()
            .filter(data -> data.getCodeDomainChineseName() != null && !data.getCodeDomainChineseName().trim().isEmpty() &&
                           data.getCodeValue() != null && !data.getCodeValue().trim().isEmpty())
            .collect(Collectors.toMap(
                data -> (data.getCodeDomainChineseName().trim() + "|" + data.getCodeValue().trim()),
                Function.identity(),
                (existing, replacement) -> existing
            ));
        
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
                report.addNew(newData);
            } else {
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
                    newData.setId(oldData.getId());
                    report.addUpdate(newData, oldData);
                } else {
                    newData.setId(oldData.getId());
                    report.addUnchanged(newData);
                }
            }
        }
        
        for (CodeExtensionData oldData : oldDataList) {
            String domainChineseName = oldData.getCodeDomainChineseName();
            String codeValue = oldData.getCodeValue();
            
            if (domainChineseName != null && !domainChineseName.trim().isEmpty() &&
                codeValue != null && !codeValue.trim().isEmpty()) {
                String key = domainChineseName.trim() + "|" + codeValue.trim();
                if (!newDataMap.containsKey(key)) {
                    report.addDelete(oldData);
                }
            }
        }
        
        report.generateSummary();
        return report;
    }
    
    /**
     * 解析多Sheet Excel文件（复用MultiSheetController的逻辑）
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
     * 解析字典技术衍生表（复用MultiSheetController的逻辑）
     */
    private List<DictData> parseDictDataSheet(Sheet sheet) {
        List<DictData> dataList = new ArrayList<>();
        int rowCount = sheet.getPhysicalNumberOfRows();
        
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
     * 解析域清单（复用MultiSheetController的逻辑）
     */
    private List<DomainData> parseDomainDataSheet(Sheet sheet) {
        List<DomainData> dataList = new ArrayList<>();
        int rowCount = sheet.getPhysicalNumberOfRows();
        
        for (int i = 1; i < rowCount; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            DomainData domainData = new DomainData();
            
            String domainNumberStr = getCellValue(row.getCell(0));
            if (domainNumberStr != null && !domainNumberStr.isEmpty()) {
                try {
                    domainData.setDomainNumber(Integer.parseInt(domainNumberStr));
                } catch (NumberFormatException e) {
                    log.warn("域编号格式错误: {}", domainNumberStr);
                }
            }
            
            domainData.setDomainType(getCellValue(row.getCell(1)));
            domainData.setDomainGroup(getCellValue(row.getCell(2)));
            
            String chineseName = getCellValue(row.getCell(3));
            domainData.setChineseName(chineseName);
            
            String generatedEnglishName = PinyinUtils.toUpperCaseWithUnderscore(chineseName);
            if (generatedEnglishName != null && !generatedEnglishName.isEmpty()) {
                domainData.setEnglishName(generatedEnglishName);
            } else {
                domainData.setEnglishName(getCellValue(row.getCell(4)));
            }
            
            domainData.setEnglishAbbr(getCellValue(row.getCell(5)));
            domainData.setDomainDefinition(getCellValue(row.getCell(6)));
            domainData.setDataFormat(getCellValue(row.getCell(7)));
            domainData.setDomainRule(getCellValue(row.getCell(8)));
            domainData.setValueRange(getCellValue(row.getCell(9)));
            domainData.setDomainSource(getCellValue(row.getCell(10)));
            domainData.setSourceNumber(getCellValue(row.getCell(11)));
            domainData.setRemark(getCellValue(row.getCell(12)));
            domainData.setCreateTime(LocalDateTime.now());
            domainData.setUpdateTime(LocalDateTime.now());
            
            dataList.add(domainData);
        }
        
        return dataList;
    }
    
    /**
     * 解析代码扩展清单（复用MultiSheetController的逻辑）
     */
    private List<CodeExtensionData> parseCodeExtensionDataSheet(Sheet sheet) {
        List<CodeExtensionData> dataList = new ArrayList<>();
        int rowCount = sheet.getPhysicalNumberOfRows();
        
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
     * 获取单元格值（复用MultiSheetController的逻辑）
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
     * 校验多Sheet数据（复用MultiSheetController的逻辑）
     */
    private ValidationResult validateMultiSheetData(MultiSheetData data) {
        ValidationResult result = new ValidationResult();
        
        if (data.getDictDataList() != null && !data.getDictDataList().isEmpty()) {
            log.info("校验字典数据...");
            ValidationResult dictResult = validationService.validateDictData(data.getDictDataList());
            if (dictResult.hasErrors()) {
                result.getErrors().addAll(dictResult.getErrors());
                result.setValid(false);
            }
        }
        
        if (data.getDomainDataList() != null && !data.getDomainDataList().isEmpty()) {
            log.info("校验域清单数据...");
            ValidationResult domainResult = validationService.validateDomainData(data.getDomainDataList());
            if (domainResult.hasErrors()) {
                result.getErrors().addAll(domainResult.getErrors());
                result.setValid(false);
            }
        }
        
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
     * 校验在途数据（包括与在途数据和贯标数据的重复性检查）
     */
    private ValidationResult validateIngDataWithStandard(MultiSheetData newData) {
        ValidationResult result = new ValidationResult();
        
        // 1. 先进行基础校验（检查Excel内部重复）
        ValidationResult basicValidation = validateMultiSheetData(newData);
        if (basicValidation.hasErrors()) {
            result.getErrors().addAll(basicValidation.getErrors());
        }
        
        // 2. 校验字典技术衍生表：英文简称、中文名称、JAVA/ESF规范命名 不能与贯标数据重复
        if (newData.getDictDataList() != null && !newData.getDictDataList().isEmpty()) {
            log.info("校验字典数据（在途）与贯标数据的重复性...");
            validateDictDataIng(newData.getDictDataList(), result);
        }
        
        // 3. 校验域清单：域中文名称不能与贯标数据重复
        if (newData.getDomainDataList() != null && !newData.getDomainDataList().isEmpty()) {
            log.info("校验域清单数据（在途）与贯标数据的重复性...");
            validateDomainDataIng(newData.getDomainDataList(), result);
        }
        
        // 4. 校验代码扩展清单：代码域中文名称+代码取值 不能与贯标数据重复
        if (newData.getCodeExtensionDataList() != null && !newData.getCodeExtensionDataList().isEmpty()) {
            log.info("校验代码扩展清单数据（在途）与贯标数据的重复性...");
            validateCodeExtensionDataIng(newData.getCodeExtensionDataList(), result);
        }
        
        return result;
    }
    
    /**
     * 校验字典技术衍生表（在途）
     * 英文简称、中文名称、JAVA/ESF规范命名 不能与贯标数据重复
     * 注意：因为是全量在途数据导入，不需要与在途数据校验（会一直重复）
     */
    private void validateDictDataIng(List<DictData> newDataList, ValidationResult result) {
        // 获取贯标数据
        List<DictData> standardDictDataList = dictDataService.getAllActiveData();
        
        // 构建贯标数据的字段值集合
        Set<String> standardEnglishAbbrSet = standardDictDataList.stream()
            .map(DictData::getEnglishAbbr)
            .filter(v -> v != null && !v.trim().isEmpty())
            .map(String::trim)
            .collect(Collectors.toSet());
        
        Set<String> standardChineseNameSet = standardDictDataList.stream()
            .map(DictData::getChineseName)
            .filter(v -> v != null && !v.trim().isEmpty())
            .map(String::trim)
            .collect(Collectors.toSet());
        
        Set<String> standardJavaEsfNameSet = standardDictDataList.stream()
            .map(DictData::getJavaEsfName)
            .filter(v -> v != null && !v.trim().isEmpty())
            .map(String::trim)
            .collect(Collectors.toSet());
        
        // 检查新数据是否与贯标数据重复
        for (int i = 0; i < newDataList.size(); i++) {
            DictData data = newDataList.get(i);
            int rowNum = i + 2; // Excel行号（从第2行开始，第1行是表头）
            
            // 检查英文简称
            if (data.getEnglishAbbr() != null && !data.getEnglishAbbr().trim().isEmpty()) {
                String englishAbbr = data.getEnglishAbbr().trim();
                if (standardEnglishAbbrSet.contains(englishAbbr)) {
                    result.addError("字典技术衍生表（在途）", "英文简称", "duplicate_with_standard",
                        Collections.singletonList(rowNum), englishAbbr + "（与贯标数据重复）");
                }
            }
            
            // 检查中文名称
            if (data.getChineseName() != null && !data.getChineseName().trim().isEmpty()) {
                String chineseName = data.getChineseName().trim();
                if (standardChineseNameSet.contains(chineseName)) {
                    result.addError("字典技术衍生表（在途）", "中文名称", "duplicate_with_standard",
                        Collections.singletonList(rowNum), chineseName + "（与贯标数据重复）");
                }
            }
            
            // 检查JAVA/ESF规范命名
            if (data.getJavaEsfName() != null && !data.getJavaEsfName().trim().isEmpty()) {
                String javaEsfName = data.getJavaEsfName().trim();
                if (standardJavaEsfNameSet.contains(javaEsfName)) {
                    result.addError("字典技术衍生表（在途）", "JAVA/ESF规范命名", "duplicate_with_standard",
                        Collections.singletonList(rowNum), javaEsfName + "（与贯标数据重复）");
                }
            }
        }
    }
    
    /**
     * 校验域清单（在途）
     * 域中文名称不能与贯标数据重复
     * 注意：因为是全量在途数据导入，不需要与在途数据校验（会一直重复）
     */
    private void validateDomainDataIng(List<DomainData> newDataList, ValidationResult result) {
        // 获取贯标数据
        List<DomainData> standardDomainDataList = domainDataService.list();
        
        // 构建贯标数据的域中文名称集合
        Set<String> standardChineseNameSet = standardDomainDataList.stream()
            .map(DomainData::getChineseName)
            .filter(v -> v != null && !v.trim().isEmpty())
            .map(String::trim)
            .collect(Collectors.toSet());
        
        // 检查新数据是否与贯标数据重复
        for (int i = 0; i < newDataList.size(); i++) {
            DomainData data = newDataList.get(i);
            int rowNum = i + 2; // Excel行号
            
            if (data.getChineseName() != null && !data.getChineseName().trim().isEmpty()) {
                String chineseName = data.getChineseName().trim();
                if (standardChineseNameSet.contains(chineseName)) {
                    result.addError("域清单（在途）", "域中文名称", "duplicate_with_standard",
                        Collections.singletonList(rowNum), chineseName + "（与贯标数据重复）");
                }
            }
        }
    }
    
    /**
     * 校验代码扩展清单（在途）
     * 代码域中文名称+代码取值 不能与贯标数据重复
     * 注意：因为是全量在途数据导入，不需要与在途数据校验（会一直重复）
     */
    private void validateCodeExtensionDataIng(List<CodeExtensionData> newDataList, ValidationResult result) {
        // 获取贯标数据
        List<CodeExtensionData> standardCodeDataList = codeExtensionDataService.list();
        
        // 构建贯标数据的组合键集合
        Set<String> standardCompositeKeySet = standardCodeDataList.stream()
            .filter(data -> data.getCodeDomainChineseName() != null && !data.getCodeDomainChineseName().trim().isEmpty() &&
                           data.getCodeValue() != null && !data.getCodeValue().trim().isEmpty())
            .map(data -> data.getCodeDomainChineseName().trim() + "|" + data.getCodeValue().trim())
            .collect(Collectors.toSet());
        
        // 检查新数据是否与贯标数据重复
        for (int i = 0; i < newDataList.size(); i++) {
            CodeExtensionData data = newDataList.get(i);
            int rowNum = i + 2; // Excel行号
            
            if (data.getCodeDomainChineseName() != null && !data.getCodeDomainChineseName().trim().isEmpty() &&
                data.getCodeValue() != null && !data.getCodeValue().trim().isEmpty()) {
                
                String compositeKey = data.getCodeDomainChineseName().trim() + "|" + data.getCodeValue().trim();
                String displayValue = String.format("代码域：%s，代码取值：%s", 
                    data.getCodeDomainChineseName().trim(), data.getCodeValue().trim());
                
                if (standardCompositeKeySet.contains(compositeKey)) {
                    result.addError("代码扩展清单（在途）", "代码域中文名称+代码取值", "duplicate_with_standard",
                        Collections.singletonList(rowNum), displayValue + "（与贯标数据重复）");
                }
            }
        }
    }
}

