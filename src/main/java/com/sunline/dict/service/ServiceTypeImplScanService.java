package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * ServiceType实现扫描服务接口
 */
public interface ServiceTypeImplScanService {
    
    /**
     * 扫描ServiceType实现文件
     * @param jarPath jar包路径（可以是目录或Fat Jar文件）
     * @param jarNames 要扫描的jar包名称列表（忽略版本号）
     */
    void scanServiceTypeImplFiles(String jarPath, List<String> jarNames);
    
    /**
     * 从上传的文件扫描ServiceType实现文件
     * @param jarFile 上传的jar包文件
     * @param jarNames 要扫描的jar包名称列表（忽略版本号）
     */
    void scanServiceTypeImplFilesFromUpload(MultipartFile jarFile, List<String> jarNames);
    
    /**
     * 获取扫描进度
     * @return 扫描进度信息
     */
    Map<String, Object> getScanProgress();
    
    /**
     * 取消扫描
     */
    void cancelScan();
}

