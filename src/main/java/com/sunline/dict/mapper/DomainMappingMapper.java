package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.DomainMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 域清单映射关系Mapper
 */
@Mapper
public interface DomainMappingMapper extends BaseMapper<DomainMapping> {
}

