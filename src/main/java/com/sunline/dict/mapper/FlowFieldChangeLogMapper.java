package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.FlowFieldChangeLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * flow_field_change_log Mapper
 */
@Mapper
public interface FlowFieldChangeLogMapper extends BaseMapper<FlowFieldChangeLog> {

    @Insert("""
            INSERT INTO flow_field_change_log (
                dedup_key, scan_run_id, change_date,
                project_id, project_name, project_path, branch, file_path,
                file_change_type, capture_status, error_message,
                parent_sha, commit_sha, commit_message, commit_author, commit_email, commit_time,
                add_count, modify_count, remove_count, input_change_count, output_change_count
            ) VALUES (
                #{dedupKey}, #{scanRunId}, #{changeDate},
                #{projectId}, #{projectName}, #{projectPath}, #{branch}, #{filePath},
                #{fileChangeType}, #{captureStatus}, #{errorMessage},
                #{parentSha}, #{commitSha}, #{commitMessage}, #{commitAuthor}, #{commitEmail},
                #{commitTime}, #{addCount}, #{modifyCount}, #{removeCount},
                #{inputChangeCount}, #{outputChangeCount}
            )
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """)
    int insertDailyPlaceholder(FlowFieldChangeLog row);

    @Select("SELECT * FROM flow_field_change_log WHERE dedup_key = #{dedupKey} LIMIT 1")
    FlowFieldChangeLog selectByDedupKey(String dedupKey);

    @Select("SELECT * FROM flow_field_change_log WHERE dedup_key = #{dedupKey} FOR UPDATE")
    FlowFieldChangeLog selectByDedupKeyForUpdate(String dedupKey);

    @Update("""
            UPDATE flow_field_change_log
               SET scan_run_id = #{scanRunId},
                   change_date = #{changeDate},
                   project_id = #{projectId},
                   project_name = #{projectName},
                   project_path = #{projectPath},
                   branch = #{branch},
                   file_path = #{filePath},
                   flow_id = #{flowId},
                   flow_longname = #{flowLongname},
                   file_change_type = #{fileChangeType},
                   capture_status = #{captureStatus},
                   error_message = #{errorMessage},
                   parent_sha = #{parentSha},
                   commit_sha = #{commitSha},
                   commit_message = #{commitMessage},
                   commit_author = #{commitAuthor},
                   commit_email = #{commitEmail},
                   commit_time = #{commitTime},
                   add_count = #{addCount},
                   modify_count = #{modifyCount},
                   remove_count = #{removeCount},
                   input_change_count = #{inputChangeCount},
                   output_change_count = #{outputChangeCount},
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{id}
            """)
    int updateDaily(FlowFieldChangeLog row);
}
