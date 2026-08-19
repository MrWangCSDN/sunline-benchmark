package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.FieldChangeRowView;
import com.sunline.dict.dto.FlowFieldChangeDtos.FlowFieldChangeHistoryDetail;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunQuery;
import com.sunline.dict.dto.FlowFieldChangeDtos.ScanRunView;
import com.sunline.dict.service.flowchange.FlowFieldChangeCaptureMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeMeta;
import com.sunline.dict.service.flowchange.FlowFieldChangeSet;

public interface FlowFieldChangeLogService {

    HistoryState state(String dedupKey);

    WriteOutcome recordSuccess(FlowFieldChangeMeta meta, FlowFieldChangeSet changeSet);

    WriteOutcome recordFailure(FlowFieldChangeMeta meta, String safeError);

    Page<FieldChangeRowView> pageFieldChanges(FieldChangeQuery query);

    FlowFieldChangeHistoryDetail getDetail(long logId);

    Page<ScanRunView> pageScanRuns(ScanRunQuery query);

    /** Temporary Task 4 bridge; removed with Webhook capture in Task 6. */
    @Deprecated(forRemoval = true)
    boolean existsByDedupKey(String dedupKey);

    /** Temporary Task 4 bridge; daily scanning never calls this overload. */
    @Deprecated(forRemoval = true)
    WriteOutcome recordSuccess(FlowFieldChangeCaptureMeta meta, FlowFieldChangeSet changeSet);

    /** Temporary Task 4 bridge; daily scanning never calls this overload. */
    @Deprecated(forRemoval = true)
    WriteOutcome recordFailure(FlowFieldChangeCaptureMeta meta, String safeError);

    enum HistoryState {
        NONE,
        FAILED,
        SUCCESS
    }

    enum WriteDisposition {
        INSERTED,
        UPGRADED,
        SKIPPED
    }

    record WriteOutcome(long logId, WriteDisposition disposition) {
    }
}
