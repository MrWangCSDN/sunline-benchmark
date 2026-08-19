CREATE TABLE IF NOT EXISTS flow_field_change_log (
    id                   BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    dedup_key            CHAR(64)      NOT NULL COMMENT 'commit/文件幂等 SHA-256',
    scan_run_id          BIGINT        NOT NULL COMMENT '扫描运行 ID',
    change_date          DATE          NOT NULL COMMENT '上海时区提交日期',
    webhook_uuid         VARCHAR(128)  NULL COMMENT '兼容旧 Webhook 数据',
    project_id           BIGINT        NOT NULL COMMENT 'GitLab 项目 ID',
    project_name         VARCHAR(255)  NULL COMMENT 'GitLab 项目名称',
    project_path         VARCHAR(512)  NULL COMMENT 'GitLab path_with_namespace',
    branch               VARCHAR(64)   NOT NULL DEFAULT 'master' COMMENT '仅 master',
    file_path            VARCHAR(1024) NOT NULL COMMENT 'flowtrans.xml 仓库路径',
    flow_id              VARCHAR(128)  NULL COMMENT '交易码',
    flow_longname        VARCHAR(512)  NULL COMMENT '交易名称',
    file_change_type     VARCHAR(16)   NOT NULL COMMENT 'ADD/MODIFY/DELETE/UNKNOWN',
    capture_status       VARCHAR(16)   NOT NULL COMMENT 'SUCCESS/FAILED',
    error_message        TEXT          NULL COMMENT '失败摘要，不含密钥',
    before_sha           VARCHAR(64)   NULL COMMENT '兼容旧 Webhook before SHA',
    after_sha            VARCHAR(64)   NULL COMMENT '兼容旧 Webhook after SHA',
    parent_sha           VARCHAR(64)   NULL COMMENT '第一父 commit SHA',
    commit_sha           VARCHAR(64)   NOT NULL COMMENT '当前 commit SHA',
    commit_message       TEXT          NULL COMMENT '提交信息',
    commit_author        VARCHAR(255)  NULL COMMENT '提交人姓名',
    commit_email         VARCHAR(255)  NULL COMMENT '提交人邮箱',
    commit_time          DATETIME      NOT NULL COMMENT '提交时间',
    add_count            INT           NOT NULL DEFAULT 0 COMMENT '新增字段数',
    modify_count         INT           NOT NULL DEFAULT 0 COMMENT '修改字段数',
    remove_count         INT           NOT NULL DEFAULT 0 COMMENT '删除字段数',
    input_change_count   INT           NOT NULL DEFAULT 0 COMMENT 'input 变化字段数',
    output_change_count  INT           NOT NULL DEFAULT 0 COMMENT 'output 变化字段数',
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_ffcl_dedup (dedup_key),
    KEY idx_ffcl_change_date (change_date),
    KEY idx_ffcl_project_file (project_id, file_path(255)),
    KEY idx_ffcl_commit (project_id, commit_sha),
    KEY idx_ffcl_author (commit_author),
    KEY idx_ffcl_commit_time (commit_time),
    KEY idx_ffcl_scan_run (scan_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='GitLab master flowtrans 接口变动历史';

CREATE TABLE IF NOT EXISTS flow_field_change_detail (
    id                   BIGINT        PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    log_id               BIGINT        NOT NULL COMMENT '关联 flow_field_change_log.id',
    io_type              VARCHAR(8)    NOT NULL COMMENT 'input/output',
    field_path           VARCHAR(1024) NOT NULL COMMENT '嵌套字段路径',
    field_id             VARCHAR(256)  NOT NULL COMMENT '<field id>',
    change_type          VARCHAR(16)   NOT NULL COMMENT 'ADD/MODIFY/DELETE',
    old_snapshot         LONGTEXT      NULL COMMENT '旧字段属性 JSON',
    new_snapshot         LONGTEXT      NULL COMMENT '新字段属性 JSON',
    changed_attributes   LONGTEXT      NULL COMMENT '修改属性 old/new JSON',
    KEY idx_ffcd_log (log_id),
    KEY idx_ffcd_field (io_type, field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='flowtrans 接口字段变动明细';

CREATE TABLE IF NOT EXISTS flow_field_scan_run (
    id                   BIGINT        PRIMARY KEY AUTO_INCREMENT,
    project_id           BIGINT        NOT NULL,
    project_name         VARCHAR(255)  NULL,
    project_path         VARCHAR(512)  NULL,
    branch               VARCHAR(64)   NOT NULL DEFAULT 'master',
    window_start         DATETIME      NOT NULL,
    window_end           DATETIME      NOT NULL,
    status               VARCHAR(32)   NOT NULL,
    commit_count         INT           NOT NULL DEFAULT 0,
    changed_file_count   INT           NOT NULL DEFAULT 0,
    history_count        INT           NOT NULL DEFAULT 0,
    failed_file_count    INT           NOT NULL DEFAULT 0,
    skipped_count        INT           NOT NULL DEFAULT 0,
    cursor_advanced      TINYINT(1)    NOT NULL DEFAULT 0,
    error_message        TEXT          NULL,
    started_at           DATETIME      NOT NULL,
    finished_at          DATETIME      NULL,
    create_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ffsr_project_window (project_id, branch, window_end),
    KEY idx_ffsr_status_time (status, started_at),
    KEY idx_ffsr_project_time (project_id, window_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='flowtrans 接口变动定时扫描运行记录';

CREATE TABLE IF NOT EXISTS flow_field_scan_cursor (
    project_id           BIGINT        NOT NULL,
    branch               VARCHAR(64)   NOT NULL DEFAULT 'master',
    project_name         VARCHAR(255)  NULL,
    project_path         VARCHAR(512)  NULL,
    last_success_end     DATETIME      NOT NULL,
    last_run_id          BIGINT        NULL,
    update_time          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
                                      ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, branch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='flowtrans 接口变动扫描游标';
