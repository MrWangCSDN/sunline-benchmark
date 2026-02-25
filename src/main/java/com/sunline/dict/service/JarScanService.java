package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Jar包扫描服务接口
 */
public interface JarScanService {
    
    /**
     * 扫描指定jar包中的flowtrans.xml文件（异步执行）
     * @param jarPath jar包路径
     * @param jarNames 要扫描的jar包名称列表（忽略版本号）
     * @param transactionIds 要扫描的交易ID列表（为null或空表示扫描全部）
     */
    void scanJar(String jarPath, List<String> jarNames, List<String> transactionIds);
    
    /**
     * 从上传的文件扫描jar包（异步执行）
     * @param jarFile 上传的jar包文件
     * @param jarNames 要扫描的jar包名称列表（忽略版本号）
     * @param transactionIds 要扫描的交易ID列表（为null或空表示扫描全部）
     */
    void scanJarFromUpload(MultipartFile jarFile, List<String> jarNames, List<String> transactionIds);
    
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

