package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.dto.FieldChange;
import com.sunline.dict.entity.CodeExtensionChangeLog;
import com.sunline.dict.entity.CodeExtensionData;
import com.sunline.dict.mapper.CodeExtensionChangeLogMapper;
import com.sunline.dict.service.CodeExtensionChangeLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 代码扩展清单变更日志服务实现
 */
@Service
public class CodeExtensionChangeLogServiceImpl extends ServiceImpl<CodeExtensionChangeLogMapper, CodeExtensionChangeLog> implements CodeExtensionChangeLogService {
    
    private static final Logger log = LoggerFactory.getLogger(CodeExtensionChangeLogServiceImpl.class);
    
    @Override
    public void saveChangeLogs(Long versionId, ChangeReport report) {
        List<CodeExtensionChangeLog> logs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        log.info("开始保存代码扩展清单变更日志: versionId={}", versionId);
        log.info("新增数量: {}, 修改数量: {}, 删除数量: {}, 字段变更数量: {}", 
            report.getNewList().size(), 
            report.getUpdateList().size(), 
            report.getDeleteList().size(),
            report.getFieldChanges().size());
        
        // 1. 记录新增
        for (Object obj : report.getNewList()) {
            if (obj instanceof CodeExtensionData) {
                CodeExtensionData data = (CodeExtensionData) obj;
                CodeExtensionChangeLog changeLog = new CodeExtensionChangeLog();
                changeLog.setVersionId(versionId);
                changeLog.setCodeDomainChineseName(data.getCodeDomainChineseName());
                changeLog.setCodeValue(data.getCodeValue());
                changeLog.setChangeType("NEW");
                changeLog.setChangeTime(now);
                logs.add(changeLog);
            }
        }
        
        // 2. 记录删除
        for (Object obj : report.getDeleteList()) {
            if (obj instanceof CodeExtensionData) {
                CodeExtensionData data = (CodeExtensionData) obj;
                CodeExtensionChangeLog changeLog = new CodeExtensionChangeLog();
                changeLog.setVersionId(versionId);
                changeLog.setCodeDomainChineseName(data.getCodeDomainChineseName());
                changeLog.setCodeValue(data.getCodeValue());
                changeLog.setChangeType("DELETE");
                changeLog.setChangeTime(now);
                logs.add(changeLog);
            }
        }
        
        // 3. 记录字段级修改
        log.info("开始处理字段变更，总数: {}", report.getFieldChanges().size());
        int codeFieldChangeCount = 0;
        for (FieldChange fieldChange : report.getFieldChanges()) {
            String dataItemCode = fieldChange.getDataItemCode();
            
            // 只处理代码扩展清单的字段变更（格式：代码域中文名称|代码取值）
            if (dataItemCode == null || !dataItemCode.contains("|")) {
                continue;
            }
            
            // 从dataItemCode解析出代码域中文名称和代码取值
            String[] parts = dataItemCode.split("\\|");
            String codeDomainChineseName = parts.length > 0 ? parts[0] : "";
            String codeValue = parts.length > 1 ? parts[1] : "";
            
            codeFieldChangeCount++;
            CodeExtensionChangeLog changeLog = new CodeExtensionChangeLog();
            changeLog.setVersionId(versionId);
            changeLog.setCodeDomainChineseName(codeDomainChineseName);
            changeLog.setCodeValue(codeValue);
            changeLog.setChangeType("UPDATE");
            changeLog.setFieldName(fieldChange.getFieldName());
            changeLog.setOldValue(fieldChange.getOldValue());
            changeLog.setNewValue(fieldChange.getNewValue());
            changeLog.setChangeTime(now);
            logs.add(changeLog);
            
            log.debug("代码扩展字段变更: key={}, field={}, old={}, new={}", 
                dataItemCode, fieldChange.getFieldName(), fieldChange.getOldValue(), fieldChange.getNewValue());
        }
        
        log.info("代码扩展清单字段变更数量: {}", codeFieldChangeCount);
        
        // 批量保存
        if (!logs.isEmpty()) {
            this.saveBatch(logs);
            log.info("保存代码扩展清单变更日志完成: versionId={}, 总记录数={} (新增={}, 删除={}, 字段变更={})", 
                versionId, logs.size(), report.getNewList().size(), report.getDeleteList().size(), codeFieldChangeCount);
        } else {
            log.warn("没有代码扩展清单变更日志需要保存");
        }
    }
    
    @Override
    public List<CodeExtensionChangeLog> getByVersionId(Long versionId) {
        QueryWrapper<CodeExtensionChangeLog> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("version_id", versionId);
        queryWrapper.orderByAsc("id");
        return this.list(queryWrapper);
    }
}

