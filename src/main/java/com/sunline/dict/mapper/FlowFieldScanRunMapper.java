package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.FlowFieldScanRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface FlowFieldScanRunMapper extends BaseMapper<FlowFieldScanRun> {

    @Select("""
            SELECT MIN(window_start)
              FROM flow_field_scan_run
             WHERE project_id = #{projectId}
               AND branch = #{branch}
               AND status = 'FAILED'
               AND cursor_advanced = 0
            """)
    LocalDateTime selectEarliestUnadvancedWindowStart(@Param("projectId") long projectId,
                                                       @Param("branch") String branch);

    @Update("""
            UPDATE flow_field_scan_run
               SET status = 'FAILED',
                   error_message = '扫描窗口超时，已由后续任务终止',
                   finished_at = #{finishedAt},
                   update_time = #{finishedAt}
             WHERE project_id = #{projectId}
               AND branch = #{branch}
               AND status = 'RUNNING'
               AND window_end < #{olderWindowEnd}
            """)
    int failStaleRuns(@Param("projectId") long projectId,
                      @Param("branch") String branch,
                      @Param("olderWindowEnd") LocalDateTime olderWindowEnd,
                      @Param("finishedAt") LocalDateTime finishedAt);

    @Update("""
            UPDATE flow_field_scan_run
               SET project_name = #{projectName},
                   project_path = #{projectPath},
                   status = #{status},
                   commit_count = #{commitCount},
                   changed_file_count = #{changedFileCount},
                   history_count = #{historyCount},
                   failed_file_count = #{failedFileCount},
                   skipped_count = #{skippedCount},
                   cursor_advanced = #{cursorAdvanced},
                   error_message = #{errorMessage},
                   finished_at = #{finishedAt},
                   update_time = #{updateTime}
             WHERE id = #{id}
               AND status = 'RUNNING'
            """)
    int finishRunning(FlowFieldScanRun run);
}
