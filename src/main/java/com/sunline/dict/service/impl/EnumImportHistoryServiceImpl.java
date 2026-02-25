package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.EnumImportHistory;
import com.sunline.dict.mapper.EnumImportHistoryMapper;
import com.sunline.dict.service.EnumImportHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 枚举映射导入历史服务实现
 */
@Service
public class EnumImportHistoryServiceImpl extends ServiceImpl<EnumImportHistoryMapper, EnumImportHistory> 
        implements EnumImportHistoryService {
    
    private static final Logger log = LoggerFactory.getLogger(EnumImportHistoryServiceImpl.class);
    
    @Override
    public String getNextVersion() {
        // 查询最新版本号
        QueryWrapper<EnumImportHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");
        queryWrapper.last("LIMIT 1");
        EnumImportHistory latest = this.getOne(queryWrapper);
        
        if (latest == null || latest.getVersion() == null) {
            // 如果没有历史记录，返回V1
            return "V1";
        }
        
        // 解析版本号，递增
        String version = latest.getVersion();
        try {
            if (version.startsWith("V")) {
                int versionNum = Integer.parseInt(version.substring(1));
                return "V" + (versionNum + 1);
            } else {
                // 如果格式不对，从V1开始
                log.warn("版本号格式异常: {}, 从V1开始", version);
                return "V1";
            }
        } catch (NumberFormatException e) {
            log.warn("版本号解析失败: {}, 从V1开始", version, e);
            return "V1";
        }
    }
    
    @Override
    public EnumImportHistory getLatestHistory() {
        QueryWrapper<EnumImportHistory> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");
        queryWrapper.last("LIMIT 1");
        return this.getOne(queryWrapper);
    }
}

