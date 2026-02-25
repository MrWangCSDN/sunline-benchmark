package com.sunline.dict.service.impl;

import com.sunline.dict.common.CompareMode;
import com.sunline.dict.service.XmlDbCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * XML模型与数据库比对服务实现
 * 委托给ExcelCompareService，使用XML_DB模式
 */
@Service
public class XmlDbCompareServiceImpl implements XmlDbCompareService {
    
    private static final Logger log = LoggerFactory.getLogger(XmlDbCompareServiceImpl.class);
    
    @Autowired
    private ExcelCompareServiceImpl excelCompareService;
    
    @Override
    public Map<String, Object> compareFiles(MultipartFile xmlFile, MultipartFile dbFile) throws Exception {
        log.info("开始XML模型与数据库比对，应用XML-DB特殊规则");
        
        // 委托给ExcelCompareService，使用XML_DB模式
        // 这会自动应用：
        // 1. 只比对A、B、D、F、G列
        // 2. D列类型等价（date=timestamp, numeric=decimal）
        // 3. G列默认值处理（去除::后缀）
        // 4. 修订记录改名为"差异结果"
        return excelCompareService.compareExcelFiles(xmlFile, dbFile, CompareMode.XML_DB);
    }
    
    @Override
    public File getResultFile(String fileName) {
        // 使用ExcelCompareService的结果目录
        return excelCompareService.getResultFile(fileName);
    }
}

