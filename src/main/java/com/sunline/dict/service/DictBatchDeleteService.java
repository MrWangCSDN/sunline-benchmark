package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 在途字典批量删除服务接口
 */
public interface DictBatchDeleteService {
    
    /**
     * 批量删除在途字典数据
     * @param dictFile 字典Excel文件
     * @return 删除结果，包含总数、已删除数、未匹配数和未匹配列表
     */
    Map<String, Object> batchDelete(MultipartFile dictFile) throws Exception;
}

