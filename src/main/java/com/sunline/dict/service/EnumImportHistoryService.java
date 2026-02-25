package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.EnumImportHistory;

/**
 * 枚举映射导入历史服务
 */
public interface EnumImportHistoryService extends IService<EnumImportHistory> {
    
    /**
     * 获取下一个版本号
     * @return 版本号（V1, V2, V3...）
     */
    String getNextVersion();
    
    /**
     * 获取最新的导入历史记录
     * @return 最新的导入历史记录
     */
    EnumImportHistory getLatestHistory();
}

