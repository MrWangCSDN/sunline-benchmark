package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.dto.FieldChange;
import com.sunline.dict.entity.DomainChangeLog;
import com.sunline.dict.entity.DomainData;
import com.sunline.dict.mapper.DomainChangeLogMapper;
import com.sunline.dict.service.DomainChangeLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 域清单变更日志服务实现
 */
@Service
public class DomainChangeLogServiceImpl extends ServiceImpl<DomainChangeLogMapper, DomainChangeLog> implements DomainChangeLogService {
    
    private static final Logger log = LoggerFactory.getLogger(DomainChangeLogServiceImpl.class);
    
    @Override
    public void saveChangeLogs(Long versionId, ChangeReport report) {
        List<DomainChangeLog> logs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        log.info("开始保存域清单变更日志: versionId={}", versionId);
        log.info("新增数量: {}, 修改数量: {}, 删除数量: {}, 字段变更数量: {}", 
            report.getNewList().size(), 
            report.getUpdateList().size(), 
            report.getDeleteList().size(),
            report.getFieldChanges().size());
        
        // 1. 记录新增
        for (Object obj : report.getNewList()) {
            if (obj instanceof DomainData) {
                DomainData data = (DomainData) obj;
                DomainChangeLog changeLog = new DomainChangeLog();
                changeLog.setVersionId(versionId);
                changeLog.setChineseName(data.getChineseName());
                changeLog.setChangeType("NEW");
                changeLog.setChangeTime(now);
                logs.add(changeLog);
            }
        }
        
        // 2. 记录删除
        for (Object obj : report.getDeleteList()) {
            if (obj instanceof DomainData) {
                DomainData data = (DomainData) obj;
                DomainChangeLog changeLog = new DomainChangeLog();
                changeLog.setVersionId(versionId);
                changeLog.setChineseName(data.getChineseName());
                changeLog.setChangeType("DELETE");
                changeLog.setChangeTime(now);
                logs.add(changeLog);
            }
        }
        
        // 3. 记录字段级修改
        log.info("开始处理字段变更，总数: {}", report.getFieldChanges().size());
        int domainFieldChangeCount = 0;
        for (FieldChange fieldChange : report.getFieldChanges()) {
            // 判断是否是域清单的字段变更（通过dataItemCode判断，域清单的dataItemCode是域中文名称，不包含"|"）
            String dataItemCode = fieldChange.getDataItemCode();
            
            // 跳过字典的字段变更（字典的dataItemCode格式是EDD-xxx）
            if (dataItemCode != null && dataItemCode.startsWith("EDD-")) {
                continue;
            }
            
            // 跳过代码扩展清单的字段变更（代码扩展的dataItemCode格式是"xxx|yyy"）
            if (dataItemCode != null && dataItemCode.contains("|")) {
                continue;
            }
            
            // 这是域清单的字段变更
            domainFieldChangeCount++;
            DomainChangeLog changeLog = new DomainChangeLog();
            changeLog.setVersionId(versionId);
            changeLog.setChineseName(dataItemCode); // dataItemCode存储的是域中文名称
            changeLog.setChangeType("UPDATE");
            changeLog.setFieldName(fieldChange.getFieldName());
            changeLog.setOldValue(fieldChange.getOldValue());
            changeLog.setNewValue(fieldChange.getNewValue());
            changeLog.setChangeTime(now);
            logs.add(changeLog);
            
            log.debug("域清单字段变更: chineseName={}, field={}, old={}, new={}", 
                dataItemCode, fieldChange.getFieldName(), fieldChange.getOldValue(), fieldChange.getNewValue());
        }
        
        log.info("域清单字段变更数量: {}", domainFieldChangeCount);
        
        // 批量保存
        if (!logs.isEmpty()) {
            this.saveBatch(logs);
            log.info("保存域清单变更日志完成: versionId={}, 总记录数={} (新增={}, 删除={}, 字段变更={})", 
                versionId, logs.size(), report.getNewList().size(), report.getDeleteList().size(), domainFieldChangeCount);
        } else {
            log.warn("没有域清单变更日志需要保存");
        }
    }
    
    @Override
    public List<DomainChangeLog> getByVersionId(Long versionId) {
        QueryWrapper<DomainChangeLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("version_id", versionId);
        queryWrapper.orderByAsc("id");
        return this.list(queryWrapper);
    }
}

