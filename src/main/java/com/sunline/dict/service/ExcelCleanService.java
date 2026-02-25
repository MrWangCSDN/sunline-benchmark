package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * Excel数据清洗服务接口
 */
public interface ExcelCleanService {
    
    /**
     * 清洗Excel文件
     * @param excelFile Excel文件
     * @param sheetNames 要保留的sheet页名称（换行分隔，可选）
     * @param renameRules sheet页名称替换规则（换行分隔，格式：旧名称 --> 新名称，可选）
     * @return 清洗结果信息
     */
    Map<String, Object> cleanExcelFile(MultipartFile excelFile, String sheetNames, String renameRules) throws Exception;
    
    /**
     * 获取结果文件
     * @param fileName 文件名
     * @return 文件对象
     */
    File getResultFile(String fileName);
}
