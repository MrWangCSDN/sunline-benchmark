package com.sunline.dict.service.impl;

import com.sunline.dict.common.CompareMode;
import com.sunline.dict.service.EsfInterfaceCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * ESF接口文档比对服务实现
 * 委托给ExcelCompareService，使用ESF_INTERFACE模式
 */
@Service
public class EsfInterfaceCompareServiceImpl implements EsfInterfaceCompareService {
    
    private static final Logger log = LoggerFactory.getLogger(EsfInterfaceCompareServiceImpl.class);
    
    @Autowired
    private ExcelCompareServiceImpl excelCompareService;
    
    @Override
    public Map<String, Object> compareFiles(MultipartFile baseFile, MultipartFile compareFile) throws Exception {
        log.info("开始ESF接口文档比对，应用ESF特殊规则");
        
        // 委托给ExcelCompareService，使用ESF_INTERFACE模式
        // 这会自动应用：
        // 1. 识别输入/输出部分
        // 2. 只比对A、B、C、D列
        // 3. 按交易码汇总差异
        // 4. 生成特殊格式的修订记录
        return excelCompareService.compareExcelFiles(baseFile, compareFile, CompareMode.ESF_INTERFACE);
    }
    
    @Override
    public File getResultFile(String fileName) {
        return excelCompareService.getResultFile(fileName);
    }
}
