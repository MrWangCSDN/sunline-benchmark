package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.entity.DictChangeLog;

import java.util.List;

/**
 * 字典变更日志服务接口
 */
public interface DictChangeLogService extends IService<DictChangeLog> {
    
    /**
     * 保存变更日志
     */
    void saveChangeLogs(Long versionId, ChangeReport report);
    
    /**
     * 根据版本ID查询变更日志
     */
    List<DictChangeLog> getByVersionId(Long versionId);
    
    /**
     * 根据数据项编号查询变更历史
     */
    List<DictChangeLog> getByDataItemCode(String dataItemCode);
}

