package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.dto.FieldChange;
import com.sunline.dict.entity.DictChangeLog;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.mapper.DictChangeLogMapper;
import com.sunline.dict.service.DictChangeLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 字典变更日志服务实现
 */
@Service
public class DictChangeLogServiceImpl extends ServiceImpl<DictChangeLogMapper, DictChangeLog> implements DictChangeLogService {
    
    private static final Logger log = LoggerFactory.getLogger(DictChangeLogServiceImpl.class);
    
    @Override
    public void saveChangeLogs(Long versionId, ChangeReport report) {
        List<DictChangeLog> logs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // 1. 记录新增
        for (Object obj : report.getNewList()) {
            if (obj instanceof DictData) {
                DictData data = (DictData) obj;
                DictChangeLog changeLog = new DictChangeLog();
                changeLog.setVersionId(versionId);
                changeLog.setDataItemCode(data.getDataItemCode());
                changeLog.setChangeType("NEW");
                changeLog.setChangeTime(now);
                logs.add(changeLog);
            }
        }
        
        // 2. 记录删除
        for (Object obj : report.getDeleteList()) {
            if (obj instanceof DictData) {
                DictData data = (DictData) obj;
                DictChangeLog changeLog = new DictChangeLog();
                changeLog.setVersionId(versionId);
                changeLog.setDataItemCode(data.getDataItemCode());
                changeLog.setChangeType("DELETE");
                changeLog.setChangeTime(now);
                logs.add(changeLog);
            }
        }
        
        // 3. 记录字段级修改
        for (FieldChange fieldChange : report.getFieldChanges()) {
            DictChangeLog changeLog = new DictChangeLog();
            changeLog.setVersionId(versionId);
            changeLog.setDataItemCode(fieldChange.getDataItemCode());
            changeLog.setChangeType("UPDATE");
            changeLog.setFieldName(fieldChange.getFieldName());
            changeLog.setOldValue(fieldChange.getOldValue());
            changeLog.setNewValue(fieldChange.getNewValue());
            changeLog.setChangeTime(now);
            logs.add(changeLog);
        }
        
        // 批量保存
        if (!logs.isEmpty()) {
            this.saveBatch(logs);
            log.info("保存变更日志: versionId={}, count={}", versionId, logs.size());
        }
    }
    
    @Override
    public List<DictChangeLog> getByVersionId(Long versionId) {
        LambdaQueryWrapper<DictChangeLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DictChangeLog::getVersionId, versionId)
                   .orderByAsc(DictChangeLog::getChangeTime);
        return this.list(queryWrapper);
    }
    
    @Override
    public List<DictChangeLog> getByDataItemCode(String dataItemCode) {
        LambdaQueryWrapper<DictChangeLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DictChangeLog::getDataItemCode, dataItemCode)
                   .orderByDesc(DictChangeLog::getChangeTime);
        return this.list(queryWrapper);
    }
}

