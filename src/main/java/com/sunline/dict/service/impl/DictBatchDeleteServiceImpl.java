package com.sunline.dict.service.impl;

import com.sunline.dict.entity.DictDataIng;
import com.sunline.dict.mapper.DictDataIngMapper;
import com.sunline.dict.service.DictBatchDeleteService;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 在途字典批量删除服务实现类
 */
@Service
public class DictBatchDeleteServiceImpl implements DictBatchDeleteService {
    
    private static final Logger log = LoggerFactory.getLogger(DictBatchDeleteServiceImpl.class);
    
    @Autowired
    private DictDataIngMapper dictDataIngMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchDelete(MultipartFile dictFile) throws Exception {
        log.info("==================== 开始批量删除在途字典数据 ====================");
        log.info("文件名: {}", dictFile.getOriginalFilename());
        
        // 保存上传的Excel文件到临时目录
        File tempExcelFile = saveTempFile(dictFile);
        
        try {
            // 读取Excel文件
            Workbook workbook = WorkbookFactory.create(tempExcelFile);
            Sheet sheet = workbook.getSheetAt(0); // 第一个sheet页
            
            log.info("读取Excel文件，sheet名称: {}, 总行数: {}", sheet.getSheetName(), sheet.getPhysicalNumberOfRows());
            
            // 收集J列的所有值（JAVA/ESF规范命名）
            List<String> javaEsfNames = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            
            // 从第2行开始（跳过表头）
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
                
                String javaEsfName = getCellValueAsString(jCell);
                if (javaEsfName != null && !javaEsfName.trim().isEmpty()) {
                    javaEsfNames.add(javaEsfName.trim());
                }
            }
            
            workbook.close();
            
            int totalCount = javaEsfNames.size();
            log.info("Excel中共读取到 {} 条JAVA/ESF规范命名数据", totalCount);
            
            if (totalCount == 0) {
                Map<String, Object> result = new HashMap<>();
                result.put("totalCount", 0);
                result.put("deletedCount", 0);
                result.put("notFoundCount", 0);
                result.put("notFoundList", new ArrayList<>());
                return result;
            }
            
            // 批量查询数据库中存在的记录
            List<String> notFoundList = new ArrayList<>();
            int deletedCount = 0;
            
            for (String javaEsfName : javaEsfNames) {
                try {
                    // 查询是否存在
                    DictDataIng dictData = dictDataIngMapper.selectByJavaEsfName(javaEsfName);
                    
                    if (dictData != null) {
                        // 物理删除
                        int deleted = dictDataIngMapper.deleteById(dictData.getId());
                        if (deleted > 0) {
                            deletedCount++;
                            log.info("已删除: {}", javaEsfName);
                        } else {
                            notFoundList.add(javaEsfName);
                            log.warn("删除失败: {}", javaEsfName);
                        }
                    } else {
                        notFoundList.add(javaEsfName);
                        log.warn("未找到匹配数据: {}", javaEsfName);
                    }
                } catch (Exception e) {
                    log.error("删除数据失败: {}", javaEsfName, e);
                    notFoundList.add(javaEsfName);
                }
            }
            
            int notFoundCount = notFoundList.size();
            
            log.info("==================== 批量删除完成 ====================");
            log.info("Excel总数: {}, 已删除: {}, 未匹配: {}", totalCount, deletedCount, notFoundCount);
            
            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("totalCount", totalCount);
            result.put("deletedCount", deletedCount);
            result.put("notFoundCount", notFoundCount);
            result.put("notFoundList", notFoundList);
            
            return result;
            
        } finally {
            // 删除临时Excel文件
            if (tempExcelFile.exists()) {
                tempExcelFile.delete();
            }
        }
    }
    
    /**
     * 保存上传的文件到临时目录
     */
    private File saveTempFile(MultipartFile file) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        File tempFile = new File(tempDir, "dict_delete_" + System.currentTimeMillis() + "_" + file.getOriginalFilename());
        file.transferTo(tempFile);
        return tempFile;
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
}

