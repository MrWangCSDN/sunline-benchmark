-- Upgrade the deployed Webhook history schema without rewriting legacy facts.
-- Run once after backing up the existing flow_field_change_* tables.

ALTER TABLE flow_field_change_log
    ADD COLUMN scan_run_id BIGINT NULL COMMENT '扫描运行 ID' AFTER dedup_key,
    ADD COLUMN change_date DATE NULL COMMENT '上海时区提交日期' AFTER scan_run_id,
    ADD COLUMN project_path VARCHAR(512) NULL COMMENT 'GitLab path_with_namespace' AFTER project_name,
    ADD COLUMN parent_sha VARCHAR(64) NULL COMMENT '第一父 commit SHA' AFTER after_sha,
    ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER create_time;

UPDATE flow_field_change_log
   SET change_date = DATE(COALESCE(commit_time, create_time))
 WHERE change_date IS NULL;

ALTER TABLE flow_field_change_log
    ADD INDEX idx_ffcl_change_date (change_date),
    ADD INDEX idx_ffcl_commit (project_id, commit_sha),
    ADD INDEX idx_ffcl_scan_run (scan_run_id);

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
