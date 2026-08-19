package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.FlowFieldScanRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface FlowFieldScanRunMapper extends BaseMapper<FlowFieldScanRun> {

    @Update("""
            UPDATE flow_field_scan_run
               SET status = 'FAILED',
                   error_message = '扫描窗口超时，已由后续任务终止',
                   finished_at = #{finishedAt}
             WHERE project_id = #{projectId}
               AND branch = #{branch}
               AND status = 'RUNNING'
               AND window_end < #{olderWindowEnd}
            """)
    int failStaleRuns(@Param("projectId") long projectId,
                      @Param("branch") String branch,
                      @Param("olderWindowEnd") LocalDateTime olderWindowEnd,
                      @Param("finishedAt") LocalDateTime finishedAt);
}
