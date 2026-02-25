package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 迁移中间表比对服务接口
 */
public interface MigrationTableCompareService {
    
    /**
     * 比较XML表定义文档和迁移中间表文档
     * @param xmlFile XML表定义文档（左边）
     * @param migrationFile 迁移中间表文档（右边）
     * @return 比较结果信息
     */
    Map<String, Object> compareFiles(MultipartFile xmlFile, MultipartFile migrationFile) throws Exception;
    
    /**
     * 获取结果文件
     * @param fileName 文件名
     * @return 文件对象
     */
    File getResultFile(String fileName);
}
