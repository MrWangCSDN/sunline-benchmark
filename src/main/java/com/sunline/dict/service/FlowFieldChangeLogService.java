package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeQuery;
import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.entity.FlowFieldDetail;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet;

import java.time.LocalDateTime;
import java.util.List;

public interface FlowFieldChangeLogService {
    boolean existsByDedupKey(String dedupKey);
    FlowFieldChangeLog recordSuccess(FlowFieldChangeCaptureMeta meta, FlowFieldChangeSet changeSet);
    FlowFieldChangeLog recordFailure(FlowFieldChangeCaptureMeta meta, String errorMessage);
    Page<FlowFieldChangeLog> pageLogs(FlowFieldChangeQuery query);
    FlowFieldChangeHistoryDetail getDetail(long logId);

    /** Temporary source bridge until the capture-pipeline task replaces its prototype call sites. */
    @Deprecated
    default FlowFieldChangeLog recordChange(ChangeMeta meta, List<FlowFieldDetail> before,
                                             List<FlowFieldDetail> after) {
        throw new UnsupportedOperationException("legacy flow change capture is not supported");
    }

    @Deprecated
    class ChangeMeta {
        public String projectName;
        public String branch;
        public String filePath;
        public String commitSha;
        public String commitMessage;
        public String commitAuthor;
        public String commitEmail;
        public LocalDateTime commitTime;
    }
}
