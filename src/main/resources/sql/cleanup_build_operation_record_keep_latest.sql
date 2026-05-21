-- 清理 build_operation_record 历史数据，只保留最新一次任务对应的整组记录
DELETE FROM build_operation_record
WHERE operation_id <> (
    SELECT latest_operation_id
    FROM (
        SELECT operation_id AS latest_operation_id
        FROM build_operation_record
        WHERE record_type = 'TASK'
        ORDER BY id DESC
        LIMIT 1
    ) t
);
