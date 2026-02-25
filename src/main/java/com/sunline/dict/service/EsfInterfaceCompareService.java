package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * ESF接口文档比对服务接口
 */
public interface EsfInterfaceCompareService {
    
    /**
     * 比较两个ESF接口文档
     * @param baseFile 基础版文件
     * @param compareFile 比较版文件
     * @return 比较结果信息
     */
    Map<String, Object> compareFiles(MultipartFile baseFile, MultipartFile compareFile) throws Exception;
    
    /**
     * 获取结果文件
     * @param fileName 文件名
     * @return 文件对象
     */
    File getResultFile(String fileName);
}
