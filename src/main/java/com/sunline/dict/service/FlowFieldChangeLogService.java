package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeQuery;
import com.sunline.dict.entity.FlowFieldChangeLog;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet;

public interface FlowFieldChangeLogService {
    boolean existsByDedupKey(String dedupKey);
    FlowFieldChangeLog recordSuccess(FlowFieldChangeCaptureMeta meta, FlowFieldChangeSet changeSet);
    FlowFieldChangeLog recordFailure(FlowFieldChangeCaptureMeta meta, String errorMessage);
    Page<FlowFieldChangeLog> pageLogs(FlowFieldChangeQuery query);
    FlowFieldChangeHistoryDetail getDetail(long logId);
}
