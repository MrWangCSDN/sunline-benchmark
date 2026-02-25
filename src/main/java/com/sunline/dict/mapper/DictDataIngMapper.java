package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.DictDataIng;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 字典数据Mapper接口（在途）
 */
@Mapper
public interface DictDataIngMapper extends BaseMapper<DictDataIng> {
    
    /**
     * 根据JAVA/ESF规范命名查询数据
     */
    @Select("SELECT * FROM dict_data_ing WHERE java_esf_name = #{javaEsfName} LIMIT 1")
    DictDataIng selectByJavaEsfName(String javaEsfName);
}

