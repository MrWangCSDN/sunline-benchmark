package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.entity.CodeExtensionChangeLog;

import java.util.List;

/**
 * 代码扩展清单变更日志服务接口
 */
public interface CodeExtensionChangeLogService extends IService<CodeExtensionChangeLog> {
    
    /**
     * 保存变更日志
     */
    void saveChangeLogs(Long versionId, ChangeReport report);
    
    /**
     * 根据版本ID查询变更日志
     */
    List<CodeExtensionChangeLog> getByVersionId(Long versionId);
}

