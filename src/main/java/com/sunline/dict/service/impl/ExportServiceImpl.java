package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.dto.MultiSheetChangeReport;
import com.sunline.dict.entity.CodeExtensionData;
import com.sunline.dict.entity.CodeExtensionDataIng;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.entity.DictDataIng;
import com.sunline.dict.entity.DomainData;
import com.sunline.dict.entity.DomainDataIng;
import com.sunline.dict.service.CodeExtensionDataIngService;
import com.sunline.dict.service.CodeExtensionDataService;
import com.sunline.dict.service.DictDataIngService;
import com.sunline.dict.service.DictDataService;
import com.sunline.dict.service.DomainDataIngService;
import com.sunline.dict.service.DomainDataService;
import com.sunline.dict.service.DomainMappingService;
import com.sunline.dict.service.EnumMappingService;
import com.sunline.dict.dto.ValidationResult;
import com.sunline.dict.service.ExportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * 数据导出服务实现
 */
@Service
public class ExportServiceImpl implements ExportService {
    
    private static final Logger log = LoggerFactory.getLogger(ExportServiceImpl.class);
    
    @Autowired
    private DictDataService dictDataService;
    
    @Autowired
    private DomainDataService domainDataService;
    
    @Autowired
    private CodeExtensionDataService codeExtensionDataService;
    
    @Autowired
    private DictDataIngService dictDataIngService;
    
    @Autowired
    private DomainDataIngService domainDataIngService;
    
    @Autowired
    private CodeExtensionDataIngService codeExtensionDataIngService;
    
    @Autowired
    private EnumMappingService enumMappingService;
    
    @Autowired
    private DomainMappingService domainMappingService;
    
