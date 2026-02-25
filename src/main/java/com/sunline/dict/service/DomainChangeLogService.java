package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.entity.DomainChangeLog;

import java.util.List;

/**
 * 域清单变更日志服务接口
 */
public interface DomainChangeLogService extends IService<DomainChangeLog> {
    
    /**
     * 保存变更日志
     */
    void saveChangeLogs(Long versionId, ChangeReport report);
    
    /**
     * 根据版本ID查询变更日志
     */
    List<DomainChangeLog> getByVersionId(Long versionId);
}

