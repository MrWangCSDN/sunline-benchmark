package com.sunline.dict.service.impl;

import com.sunline.dict.service.NewOldCoreErrorCodeCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 新老核心错误码比对服务实现
 * 委托给 ExcelCompareServiceImpl 的 compareNewOldCoreErrorCodes 方法
 */
@Service
public class NewOldCoreErrorCodeCompareServiceImpl implements NewOldCoreErrorCodeCompareService {

    private static final Logger log = LoggerFactory.getLogger(NewOldCoreErrorCodeCompareServiceImpl.class);

    @Autowired
    private ExcelCompareServiceImpl excelCompareService;

    @Override
    public Map<String, Object> compareFiles(MultipartFile oldFile, MultipartFile newFile, boolean firstSheetOnly)
            throws Exception {
        log.info("开始新老核心错误码比对，firstSheetOnly={}", firstSheetOnly);
        return excelCompareService.compareNewOldCoreErrorCodes(oldFile, newFile, firstSheetOnly);
    }

    @Override
    public File getResultFile(String fileName) {
        return excelCompareService.getResultFile(fileName);
    }
}
