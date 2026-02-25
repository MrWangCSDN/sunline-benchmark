package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.common.ImportProgress;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.mapper.DictDataMapper;
import com.sunline.dict.service.DictDataService;
import com.sunline.dict.service.ProgressService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 字典数据服务实现类
 */
@Service
public class DictDataServiceImpl extends ServiceImpl<DictDataMapper, DictData> implements DictDataService {
    
    private static final Logger log = LoggerFactory.getLogger(DictDataServiceImpl.class);
    
    @Autowired
    private ProgressService progressService;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importExcel(MultipartFile file, String clientId) throws IOException {
        log.info("======================================");
        log.info("开始导入Excel文件: {}", file.getOriginalFilename());
        log.info("文件大小: {} bytes", file.getSize());
        log.info("客户端ID: {}", clientId);
        log.info("======================================");
        
        List<DictData> dataList = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            int totalDataRows = rowCount - 1; // 减去表头
            
            log.info("Excel总行数: {}", rowCount);
            log.info("数据行数: {}", totalDataRows);
            
            // 发送开始解析进度
            progressService.sendProgress(clientId, 
                new ImportProgress(totalDataRows, 0, "parsing", "开始解析Excel..."));
            
            // 跳过表头，从第二行开始读取
            for (int i = 1; i < rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                
                // 每处理10行发送一次进度
                if (i % 10 == 0 || i == rowCount - 1) {
                    int currentRow = i;
                    int percentage = (currentRow * 100 / totalDataRows);
                    progressService.sendProgress(clientId, 
                        new ImportProgress(totalDataRows, currentRow, "parsing", 
                            String.format("正在解析: %d/%d (%.1f%%)", currentRow, totalDataRows, percentage * 1.0)));
                }
                
                try {
                    DictData dictData = new DictData();
                    
                    // 设置排序号（保持Excel顺序）
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
                    
                    dictData.setCreateTime(LocalDateTime.now());
                    dictData.setUpdateTime(LocalDateTime.now());
                    
                    // 验证必填字段
                    if (StringUtils.hasText(dictData.getDataItemCode())) {
                        dataList.add(dictData);
                    }
                    
                } catch (Exception e) {
                    log.error("解析第{}行数据失败: {}", i + 1, e.getMessage());
                }
            }
        }
        
        // 批量插入数据
        if (!dataList.isEmpty()) {
            log.info("准备插入{}条数据到数据库", dataList.size());
            
            try {
                // 分批插入，每批500条
                int batchSize = 500;
                int totalSize = dataList.size();
                int batchCount = (totalSize + batchSize - 1) / batchSize;
                
                log.info("将分{}批次插入数据", batchCount);
                
                // 发送开始导入进度
                progressService.sendProgress(clientId, 
                    new ImportProgress(totalSize, 0, "importing", "开始导入数据库..."));
                
                for (int i = 0; i < totalSize; i += batchSize) {
                    int endIndex = Math.min(i + batchSize, totalSize);
                    List<DictData> batch = dataList.subList(i, endIndex);
                    int currentBatch = i / batchSize + 1;
                    
                    log.info("正在插入第{}/{}批，本批数量: {}", currentBatch, batchCount, batch.size());
                    
                    // 发送导入进度
                    int importedCount = i;
                    int percentage = (importedCount * 100 / totalSize);
                    progressService.sendProgress(clientId, 
                        new ImportProgress(totalSize, importedCount, "importing", 
                            String.format("正在导入: 第%d/%d批 (%d%%)", currentBatch, batchCount, percentage)));
                    
                    boolean result = this.saveBatch(batch);
                    
                    if (!result) {
                        log.error("第{}批插入失败", currentBatch);
                        progressService.complete(clientId, 
                            new ImportProgress(totalSize, i, "error", "第" + currentBatch + "批插入失败"));
                        throw new RuntimeException("批量插入数据失败");
                    }
                    
                    log.info("第{}/{}批插入成功", currentBatch, batchCount);
                }
                
                log.info("成功导入{}条数据", dataList.size());
                
                // 发送完成进度
                progressService.complete(clientId, 
                    new ImportProgress(totalSize, totalSize, "completed", 
                        "导入完成！共导入 " + totalSize + " 条数据"));
                
            } catch (Exception e) {
                log.error("批量插入数据时发生错误", e);
                progressService.complete(clientId, 
                    new ImportProgress(dataList.size(), 0, "error", "导入失败: " + e.getMessage()));
                throw new RuntimeException("批量插入数据失败: " + e.getMessage(), e);
            }
        } else {
            log.warn("没有有效数据需要导入");
            progressService.complete(clientId, 
                new ImportProgress(0, 0, "completed", "没有有效数据需要导入"));
        }
        
