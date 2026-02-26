package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.Dict;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 Mapper
 */
@Mapper
public interface DictMapper extends BaseMapper<Dict> {
}
