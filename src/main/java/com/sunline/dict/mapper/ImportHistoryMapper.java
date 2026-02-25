package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.ImportHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 导入历史Mapper
 */
@Mapper
public interface ImportHistoryMapper extends BaseMapper<ImportHistory> {
}