    @Override
    public byte[] exportAllData(String exportType) throws Exception {
        if (exportType == null || exportType.isEmpty()) {
            exportType = "all"; // 默认全量数据
        }
        
        log.info("开始导出数据，类型: {}", exportType);
        
        Workbook workbook = new XSSFWorkbook();
        
        // 创建样式
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        
        List<DictData> allDictDataList = new java.util.ArrayList<>();
        List<DomainData> allDomainDataList = new java.util.ArrayList<>();
        List<CodeExtensionData> allCodeDataList = new java.util.ArrayList<>();
        
        if ("standard".equals(exportType)) {
            // 只导出贯标数据
            log.info("导出贯标数据");
            
            QueryWrapper<DictData> dictQuery = new QueryWrapper<>();
            dictQuery.eq("is_deleted", 0);
            dictQuery.orderByAsc("sort_order");
            allDictDataList = dictDataService.list(dictQuery);
            
            allDomainDataList = domainDataService.list();
            allCodeDataList = codeExtensionDataService.list();
            
            log.info("贯标数据: 字典{}条, 域清单{}条, 代码扩展{}条", 
                allDictDataList.size(), allDomainDataList.size(), allCodeDataList.size());
            
        } else if ("ing".equals(exportType)) {
            // 只导出在途数据
            log.info("导出在途数据");
            
            QueryWrapper<DictDataIng> dictIngQuery = new QueryWrapper<>();
            dictIngQuery.eq("is_deleted", 0).or().isNull("is_deleted");
            dictIngQuery.orderByAsc("sort_order");
            List<DictDataIng> dictDataIngList = dictDataIngService.list(dictIngQuery);
            
            List<DomainDataIng> domainDataIngList = domainDataIngService.list();
            List<CodeExtensionDataIng> codeDataIngList = codeExtensionDataIngService.list();
            
            // 转换为标准数据格式
            for (DictDataIng ing : dictDataIngList) {
                allDictDataList.add(convertDictDataIngToDictData(ing));
            }
            for (DomainDataIng ing : domainDataIngList) {
                allDomainDataList.add(convertDomainDataIngToDomainData(ing));
            }
            for (CodeExtensionDataIng ing : codeDataIngList) {
                allCodeDataList.add(convertCodeExtensionDataIngToCodeExtensionData(ing));
            }
            
            log.info("在途数据: 字典{}条, 域清单{}条, 代码扩展{}条", 
                allDictDataList.size(), allDomainDataList.size(), allCodeDataList.size());
            
        } else {
            // 导出全量数据（贯标+在途）
            log.info("导出全量数据（贯标数据+在途数据）");
            
            // 查询贯标数据
            QueryWrapper<DictData> dictQuery = new QueryWrapper<>();
            dictQuery.eq("is_deleted", 0);
            dictQuery.orderByAsc("sort_order");
            List<DictData> dictDataList = dictDataService.list(dictQuery);
            
            List<DomainData> domainDataList = domainDataService.list();
            List<CodeExtensionData> codeDataList = codeExtensionDataService.list();
            
            // 查询在途数据
            QueryWrapper<DictDataIng> dictIngQuery = new QueryWrapper<>();
            dictIngQuery.eq("is_deleted", 0).or().isNull("is_deleted");
            dictIngQuery.orderByAsc("sort_order");
            List<DictDataIng> dictDataIngList = dictDataIngService.list(dictIngQuery);
            
            List<DomainDataIng> domainDataIngList = domainDataIngService.list();
            List<CodeExtensionDataIng> codeDataIngList = codeExtensionDataIngService.list();
            
            log.info("贯标数据: 字典{}条, 域清单{}条, 代码扩展{}条", 
                dictDataList.size(), domainDataList.size(), codeDataList.size());
            log.info("在途数据: 字典{}条, 域清单{}条, 代码扩展{}条", 
                dictDataIngList.size(), domainDataIngList.size(), codeDataIngList.size());
            
            // 合并数据：贯标数据 + 在途数据
            allDictDataList = new java.util.ArrayList<>(dictDataList);
            for (DictDataIng ing : dictDataIngList) {
                allDictDataList.add(convertDictDataIngToDictData(ing));
            }
            
            allDomainDataList = new java.util.ArrayList<>(domainDataList);
            for (DomainDataIng ing : domainDataIngList) {
                allDomainDataList.add(convertDomainDataIngToDomainData(ing));
            }
            
            allCodeDataList = new java.util.ArrayList<>(codeDataList);
            for (CodeExtensionDataIng ing : codeDataIngList) {
                allCodeDataList.add(convertCodeExtensionDataIngToCodeExtensionData(ing));
            }
            
            log.info("合并后数据: 字典{}条, 域清单{}条, 代码扩展{}条", 
                allDictDataList.size(), allDomainDataList.size(), allCodeDataList.size());
        }
        
        // 填充域清单的映射域英文简称
        fillMappedEnglishAbbr(allDomainDataList);
        
        // 填充代码扩展清单的映射代码含义英文简称
        fillMappedCodeEnglishAbbr(allCodeDataList);
        
        // 根据导出类型设置Sheet名称
        String dictSheetName = "字典技术衍生表";
        String domainSheetName = "域清单";
        String codeSheetName = "代码扩展清单";
        
        if ("standard".equals(exportType)) {
            dictSheetName = "字典技术衍生表";
            domainSheetName = "域清单";
            codeSheetName = "代码扩展清单";
        } else if ("ing".equals(exportType)) {
            dictSheetName = "字典技术衍生表";
            domainSheetName = "域清单";
            codeSheetName = "代码扩展清单";
        }
        
        // 创建三个Sheet
        createDictSheet(workbook, allDictDataList, headerStyle, dataStyle, dictSheetName);
        createDomainSheet(workbook, allDomainDataList, headerStyle, dataStyle, domainSheetName);
        createCodeExtensionSheet(workbook, allCodeDataList, headerStyle, dataStyle, codeSheetName);
        
        // 写入字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        log.info("数据导出成功，类型: {}, 总计: 字典{}条, 域清单{}条, 代码扩展{}条", 
            exportType, allDictDataList.size(), allDomainDataList.size(), allCodeDataList.size());
        
