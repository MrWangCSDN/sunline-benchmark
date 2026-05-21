package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.CallRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CallRelationMapper extends BaseMapper<CallRelation> {

    /**
     * 向上查询（影响面）：谁调了我？
     */
    @Select("SELECT * FROM call_relation WHERE callee_id = #{calleeId} AND callee_type = #{calleeType}")
    List<CallRelation> findCallers(@Param("calleeId") String calleeId, @Param("calleeType") String calleeType);

    /**
     * 向上查询（影响面）精确到方法级
     */
    @Select("SELECT * FROM call_relation WHERE callee_id = #{calleeId} AND callee_type = #{calleeType} AND callee_method = #{calleeMethod}")
    List<CallRelation> findCallersByMethod(@Param("calleeId") String calleeId, @Param("calleeType") String calleeType, @Param("calleeMethod") String calleeMethod);

    /**
     * 向下查询（依赖链）：我调了谁？
     */
    @Select("SELECT * FROM call_relation WHERE caller_id = #{callerId} AND caller_type = #{callerType}")
    List<CallRelation> findCallees(@Param("callerId") String callerId, @Param("callerType") String callerType);

    /**
     * 向下查询精确到方法级
     */
    @Select("SELECT * FROM call_relation WHERE caller_id = #{callerId} AND caller_type = #{callerType} AND caller_method = #{callerMethod}")
    List<CallRelation> findCalleesByMethod(@Param("callerId") String callerId, @Param("callerType") String callerType, @Param("callerMethod") String callerMethod);

    /**
     * 查询所有违规调用
     */
    @Select("SELECT * FROM call_relation WHERE rule_violation = 1 ORDER BY caller_type, caller_id")
    List<CallRelation> findViolations();

    /**
     * 按 caller 删除（增量更新用）
     */
    @Select("DELETE FROM call_relation WHERE caller_id = #{callerId} AND caller_type = #{callerType}")
    int deleteByCallerId(@Param("callerId") String callerId, @Param("callerType") String callerType);

    /**
     * 统计各类型节点数量
     */
    @Select("SELECT callee_type as type, COUNT(DISTINCT callee_id) as cnt FROM call_relation GROUP BY callee_type " +
            "UNION ALL SELECT caller_type as type, COUNT(DISTINCT caller_id) as cnt FROM call_relation GROUP BY caller_type")
    List<Map<String, Object>> countByType();
}
