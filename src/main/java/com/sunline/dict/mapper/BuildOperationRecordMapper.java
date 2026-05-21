package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.BuildOperationRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全量拉取+编译操作记录 Mapper
 */
@Mapper
public interface BuildOperationRecordMapper extends BaseMapper<BuildOperationRecord> {
}
