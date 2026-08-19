package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.FlowFieldChangeDetail;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * flow_field_change_detail Mapper
 */
@Mapper
public interface FlowFieldChangeDetailMapper extends BaseMapper<FlowFieldChangeDetail> {

    @Delete("DELETE FROM flow_field_change_detail WHERE log_id = #{logId}")
    int deleteByLogId(long logId);

    @Select("SELECT * FROM flow_field_change_detail WHERE log_id = #{logId} ORDER BY id ASC")
    List<FlowFieldChangeDetail> selectByLogId(long logId);
}
