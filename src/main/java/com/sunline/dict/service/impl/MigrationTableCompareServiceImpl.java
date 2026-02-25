package com.sunline.dict.service.impl;

import com.sunline.dict.common.CompareMode;
import com.sunline.dict.service.MigrationTableCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 迁移中间表比对服务实现
 * 委托给ExcelCompareService，使用MIGRATION_TABLE模式
 */
@Service
public class MigrationTableCompareServiceImpl implements MigrationTableCompareService {
    
    private static final Logger log = LoggerFactory.getLogger(MigrationTableCompareServiceImpl.class);
    
    @Autowired
    private ExcelCompareServiceImpl excelCompareService;
    
    @Override
    public Map<String, Object> compareFiles(MultipartFile xmlFile, MultipartFile migrationFile) throws Exception {
        log.info("开始迁移中间表比对");
        
        // 委托给ExcelCompareService，使用MIGRATION_TABLE模式
        // 这会自动应用：
        // 1. 跳过"表总览"sheet
        // 2. 字段部分特殊映射（XML列 -> 结果列）
        // 3. 字段修改：黄色显示最新值
        // 4. 字段新增：绿色显示
        // 5. 字段删除：修订记录中体现
        // 6. 生成修订记录sheet（带双向超链接）
        return excelCompareService.compareExcelFiles(xmlFile, migrationFile, CompareMode.MIGRATION_TABLE);
    }
    
    @Override
    public File getResultFile(String fileName) {
        return excelCompareService.getResultFile(fileName);
    }
}
