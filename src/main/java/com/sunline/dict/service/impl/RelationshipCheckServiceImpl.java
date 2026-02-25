package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunline.dict.entity.CodeExtensionData;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.entity.DomainData;
import com.sunline.dict.service.CodeExtensionDataService;
import com.sunline.dict.service.DictDataService;
import com.sunline.dict.service.DomainDataService;
import com.sunline.dict.service.RelationshipCheckService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关联性检查服务实现
 */
@Service
public class RelationshipCheckServiceImpl implements RelationshipCheckService {
    
    private static final Logger log = LoggerFactory.getLogger(RelationshipCheckServiceImpl.class);
    
    @Autowired
    private DictDataService dictDataService;
    
    @Autowired
    private DomainDataService domainDataService;
    
    @Autowired
    private CodeExtensionDataService codeExtensionDataService;
    
    @Override
    public byte[] performCheck() throws Exception {
        log.info("开始执行关联性检查");
        
        // 第一类校验：字典衍生表的域中文名称必须在域清单中找到
        List<DictData> dictErrors = checkDictToDomain();
        log.info("字典检查完成，发现 {} 条问题记录", dictErrors.size());
        
        // 第二类校验：域清单中域组为"代码类"的域中文名称必须在代码扩展清单中找到
        List<DomainData> domainErrors = checkDomainToCode();
        log.info("域清单检查完成，发现 {} 条问题记录", domainErrors.size());
        
        // 如果都没有问题，返回null
        if (dictErrors.isEmpty() && domainErrors.isEmpty()) {
            log.info("关联性检查通过，无问题记录");
            return null;
        }
        
        // 生成Excel报告
        log.info("生成Excel报告，字典问题 {} 条，域清单问题 {} 条", dictErrors.size(), domainErrors.size());
        return generateExcelReport(dictErrors, domainErrors);
    }
    
    /**
     * 第一类校验：字典衍生表的域中文名称必须在域清单中找到
     */
    private List<DictData> checkDictToDomain() {
        // 查询所有字典数据
        QueryWrapper<DictData> dictQuery = new QueryWrapper<>();
        dictQuery.eq("is_deleted", 0);
        List<DictData> allDictData = dictDataService.list(dictQuery);
        log.info("查询到字典数据 {} 条", allDictData.size());
        
        // 查询所有域清单数据的域中文名称
        QueryWrapper<DomainData> domainQuery = new QueryWrapper<>();
        domainQuery.select("chinese_name");
        List<DomainData> allDomainData = domainDataService.list(domainQuery);
        Set<String> domainChineseNames = allDomainData.stream()
            .map(DomainData::getChineseName)
            .filter(name -> name != null && !name.isEmpty())
            .collect(Collectors.toSet());
        log.info("查询到域清单中的域中文名称 {} 个", domainChineseNames.size());
        
        // 检查字典中的域中文名称是否都在域清单中
        List<DictData> errors = new ArrayList<>();
        for (DictData dict : allDictData) {
            String domainChineseName = dict.getDomainChineseName();
            if (domainChineseName != null && !domainChineseName.isEmpty()) {
                if (!domainChineseNames.contains(domainChineseName)) {
                    errors.add(dict);
                    log.debug("字典记录 {} 的域中文名称 '{}' 在域清单中未找到", 
                        dict.getDataItemCode(), domainChineseName);
                }
            }
        }
        
        return errors;
    }
    
    /**
     * 第二类校验：域清单中域组为"代码类"的域中文名称必须在代码扩展清单中找到
     */
    private List<DomainData> checkDomainToCode() {
        // 查询所有域组为"代码类"的域清单数据
        QueryWrapper<DomainData> domainQuery = new QueryWrapper<>();
        domainQuery.eq("domain_group", "代码类");
        List<DomainData> codeDomains = domainDataService.list(domainQuery);
        log.info("查询到域组为'代码类'的域清单数据 {} 条", codeDomains.size());
        
        // 查询所有代码扩展清单数据的代码域中文名称
        QueryWrapper<CodeExtensionData> codeQuery = new QueryWrapper<>();
        codeQuery.select("code_domain_chinese_name");
        List<CodeExtensionData> allCodeData = codeExtensionDataService.list(codeQuery);
        Set<String> codeDomainNames = allCodeData.stream()
            .map(CodeExtensionData::getCodeDomainChineseName)
            .filter(name -> name != null && !name.isEmpty())
            .collect(Collectors.toSet());
        log.info("查询到代码扩展清单中的代码域中文名称 {} 个", codeDomainNames.size());
        
        // 检查代码类域的域中文名称是否都在代码扩展清单中
        List<DomainData> errors = new ArrayList<>();
        for (DomainData domain : codeDomains) {
            String chineseName = domain.getChineseName();
            if (chineseName != null && !chineseName.isEmpty()) {
                if (!codeDomainNames.contains(chineseName)) {
                    errors.add(domain);
                    log.debug("域清单记录 '{}' 在代码扩展清单中未找到", chineseName);
                }
            }
        }
        
        return errors;
    }
    
    /**
     * 生成Excel报告
     */
    private byte[] generateExcelReport(List<DictData> dictErrors, List<DomainData> domainErrors) throws Exception {
        Workbook workbook = new XSSFWorkbook();
        
        // 创建样式
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle dataStyle = createDataStyle(workbook);
        
        // 如果有字典问题，创建"字典检查"sheet
        if (!dictErrors.isEmpty()) {
            createDictCheckSheet(workbook, dictErrors, headerStyle, dataStyle);
        }
        
        // 如果有域清单问题，创建"域清单检查"sheet
        if (!domainErrors.isEmpty()) {
            createDomainCheckSheet(workbook, domainErrors, headerStyle, dataStyle);
        }
        
        // 写入字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        
        return outputStream.toByteArray();
    }
    
    /**
     * 创建字典检查Sheet
     */
    private void createDictCheckSheet(Workbook workbook, List<DictData> errors, 
                                     CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("字典检查");
        
        // 创建表头
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "数据项编号", "英文简称", "中文名称", "字典属性", "域中文名称", 
            "数据类型", "数据格式", "JAVA/ESF规范命名", "ESF数据格式", 
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
        for (DictData dict : errors) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            
            createCell(row, colNum++, dict.getDataItemCode(), dataStyle);
            createCell(row, colNum++, dict.getEnglishAbbr(), dataStyle);
            createCell(row, colNum++, dict.getChineseName(), dataStyle);
            createCell(row, colNum++, dict.getDictAttr(), dataStyle);
            createCell(row, colNum++, dict.getDomainChineseName(), dataStyle);
            createCell(row, colNum++, dict.getDataType(), dataStyle);
            createCell(row, colNum++, dict.getDataFormat(), dataStyle);
            createCell(row, colNum++, dict.getJavaEsfName(), dataStyle);
            createCell(row, colNum++, dict.getEsfDataFormat(), dataStyle);
            createCell(row, colNum++, dict.getGaussdbDataFormat(), dataStyle);
            createCell(row, colNum++, dict.getGoldendbDataFormat(), dataStyle);
        }
        
        log.info("创建字典检查Sheet，共 {} 条记录", errors.size());
    }
    
    /**
     * 创建域清单检查Sheet
     */
    private void createDomainCheckSheet(Workbook workbook, List<DomainData> errors,
                                       CellStyle headerStyle, CellStyle dataStyle) {
        Sheet sheet = workbook.createSheet("域清单检查");
        
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
        for (DomainData domain : errors) {
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
        
        log.info("创建域清单检查Sheet，共 {} 条记录", errors.size());
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
}

