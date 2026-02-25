package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.DictVersion;
import org.springframework.web.multipart.MultipartFile;

/**
 * 字典版本服务接口
 */
public interface DictVersionService extends IService<DictVersion> {
    
    /**
     * 创建新版本
     */
    DictVersion createVersion(MultipartFile file);
    
    /**
     * 更新版本统计信息
     */
    void updateVersionStats(Long versionId, Integer newCount, Integer updateCount, 
                           Integer deleteCount, Integer unchangedCount);
    
    /**
     * 分页查询版本历史
     */
    Page<DictVersion> getVersionHistory(int current, int size);
    
    /**
     * 根据版本号查询
     */
    DictVersion getByVersionNumber(String versionNumber);
}

