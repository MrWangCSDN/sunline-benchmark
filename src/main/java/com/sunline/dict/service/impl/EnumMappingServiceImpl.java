package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.EnumMapping;
import com.sunline.dict.mapper.EnumMappingMapper;
import com.sunline.dict.service.EnumMappingService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 枚举映射关系Service实现
 */
@Service
public class EnumMappingServiceImpl extends ServiceImpl<EnumMappingMapper, EnumMapping> implements EnumMappingService {
    
    @Override
    public String getDomainEnglishAbbrByChineseName(String domainChineseName) {
        if (domainChineseName == null || domainChineseName.trim().isEmpty()) {
            return null;
        }
        
        QueryWrapper<EnumMapping> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("domain_chinese_name", domainChineseName.trim());
        queryWrapper.isNotNull("domain_english_abbr");
        queryWrapper.last("LIMIT 1");
        
        EnumMapping mapping = this.getOne(queryWrapper);
        return mapping != null ? mapping.getDomainEnglishAbbr() : null;
    }
    
    @Override
    public String getEnumFieldIdByDomainAndCodeValue(String domainChineseName, String codeValue) {
        if (domainChineseName == null || domainChineseName.trim().isEmpty() ||
            codeValue == null || codeValue.trim().isEmpty()) {
            return null;
        }
        
        QueryWrapper<EnumMapping> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("domain_chinese_name", domainChineseName.trim());
        queryWrapper.eq("code_value", codeValue.trim());
        queryWrapper.isNotNull("enum_field_id");
        queryWrapper.last("LIMIT 1");
        
        EnumMapping mapping = this.getOne(queryWrapper);
        return mapping != null ? mapping.getEnumFieldId() : null;
    }
    
    @Override
    public Map<String, String> getAllDomainMappings() {
        Map<String, String> mappings = new HashMap<>();
        
        QueryWrapper<EnumMapping> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNotNull("domain_english_abbr");
        queryWrapper.ne("domain_english_abbr", "");
        
        List<EnumMapping> list = this.list(queryWrapper);
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EnumMappingServiceImpl.class);
        log.debug("getAllDomainMappings: 查询到 {} 条枚举映射记录", list.size());
        
        for (EnumMapping mapping : list) {
            String domainChineseName = mapping.getDomainChineseName();
            String domainEnglishAbbr = mapping.getDomainEnglishAbbr();
            
            if (domainChineseName != null && !domainChineseName.trim().isEmpty() &&
                domainEnglishAbbr != null && !domainEnglishAbbr.trim().isEmpty()) {
                // 如果已存在，保留第一个（模拟LIMIT 1的行为）
                mappings.putIfAbsent(domainChineseName.trim(), domainEnglishAbbr.trim());
                log.debug("添加域映射: {} -> {}", domainChineseName.trim(), domainEnglishAbbr.trim());
            }
        }
        
        log.debug("getAllDomainMappings: 返回 {} 条有效映射", mappings.size());
        return mappings;
    }
    
    @Override
    public Map<String, String> getAllCodeMappings() {
        Map<String, String> mappings = new HashMap<>();
        
        QueryWrapper<EnumMapping> queryWrapper = new QueryWrapper<>();
        queryWrapper.isNotNull("enum_field_id");
        queryWrapper.ne("enum_field_id", "");
        
        List<EnumMapping> list = this.list(queryWrapper);
        for (EnumMapping mapping : list) {
            String domainChineseName = mapping.getDomainChineseName();
            String codeValue = mapping.getCodeValue();
            String enumFieldId = mapping.getEnumFieldId();
            
            if (domainChineseName != null && !domainChineseName.trim().isEmpty() &&
                codeValue != null && !codeValue.trim().isEmpty() &&
                enumFieldId != null && !enumFieldId.trim().isEmpty()) {
                // 组合键：域中文名称|代码取值
                String key = domainChineseName.trim() + "|" + codeValue.trim();
                // 如果已存在，保留第一个（模拟LIMIT 1的行为）
                mappings.putIfAbsent(key, enumFieldId.trim());
            }
        }
        
        return mappings;
    }
}

