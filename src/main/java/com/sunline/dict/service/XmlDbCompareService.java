package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * XML模型与数据库比对服务接口
 */
public interface XmlDbCompareService {
    
    /**
     * 比较XML模型和数据库表结构
     * @param xmlFile XML模型文件
     * @param dbFile 数据库表结构文件
     * @return 比较结果信息
     */
    Map<String, Object> compareFiles(MultipartFile xmlFile, MultipartFile dbFile) throws Exception;
    
    /**
     * 获取结果文件
     * @param fileName 文件名
     * @return 文件对象
     */
    File getResultFile(String fileName);
}