        return outputStream.toByteArray();
    }
    
    /**
     * 转换DictDataIng到DictData
     */
    private DictData convertDictDataIngToDictData(DictDataIng ing) {
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
        domain.setCreateTime(ing.getCreateTime());
        domain.setUpdateTime(ing.getUpdateTime());
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
        code.setCreateTime(ing.getCreateTime());
        code.setUpdateTime(ing.getUpdateTime());
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
            // 导出时，将"标志类"替换为"代码类"（仅针对Excel导出，不影响数据库）
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
            
            createCell(row, colNum++, domain.getDomainNumber() != null ? domain.getDomainNumber().toString() : "", dataStyle);
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
            "代码域编号", "代码域中文名称", "代码取值", "取值含义中文名称",
            "代码含义英文名称", "代码含义英文简称", "代码描述", "域规则",
            "代码域来源", "来源编号", "备注"
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
     * 创建表头样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        
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
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        
        return style;
    }
    
    /**
     * 创建单元格并设置值
     */
    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }
    
    /**
     * 填充域清单的映射域英文简称
     * 1. 如果域组是"代码类"，从枚举映射表中查找
     * 2. 如果域组是"非代码类"，从域清单映射表中查找
     */
    private void fillMappedEnglishAbbr(List<DomainData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        
        // 一次性加载所有映射到Map中，避免N+1查询问题
        java.util.Map<String, String> enumMappings = enumMappingService.getAllDomainMappings(); // 代码类映射
        java.util.Map<String, String> domainMappings = domainMappingService.getAllDomainMappings(); // 非代码类映射
        
        for (DomainData domain : dataList) {
            
            String chineseName = domain.getChineseName();
            if (chineseName == null || chineseName.trim().isEmpty()) {
                continue;
            }
            
            String domainGroup = domain.getDomainGroup();
            String mappedAbbr = null;
            
            // 根据域组选择不同的映射表
            if ("代码类".equals(domainGroup)) {
                // 代码类：从枚举映射表查找
                mappedAbbr = enumMappings.get(chineseName.trim());
            } else if (domainGroup != null && !domainGroup.trim().isEmpty()) {
                // 非代码类：从域清单映射表查找
                mappedAbbr = domainMappings.get(chineseName.trim());
            }
            
            // 如果找到映射，填充域英文简称
            if (mappedAbbr != null && !mappedAbbr.trim().isEmpty()) {
                domain.setEnglishAbbr(mappedAbbr);
            }
        }
    }
    
    /**
     * 填充代码扩展清单的映射代码含义英文简称
     * 通过代码域中文名称+取值含义中文名称去枚举映射表中查找
     */
    private void fillMappedCodeEnglishAbbr(List<CodeExtensionData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        
        // 一次性加载所有映射到Map中，避免N+1查询问题
        java.util.Map<String, String> mappings = enumMappingService.getAllCodeMappings();
        
        for (CodeExtensionData code : dataList) {
            String domainChineseName = code.getCodeDomainChineseName();
            String codeValue = code.getCodeValue();
            
            if (domainChineseName != null && !domainChineseName.trim().isEmpty() &&
                codeValue != null && !codeValue.trim().isEmpty()) {
                // 组合键：域中文名称|代码取值
                String key = domainChineseName.trim() + "|" + codeValue.trim();
                // 从Map中查找映射
                String mappedAbbr = mappings.get(key);
                if (mappedAbbr != null && !mappedAbbr.isEmpty()) {
                    // 如果找到映射，填充代码含义英文简称
                    code.setCodeEnglishAbbr(mappedAbbr);
                }
            }
        }
    }
    
    @Override
    public byte[] exportChangeAnalysisReport(MultiSheetChangeReport changeReport) throws Exception {
        log.info("开始导出分析报告");
        
        Workbook workbook = new XSSFWorkbook();
        
        // 创建样式
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        
        // 导出字典变更
        if (changeReport.getDictChangeReport() != null) {
            ChangeReport dictReport = changeReport.getDictChangeReport();
            // 字典(新增)
            if (dictReport.getNewList() != null && !dictReport.getNewList().isEmpty()) {
                createDictChangeSheet(workbook, "字典(新增)", dictReport.getNewList(), headerStyle, dataStyle);
            }
            // 字典(修改)
            if (dictReport.getUpdateList() != null && !dictReport.getUpdateList().isEmpty()) {
                createDictChangeSheet(workbook, "字典(修改)", dictReport.getUpdateList(), headerStyle, dataStyle);
            }
            // 字典(删除)
            if (dictReport.getDeleteList() != null && !dictReport.getDeleteList().isEmpty()) {
                createDictChangeSheet(workbook, "字典(删除)", dictReport.getDeleteList(), headerStyle, dataStyle);
            }
        }
        
        // 导出域清单变更
        if (changeReport.getDomainChangeReport() != null) {
            ChangeReport domainReport = changeReport.getDomainChangeReport();
            // 域清单(新增)
            if (domainReport.getNewList() != null && !domainReport.getNewList().isEmpty()) {
                createDomainChangeSheet(workbook, "域清单(新增)", domainReport.getNewList(), headerStyle, dataStyle);
            }
            // 域清单(修改)
            if (domainReport.getUpdateList() != null && !domainReport.getUpdateList().isEmpty()) {
                createDomainChangeSheet(workbook, "域清单(修改)", domainReport.getUpdateList(), headerStyle, dataStyle);
            }
            // 域清单(删除)
            if (domainReport.getDeleteList() != null && !domainReport.getDeleteList().isEmpty()) {
                createDomainChangeSheet(workbook, "域清单(删除)", domainReport.getDeleteList(), headerStyle, dataStyle);
            }
        }
        
        // 导出代码扩展清单变更
        if (changeReport.getCodeExtensionChangeReport() != null) {
            ChangeReport codeReport = changeReport.getCodeExtensionChangeReport();
            // 代码扩展清单(新增)
            if (codeReport.getNewList() != null && !codeReport.getNewList().isEmpty()) {
                createCodeExtensionChangeSheet(workbook, "代码扩展清单(新增)", codeReport.getNewList(), headerStyle, dataStyle);
            }
            // 代码扩展清单(修改)
            if (codeReport.getUpdateList() != null && !codeReport.getUpdateList().isEmpty()) {
                createCodeExtensionChangeSheet(workbook, "代码扩展清单(修改)", codeReport.getUpdateList(), headerStyle, dataStyle);
            }
            // 代码扩展清单(删除)
            if (codeReport.getDeleteList() != null && !codeReport.getDeleteList().isEmpty()) {
                createCodeExtensionChangeSheet(workbook, "代码扩展清单(删除)", codeReport.getDeleteList(), headerStyle, dataStyle);
            }
        }
        
        // 写入字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        log.info("分析报告导出完成");
        return outputStream.toByteArray();
    }
    
    /**
     * 创建字典变更Sheet
     */
    private void createDictChangeSheet(Workbook workbook, String sheetName, List<Object> dataList,
                                       CellStyle headerStyle, CellStyle dataStyle) {
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
        for (Object obj : dataList) {
            if (obj instanceof DictData) {
                DictData dict = (DictData) obj;
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
    }
    
    /**
     * 创建域清单变更Sheet
     */
    private void createDomainChangeSheet(Workbook workbook, String sheetName, List<Object> dataList,
                                         CellStyle headerStyle, CellStyle dataStyle) {
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
        for (Object obj : dataList) {
            if (obj instanceof DomainData) {
                DomainData domain = (DomainData) obj;
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;
                
                createCell(row, colNum++, domain.getDomainNumber() != null ? domain.getDomainNumber().toString() : "", dataStyle);
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
    }
    
    /**
     * 创建代码扩展清单变更Sheet
     */
    private void createCodeExtensionChangeSheet(Workbook workbook, String sheetName, List<Object> dataList,
                                               CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet(sheetName);
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "代码域编号", "代码域中文名称", "代码取值", "取值含义中文名称",
            "代码含义英文名称", "代码含义英文简称", "代码描述", "域规则",
            "代码域来源", "来源编号", "备注"
        };
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 4000);
        }
        
        // 填充数据
        int rowNum = 1;
        for (Object obj : dataList) {
            if (obj instanceof CodeExtensionData) {
                CodeExtensionData code = (CodeExtensionData) obj;
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
    }
    
    @Override
    public ValidationResult validateAllDataForExport() throws Exception {
        log.info("开始校验全量数据导出前的数据完整性");
        
        ValidationResult result = new ValidationResult();
        
        // 获取全量数据（贯标+在途）
        QueryWrapper<DictData> dictQuery = new QueryWrapper<>();
        dictQuery.eq("is_deleted", 0);
        dictQuery.orderByAsc("sort_order");
        List<DictData> dictDataList = dictDataService.list(dictQuery);
        
        List<DomainData> domainDataList = domainDataService.list();
        List<CodeExtensionData> codeDataList = codeExtensionDataService.list();
        
        // 查询在途数据
        QueryWrapper<DictDataIng> dictIngQuery = new QueryWrapper<>();
        dictIngQuery.eq("is_deleted", 0).or().isNull("is_deleted");
        dictIngQuery.orderByAsc("sort_order");
        List<DictDataIng> dictDataIngList = dictDataIngService.list(dictIngQuery);
        
        List<DomainDataIng> domainDataIngList = domainDataIngService.list();
        List<CodeExtensionDataIng> codeDataIngList = codeExtensionDataIngService.list();
        
        // 转换为标准数据格式并合并
        List<DictData> allDictDataList = new ArrayList<>(dictDataList);
        for (DictDataIng ing : dictDataIngList) {
            allDictDataList.add(convertDictDataIngToDictData(ing));
        }
        
        List<DomainData> allDomainDataList = new ArrayList<>(domainDataList);
        for (DomainDataIng ing : domainDataIngList) {
            allDomainDataList.add(convertDomainDataIngToDomainData(ing));
        }
        
        List<CodeExtensionData> allCodeDataList = new ArrayList<>(codeDataList);
        for (CodeExtensionDataIng ing : codeDataIngList) {
            allCodeDataList.add(convertCodeExtensionDataIngToCodeExtensionData(ing));
        }
        
        // 1. 校验字典技术衍生表
        validateDictDataForExport(allDictDataList, result);
        
        // 2. 校验域清单
        validateDomainDataForExport(allDomainDataList, result);
        
        // 3. 校验代码扩展清单
        validateCodeExtensionDataForExport(allCodeDataList, result);
        
        log.info("全量数据校验完成，是否有错误: {}", result.hasErrors());
        
        return result;
    }
    
    /**
     * 校验字典技术衍生表数据
     * 英文简称、中文名称、JAVA/ESF规范命名 任意一列都不允许重复
     */
    private void validateDictDataForExport(List<DictData> dataList, ValidationResult result) {
        // 校验英文简称
        Map<String, List<Integer>> englishAbbrMap = new HashMap<>();
        for (int i = 0; i < dataList.size(); i++) {
            DictData data = dataList.get(i);
            if (data.getEnglishAbbr() != null && !data.getEnglishAbbr().trim().isEmpty()) {
                String key = data.getEnglishAbbr().trim();
                englishAbbrMap.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 2); // Excel行号从2开始（第1行是表头）
            }
        }
        for (Map.Entry<String, List<Integer>> entry : englishAbbrMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.addError("字典技术衍生表", "英文简称", "duplicate", entry.getValue(), entry.getKey());
            }
        }
        
        // 校验中文名称
        Map<String, List<Integer>> chineseNameMap = new HashMap<>();
        for (int i = 0; i < dataList.size(); i++) {
            DictData data = dataList.get(i);
            if (data.getChineseName() != null && !data.getChineseName().trim().isEmpty()) {
                String key = data.getChineseName().trim();
                chineseNameMap.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 2);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : chineseNameMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.addError("字典技术衍生表", "中文名称", "duplicate", entry.getValue(), entry.getKey());
            }
        }
        
        // 校验JAVA/ESF规范命名
        Map<String, List<Integer>> javaEsfNameMap = new HashMap<>();
        for (int i = 0; i < dataList.size(); i++) {
            DictData data = dataList.get(i);
            if (data.getJavaEsfName() != null && !data.getJavaEsfName().trim().isEmpty()) {
                String key = data.getJavaEsfName().trim();
                javaEsfNameMap.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 2);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : javaEsfNameMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.addError("字典技术衍生表", "JAVA/ESF规范命名", "duplicate", entry.getValue(), entry.getKey());
            }
        }
    }
    
    /**
     * 校验域清单数据
     * 域中文名称、域英文名称任意一列不允许重复
     */
    private void validateDomainDataForExport(List<DomainData> dataList, ValidationResult result) {
        // 校验域中文名称
        Map<String, List<Integer>> chineseNameMap = new HashMap<>();
        for (int i = 0; i < dataList.size(); i++) {
            DomainData data = dataList.get(i);
            if (data.getChineseName() != null && !data.getChineseName().trim().isEmpty()) {
                String key = data.getChineseName().trim();
                chineseNameMap.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 2);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : chineseNameMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.addError("域清单", "域中文名称", "duplicate", entry.getValue(), entry.getKey());
            }
        }
        
        // 校验域英文名称
        Map<String, List<Integer>> englishNameMap = new HashMap<>();
        for (int i = 0; i < dataList.size(); i++) {
            DomainData data = dataList.get(i);
            if (data.getEnglishName() != null && !data.getEnglishName().trim().isEmpty()) {
                String key = data.getEnglishName().trim();
                englishNameMap.computeIfAbsent(key, k -> new ArrayList<>()).add(i + 2);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : englishNameMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.addError("域清单", "域英文名称", "duplicate", entry.getValue(), entry.getKey());
            }
        }
    }
    
    /**
     * 校验代码扩展清单数据
     * 代码域中文名称+代码取值 作为唯一值，不能重复
     */
    private void validateCodeExtensionDataForExport(List<CodeExtensionData> dataList, ValidationResult result) {
        Map<String, List<Integer>> compositeKeyMap = new HashMap<>();
        for (int i = 0; i < dataList.size(); i++) {
            CodeExtensionData data = dataList.get(i);
            String codeDomainChineseName = data.getCodeDomainChineseName() != null ? data.getCodeDomainChineseName().trim() : "";
            String codeValue = data.getCodeValue() != null ? data.getCodeValue().trim() : "";
            if (!codeDomainChineseName.isEmpty() && !codeValue.isEmpty()) {
                String compositeKey = codeDomainChineseName + "|" + codeValue;
                compositeKeyMap.computeIfAbsent(compositeKey, k -> new ArrayList<>()).add(i + 2);
            }
        }
        for (Map.Entry<String, List<Integer>> entry : compositeKeyMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                String[] parts = entry.getKey().split("\\|", 2);
                String displayValue = parts.length == 2 ? parts[0] + " + " + parts[1] : entry.getKey();
                result.addError("代码扩展清单", "代码域中文名称+代码取值", "duplicate", entry.getValue(), displayValue);
            }
        }
    }
}