        return dataList.size();
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
                    // 避免科学计数法
                    cell.setCellType(CellType.STRING);
                    return cell.getStringCellValue().trim();
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue().trim();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }
    
    @Override
    public Page<DictData> pageQuery(int current, int size, String keyword) {
        Page<DictData> page = new Page<>(current, size);
        
        LambdaQueryWrapper<DictData> queryWrapper = new LambdaQueryWrapper<>();
        
        log.info("开始分页查询: current={}, size={}, keyword={}", current, size, keyword);
        
        // 只查询未删除的数据（修正：使用nested或者直接用括号）
        queryWrapper.nested(wrapper -> wrapper
            .eq(DictData::getIsDeleted, 0)
            .or()
            .isNull(DictData::getIsDeleted)
        );
        
        // 支持多列搜索或单一keyword搜索
        if (StringUtils.hasText(keyword)) {
            log.info("搜索关键字不为空: {}", keyword);
            // 如果keyword包含":"，则按字段搜索格式解析（支持单字段或多字段）
            if (keyword.contains(":")) {
                log.info("检测到字段搜索格式");
                String[] conditions = keyword.split("\\|");
                // 使用and包裹所有搜索条件，确保与is_deleted条件正确组合
                queryWrapper.and(searchWrapper -> {
                    boolean isFirst = true;
                    for (String condition : conditions) {
                        if (StringUtils.hasText(condition)) {
                            String[] parts = condition.split(":", 2); // 限制分割为2部分，避免值中包含:的问题
                            if (parts.length == 2) {
                                String field = parts[0].trim();
                                String value = parts[1].trim();
                                log.info("字段搜索条件: field={}, value={}", field, value);
                                if (StringUtils.hasText(value)) {
                                    if (!isFirst) {
                                        searchWrapper.and(w -> applyFieldCondition(w, field, value));
                                    } else {
                                        applyFieldCondition(searchWrapper, field, value);
                                        isFirst = false;
                                    }
                                }
                            }
                        }
                    }
                });
            } else {
                log.info("使用传统单keyword搜索");
                // 传统的单keyword模糊搜索（使用and包裹，确保与is_deleted条件正确组合）
                queryWrapper.and(searchWrapper -> searchWrapper
                    .like(DictData::getDataItemCode, keyword)
                    .or().like(DictData::getEnglishAbbr, keyword)
                    .or().like(DictData::getChineseName, keyword)
                    .or().like(DictData::getDictAttr, keyword)
                    .or().like(DictData::getDataType, keyword)
                );
            }
        } else {
            log.info("搜索关键字为空，查询所有未删除数据");
        }
        
        // 按排序号升序排序（保持Excel顺序）
        queryWrapper.orderByAsc(DictData::getSortOrder);
        
        Page<DictData> result = this.page(page, queryWrapper);
        log.info("查询结果: total={}, records={}", result.getTotal(), result.getRecords().size());
        
        return result;
    }
    
    /**
     * 应用字段查询条件
     */
    private void applyFieldCondition(LambdaQueryWrapper<DictData> queryWrapper, String field, String value) {
        if (!StringUtils.hasText(value)) {
            log.debug("字段值为空，跳过: field={}", field);
            return;
        }
        
        log.info("应用字段查询条件: field={}, value={}", field, value);
        
        switch (field) {
            case "dataItemCode":
                queryWrapper.like(DictData::getDataItemCode, value);
                break;
            case "englishAbbr":
                queryWrapper.like(DictData::getEnglishAbbr, value);
                break;
            case "englishName":
                queryWrapper.like(DictData::getEnglishName, value);
                break;
            case "chineseName":
                queryWrapper.like(DictData::getChineseName, value);
                break;
            case "dictAttr":
                queryWrapper.like(DictData::getDictAttr, value);
                break;
            case "domainChineseName":
                queryWrapper.like(DictData::getDomainChineseName, value);
                break;
            case "dataType":
                queryWrapper.like(DictData::getDataType, value);
                break;
            case "dataFormat":
                queryWrapper.like(DictData::getDataFormat, value);
                break;
            case "valueRange":
                queryWrapper.like(DictData::getValueRange, value);
                break;
            case "javaEsfName":
                queryWrapper.like(DictData::getJavaEsfName, value);
                break;
            case "esfDataFormat":
                queryWrapper.like(DictData::getEsfDataFormat, value);
                break;
            case "gaussdbDataFormat":
                queryWrapper.like(DictData::getGaussdbDataFormat, value);
                break;
            case "goldendbDataFormat":
                queryWrapper.like(DictData::getGoldendbDataFormat, value);
                break;
            default:
                log.warn("未知的搜索字段: {}", field);
        }
    }
    
    @Override
    public List<DictData> getAllData() {
        LambdaQueryWrapper<DictData> queryWrapper = new LambdaQueryWrapper<>();
        // 只查询未删除的数据
        queryWrapper.and(wrapper -> wrapper.eq(DictData::getIsDeleted, 0)
                                          .or()
                                          .isNull(DictData::getIsDeleted));
        // 按排序号升序排序（保持Excel顺序）
        queryWrapper.orderByAsc(DictData::getSortOrder);
        return this.list(queryWrapper);
    }
    
    @Override
    public List<DictData> getAllActiveData() {
        LambdaQueryWrapper<DictData> queryWrapper = new LambdaQueryWrapper<>();
        // 只查询未删除的数据
        queryWrapper.and(wrapper -> wrapper.eq(DictData::getIsDeleted, 0)
                                          .or()
                                          .isNull(DictData::getIsDeleted));
        // 按排序号升序排序（保持Excel顺序）
        queryWrapper.orderByAsc(DictData::getSortOrder);
        return this.list(queryWrapper);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void truncateTable() {
        // 删除所有数据
        this.remove(new LambdaQueryWrapper<>());
    }
}

