package com.sunline.dict.mapper;

import com.sunline.dict.entity.FlowFieldScanCursor;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FlowFieldScanCursorMapper {

    @Select("""
            SELECT project_id, branch, project_name, project_path,
                   last_success_end, last_run_id, update_time
              FROM flow_field_scan_cursor
             WHERE project_id = #{projectId} AND branch = #{branch}
            """)
    FlowFieldScanCursor selectCursor(@Param("projectId") long projectId,
                                     @Param("branch") String branch);

    @Insert("""
            INSERT INTO flow_field_scan_cursor
                (project_id, branch, project_name, project_path, last_success_end, last_run_id)
            VALUES
                (#{projectId}, #{branch}, #{projectName}, #{projectPath}, #{lastSuccessEnd}, #{lastRunId})
            ON DUPLICATE KEY UPDATE
                project_name = VALUES(project_name),
                project_path = VALUES(project_path),
                last_success_end = VALUES(last_success_end),
                last_run_id = VALUES(last_run_id)
            """)
    int upsertCursor(FlowFieldScanCursor cursor);
}
