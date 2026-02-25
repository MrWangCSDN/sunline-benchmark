package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 在途字典使用分析服务接口
 */
public interface DictUsageAnalysisService {
    
    /**
     * 分析字典字段在XML代码中的使用情况
     * @param codeDirectory 代码目录路径
     * @param dictFile 字典Excel文件
     * @return 分析结果，包含统计信息和输出文件路径
     */
    Map<String, Object> analyzeUsage(String codeDirectory, MultipartFile dictFile) throws Exception;
    
    /**
     * 获取分析结果文件
     * @param fileName 文件名
     * @return 文件对象
     */
    File getAnalysisResultFile(String fileName);
}

