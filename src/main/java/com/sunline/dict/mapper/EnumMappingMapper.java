package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.EnumMapping;
import org.apache.ibatis.annotations.Mapper;

/**
 * 枚举映射关系Mapper
 */
@Mapper
public interface EnumMappingMapper extends BaseMapper<EnumMapping> {
}

