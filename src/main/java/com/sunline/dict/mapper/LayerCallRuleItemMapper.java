package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.LayerCallRuleItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分层调用规则项Mapper
 */
@Mapper
public interface LayerCallRuleItemMapper extends BaseMapper<LayerCallRuleItem> {
    
    /**
     * 根据规则ID查询规则项
     */
    @Select("SELECT * FROM layer_call_rule_item WHERE rule_id = #{ruleId} ORDER BY item_order")
    List<LayerCallRuleItem> selectByRuleId(Long ruleId);
    
    /**
     * 根据规则ID删除规则项
     */
    @Delete("DELETE FROM layer_call_rule_item WHERE rule_id = #{ruleId}")
    int deleteByRuleId(Long ruleId);
}

