package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.DomainMapping;
import com.sunline.dict.mapper.DomainMappingMapper;
import com.sunline.dict.service.DomainMappingService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 域清单映射关系Service实现
 */
@Service
public class DomainMappingServiceImpl extends ServiceImpl<DomainMappingMapper, DomainMapping> implements DomainMappingService {
    
    @Override
    public String getDomainEnglishAbbrByChineseName(String domainChineseName) {
        if (domainChineseName == null || domainChineseName.trim().isEmpty()) {
            return null;
        }
        
        QueryWrapper<DomainMapping> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("domain_chinese_name", domainChineseName.trim());
        queryWrapper.isNotNull("domain_english_abbr");
        queryWrapper.last("LIMIT 1");
        
        DomainMapping mapping = this.getOne(queryWrapper);
        return mapping != null ? mapping.getDomainEnglishAbbr() : null;
    }
    
    @Override
    public Map<String, String> getAllDomainMappings() {
        List<DomainMapping> allMappings = this.list();
        return allMappings.stream()
                .filter(mapping -> mapping.getDomainChineseName() != null 
                        && !mapping.getDomainChineseName().trim().isEmpty()
                        && mapping.getDomainEnglishAbbr() != null 
                        && !mapping.getDomainEnglishAbbr().trim().isEmpty())
                .collect(Collectors.toMap(
                        mapping -> mapping.getDomainChineseName().trim(),
                        mapping -> mapping.getDomainEnglishAbbr().trim(),
                        (existing, replacement) -> existing, // 如果有重复key，保留第一个
                        HashMap::new
                ));
    }
}

