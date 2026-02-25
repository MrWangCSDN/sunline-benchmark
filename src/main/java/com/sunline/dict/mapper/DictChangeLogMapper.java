package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.DictChangeLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典变更日志Mapper
 */
@Mapper
public interface DictChangeLogMapper extends BaseMapper<DictChangeLog> {
}

