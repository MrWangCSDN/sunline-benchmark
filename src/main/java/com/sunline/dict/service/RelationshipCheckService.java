package com.sunline.dict.service;

/**
 * 关联性检查服务
 */
public interface RelationshipCheckService {
    
    /**
     * 执行关联性检查
     * @return 如果检查通过返回null，如果有问题返回Excel文件的字节数组
     */
    byte[] performCheck() throws Exception;
}

