-- 全量拉取+编译操作记录表
CREATE TABLE IF NOT EXISTS build_operation_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    operation_id VARCHAR(64) NOT NULL COMMENT '一次任务的唯一编号',
    version_no VARCHAR(32) NOT NULL COMMENT '任务版本号，如 V-2026.0331.12:26:07',
    record_type VARCHAR(20) NOT NULL COMMENT '记录类型：TASK/PULL_PROJECT/BUILD_BATCH/BUILD_PROJECT',
    stage VARCHAR(20) NOT NULL COMMENT '阶段：ALL/PULL/BUILD',
    batch_index INT DEFAULT NULL COMMENT '批次序号，从0开始',
    batch_name VARCHAR(100) DEFAULT NULL COMMENT '批次名称',
    project_name VARCHAR(100) DEFAULT NULL COMMENT '工程名称',
    sort_no INT DEFAULT 0 COMMENT '展示顺序',
    trigger_api VARCHAR(100) DEFAULT NULL COMMENT '触发接口',
    trigger_mode VARCHAR(20) DEFAULT NULL COMMENT '触发方式：API/MANUAL/WEBHOOK',
    operator VARCHAR(100) DEFAULT NULL COMMENT '操作人',
    status VARCHAR(20) NOT NULL COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/CANCELLED/TIMEOUT',
    async_build_status VARCHAR(32) DEFAULT NULL COMMENT '8123 异步构建状态：未触发/异步构建中/成功/失败/触发失败',
    result_message VARCHAR(1000) DEFAULT NULL COMMENT '结果描述',
    error_type VARCHAR(200) DEFAULT NULL COMMENT '错误类型',
    error_message TEXT COMMENT '错误摘要',
    error_stack LONGTEXT COMMENT '错误堆栈或失败日志摘要',
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
    end_time DATETIME DEFAULT NULL COMMENT '结束时间',
    cost_ms BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    log_file VARCHAR(255) DEFAULT NULL COMMENT '日志文件路径',
    extra_json LONGTEXT COMMENT '扩展信息JSON',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全量拉取+编译操作记录表';

CREATE INDEX idx_build_operation_id ON build_operation_record(operation_id);
CREATE INDEX idx_build_version_no ON build_operation_record(version_no);
CREATE INDEX idx_build_record_type ON build_operation_record(record_type);
CREATE INDEX idx_build_stage ON build_operation_record(stage);
CREATE INDEX idx_build_status ON build_operation_record(status);
CREATE INDEX idx_build_start_time ON build_operation_record(start_time DESC);
CREATE INDEX idx_build_batch ON build_operation_record(operation_id, batch_index);
CREATE INDEX idx_build_project ON build_operation_record(operation_id, project_name);
