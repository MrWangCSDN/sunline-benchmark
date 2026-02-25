package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.DictData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典数据Mapper接口
 */
@Mapper
public interface DictDataMapper extends BaseMapper<DictData> {
}

