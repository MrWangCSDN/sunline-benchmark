package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.FlowFieldDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * flow_field_detail Mapper
 */
@Mapper
public interface FlowFieldDetailMapper extends BaseMapper<FlowFieldDetail> {

    /** 按 flow_id 删除该交易的所有字段记录（用于先删后插的幂等策略） */
    int deleteByFlowId(@Param("flowId") String flowId);

    /** 按 flow_id + io_type 查字段清单（用于 by-flow 查询） */
    List<FlowFieldDetail> selectByFlowIdAndIoType(@Param("flowId") String flowId,
                                                   @Param("ioType") String ioType);
}
