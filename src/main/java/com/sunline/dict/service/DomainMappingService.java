package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.DomainMapping;

import java.util.Map;

/**
 * 域清单映射关系Service
 */
public interface DomainMappingService extends IService<DomainMapping> {
    
    /**
     * 根据域中文名称查询域英文简称
     * @param domainChineseName 域中文名称
     * @return 域英文简称
     */
    String getDomainEnglishAbbrByChineseName(String domainChineseName);
    
    /**
     * 获取所有域清单映射关系（用于性能优化，避免N+1查询）
     * @return Map<域中文名称, 域英文简称>
     */
    Map<String, String> getAllDomainMappings();
}

