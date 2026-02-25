package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.DictVersion;
import com.sunline.dict.mapper.DictVersionMapper;
import com.sunline.dict.service.DictVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 字典版本服务实现
 */
@Service
public class DictVersionServiceImpl extends ServiceImpl<DictVersionMapper, DictVersion> implements DictVersionService {
    
    private static final Logger log = LoggerFactory.getLogger(DictVersionServiceImpl.class);
    
    @Override
    public DictVersion createVersion(MultipartFile file) {
        DictVersion version = new DictVersion();
        
        // 生成版本号：V + 时间戳
        String versionNumber = "V" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        version.setVersionNumber(versionNumber);
        version.setImportTime(LocalDateTime.now());
        version.setFileName(file.getOriginalFilename());
        version.setFileSize(file.getSize());
        
        // 计算文件MD5
        try {
            String fileMd5 = DigestUtils.md5DigestAsHex(file.getInputStream());
            version.setFileMd5(fileMd5);
        } catch (IOException e) {
            log.error("计算文件MD5失败", e);
        }
        
        version.setStatus("PREVIEW"); // 初始状态为预览
        version.setCreateTime(LocalDateTime.now());
        
        // 保存版本记录
        this.save(version);
        
        log.info("创建版本成功: {}", versionNumber);
        return version;
    }
    
    @Override
    public void updateVersionStats(Long versionId, Integer newCount, Integer updateCount, 
                                   Integer deleteCount, Integer unchangedCount) {
        DictVersion version = this.getById(versionId);
        if (version != null) {
            version.setNewCount(newCount);
            version.setUpdateCount(updateCount);
            version.setDeleteCount(deleteCount);
            version.setUnchangedCount(unchangedCount);
            version.setTotalCount(newCount + updateCount + unchangedCount);
            version.setStatus("SUCCESS");
            this.updateById(version);
            
            log.info("更新版本统计: versionId={}, new={}, update={}, delete={}", 
                     versionId, newCount, updateCount, deleteCount);
        }
    }
    
    @Override
    public Page<DictVersion> getVersionHistory(int current, int size) {
        Page<DictVersion> page = new Page<>(current, size);
        LambdaQueryWrapper<DictVersion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(DictVersion::getImportTime);
        return this.page(page, queryWrapper);
    }
    
    @Override
    public DictVersion getByVersionNumber(String versionNumber) {
        LambdaQueryWrapper<DictVersion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DictVersion::getVersionNumber, versionNumber);
        return this.getOne(queryWrapper);
    }
}

