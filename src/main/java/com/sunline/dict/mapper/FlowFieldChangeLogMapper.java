package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.FlowFieldChangeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * flow_field_change_log Mapper
 */
@Mapper
public interface FlowFieldChangeLogMapper extends BaseMapper<FlowFieldChangeLog> {

    @Select("SELECT * FROM flow_field_change_log WHERE dedup_key = #{dedupKey} LIMIT 1")
    FlowFieldChangeLog selectByDedupKey(String dedupKey);

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
