package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.Complex;
import org.apache.ibatis.annotations.Mapper;

/**
 * 复合类型 Mapper
 */
@Mapper
public interface ComplexMapper extends BaseMapper<Complex> {
}
