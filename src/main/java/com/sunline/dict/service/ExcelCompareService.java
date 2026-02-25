package com.sunline.dict.service;

import com.sunline.dict.common.CompareMode;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * Excel文档比对服务接口
 */
public interface ExcelCompareService {
    
    /**
     * 比较两个Excel文件（普通模式）
     * @param baseFile 基本版文件
     * @param compareFile 比较版文件
     * @return 比较结果信息
     */
    Map<String, Object> compareExcelFiles(MultipartFile baseFile, MultipartFile compareFile) throws Exception;
    
    /**
     * 比较两个Excel文件（指定模式）
     * @param baseFile 基本版文件
     * @param compareFile 比较版文件
     * @param mode 比对模式
     * @return 比较结果信息
     */
    Map<String, Object> compareExcelFiles(MultipartFile baseFile, MultipartFile compareFile, CompareMode mode) throws Exception;
    
    /**
     * 获取结果文件
     * @param fileName 文件名
     * @return 文件对象
     */
    File getResultFile(String fileName);
}

