package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 方法栈扫描服务接口
 */
public interface MethodStackScanService {
    
    /**
     * 扫描方法调用栈
     * @param jarPath jar包路径（可以是目录或Fat Jar文件）
     * @param jarNamePrefixes jar包名称前缀列表（如sett-、loan-、dept-、comm-）
     */
    void scanMethodStack(String jarPath, List<String> jarNamePrefixes);
    
    /**
     * 从上传的文件扫描方法调用栈
     * @param jarFile 上传的jar包文件
     * @param jarNamePrefixes jar包名称前缀列表（如sett-、loan-、dept-、comm-）
     */
    void scanMethodStackFromUpload(MultipartFile jarFile, List<String> jarNamePrefixes);
    
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

