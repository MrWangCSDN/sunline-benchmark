package com.sunline.dict.service.impl;

import com.sunline.dict.service.PublicCoreFileBaselineCompareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 对公核心文件基线比对服务实现
 * 委托给 ExcelCompareServiceImpl.comparePublicCoreFileBaseline
 */
@Service
public class PublicCoreFileBaselineCompareServiceImpl implements PublicCoreFileBaselineCompareService {

    private static final Logger log = LoggerFactory.getLogger(PublicCoreFileBaselineCompareServiceImpl.class);

    @Autowired
    private ExcelCompareServiceImpl excelCompareService;

    @Override
    public Map<String, Object> compareFiles(MultipartFile baselineFile, MultipartFile compareFile)
            throws Exception {
        log.info("开始对公核心文件基线比对");
        return excelCompareService.comparePublicCoreFileBaseline(baselineFile, compareFile);
    }

    @Override
    public File getResultFile(String fileName) {
        return excelCompareService.getResultFile(fileName);
    }
}
