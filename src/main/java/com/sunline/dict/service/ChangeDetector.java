package com.sunline.dict.service;

import com.sunline.dict.dto.ChangeReport;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.util.DataHashUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 变更检测服务
 */
@Service
public class ChangeDetector {
    
    /**
     * 检测数据变更
     * 
     * @param newDataList 新Excel数据
     * @param oldDataList 数据库现有数据
     * @return 变更报告
     */
    public ChangeReport detectChanges(List<DictData> newDataList, List<DictData> oldDataList) {
        ChangeReport report = new ChangeReport();
        
        // 1. 构建旧数据Map（以数据项编号为Key）
        Map<String, DictData> oldDataMap = oldDataList.stream()
            .filter(data -> data.getDataItemCode() != null)
            .collect(Collectors.toMap(
                DictData::getDataItemCode, 
                Function.identity(),
                (existing, replacement) -> existing // 如果有重复key，保留第一个
            ));
        
        // 2. 构建新数据Map
        Map<String, DictData> newDataMap = newDataList.stream()
            .filter(data -> data.getDataItemCode() != null)
            .collect(Collectors.toMap(
                DictData::getDataItemCode, 
                Function.identity(),
                (existing, replacement) -> existing
            ));
        
        // 3. 检测新增和修改
        for (DictData newData : newDataList) {
            String code = newData.getDataItemCode();
            if (code == null || code.trim().isEmpty()) {
                continue;
            }
            
            DictData oldData = oldDataMap.get(code);
            
            if (oldData == null) {
                // 新增
                report.addNew(newData);
            } else {
                // 对比Hash判断是否修改
                String newHash = DataHashUtils.calculateHash(newData);
                String oldHash = DataHashUtils.calculateHash(oldData);
                
                if (!newHash.equals(oldHash)) {
                    // 修改
                    newData.setId(oldData.getId()); // 保留原ID
                    report.addUpdate(newData, oldData);
                } else {
                    // 未变化
                    newData.setId(oldData.getId()); // 保留原ID
                    report.addUnchanged(newData);
                }
            }
        }
        
        // 4. 检测删除（在旧数据中但不在新数据中）
        for (DictData oldData : oldDataList) {
            String code = oldData.getDataItemCode();
            if (code != null && !code.trim().isEmpty() && !newDataMap.containsKey(code)) {
                report.addDelete(oldData);
            }
        }
        
        // 5. 生成摘要
        report.generateSummary();
        
        return report;
    }
}

