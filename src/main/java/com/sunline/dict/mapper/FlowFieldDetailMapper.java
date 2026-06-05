package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.FlowFieldDetail;
import org.apache.ibatis.annotations.Mapper;

/**
 * flow_field_detail Mapper
 * <p>
 * 与项目 sibling（如 FlowStepMapper）保持一致：仅继承 BaseMapper，所有自定义查询
 * 一律走 MyBatis-Plus 的 QueryWrapper，避免依赖自定义 mapper XML（项目里其他
 * mapper 都不写 XML，统一惯例）。
 */
@Mapper
public interface FlowFieldDetailMapper extends BaseMapper<FlowFieldDetail> {
}
