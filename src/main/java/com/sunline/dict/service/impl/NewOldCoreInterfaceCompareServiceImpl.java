package com.sunline.dict.service.impl;

import com.sunline.dict.service.NewOldCoreInterfaceCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 新老核心接口文档比对服务实现
 * 委托给 ExcelCompareServiceImpl 的 compareNewOldCoreInterfaces 方法
 */
@Service
public class NewOldCoreInterfaceCompareServiceImpl implements NewOldCoreInterfaceCompareService {

    private static final Logger log = LoggerFactory.getLogger(NewOldCoreInterfaceCompareServiceImpl.class);

    @Autowired
    private ExcelCompareServiceImpl excelCompareService;

    @Override
    public Map<String, Object> compareFiles(MultipartFile oldFile, MultipartFile newFile, String excludeSheets)
            throws Exception {
        log.info("开始新老核心接口文档比对，excludeSheets={}", excludeSheets);
        return excelCompareService.compareNewOldCoreInterfaces(oldFile, newFile, excludeSheets);
    }

    @Override
    public File getResultFile(String fileName) {
        return excelCompareService.getResultFile(fileName);
    }
}
