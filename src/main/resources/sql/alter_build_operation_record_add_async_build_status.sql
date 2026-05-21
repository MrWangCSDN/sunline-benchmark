ALTER TABLE build_operation_record
    ADD COLUMN async_build_status VARCHAR(32) DEFAULT NULL COMMENT '8123 异步构建状态：未触发/异步构建中/成功/失败/触发失败' AFTER status;

UPDATE build_operation_record
SET async_build_status = CASE
    WHEN status = 'SUCCESS' THEN '未触发'
    ELSE NULL
END
WHERE async_build_status IS NULL;
