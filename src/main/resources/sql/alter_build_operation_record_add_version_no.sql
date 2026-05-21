-- 已经创建过 build_operation_record 表的环境，请执行本脚本补充版本号字段
ALTER TABLE build_operation_record
    ADD COLUMN version_no VARCHAR(32) DEFAULT NULL COMMENT '任务版本号，如 V-2026.0331.12:26:07' AFTER operation_id;

UPDATE build_operation_record
SET version_no = CONCAT('V-', DATE_FORMAT(COALESCE(create_time, NOW()), '%Y.%m%d.%H:%i:%s'))
WHERE version_no IS NULL OR version_no = '';

ALTER TABLE build_operation_record
    MODIFY COLUMN version_no VARCHAR(32) NOT NULL COMMENT '任务版本号，如 V-2026.0331.12:26:07';

CREATE INDEX idx_build_version_no ON build_operation_record(version_no);
