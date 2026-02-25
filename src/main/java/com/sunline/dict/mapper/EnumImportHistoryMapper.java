package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.EnumImportHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 枚举映射导入历史Mapper
 */
@Mapper
public interface EnumImportHistoryMapper extends BaseMapper<EnumImportHistory> {
    
    /**
     * 获取最新版本号
     */
    @Select("SELECT version FROM enum_import_history ORDER BY id DESC LIMIT 1")
    String getLatestVersion();
}

