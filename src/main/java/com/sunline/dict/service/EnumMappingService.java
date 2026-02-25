package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.EnumMapping;

import java.util.Map;

/**
 * 枚举映射关系Service
 */
public interface EnumMappingService extends IService<EnumMapping> {
    
    /**
     * 根据域中文名称查询域英文简称
     * @param domainChineseName 域中文名称
     * @return 域英文简称
     */
    String getDomainEnglishAbbrByChineseName(String domainChineseName);
    
    /**
     * 根据域中文名称和代码取值查询枚举字段ID
     * @param domainChineseName 域中文名称
     * @param codeValue 代码取值
     * @return 枚举字段ID
     */
    String getEnumFieldIdByDomainAndCodeValue(String domainChineseName, String codeValue);
    
    /**
     * 获取所有域英文简称映射
     * @return Map<域中文名称, 域英文简称>
     */
    Map<String, String> getAllDomainMappings();
    
    /**
     * 获取所有枚举字段ID映射
     * @return Map<域中文名称|代码取值, 枚举字段ID>
     */
    Map<String, String> getAllCodeMappings();
}

