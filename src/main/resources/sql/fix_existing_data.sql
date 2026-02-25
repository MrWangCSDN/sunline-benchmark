-- 修复现有数据的变更追踪字段

USE apimega;

-- 1. 检查当前数据状态
SELECT 
    COUNT(*) AS total_count,
    SUM(CASE WHEN is_deleted IS NULL THEN 1 ELSE 0 END) AS null_is_deleted,
    SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END) AS active_count,
    SUM(CASE WHEN is_deleted = 1 THEN 1 ELSE 0 END) AS deleted_count
FROM dict_data;

-- 2. 将所有is_deleted为NULL的数据设置为0（未删除）
UPDATE dict_data 
SET is_deleted = 0 
WHERE is_deleted IS NULL;

-- 3. 如果sort_order为NULL，按id顺序设置
SET @sort_num = 0;
UPDATE dict_data 
SET sort_order = (@sort_num := @sort_num + 1) 
WHERE sort_order IS NULL
ORDER BY id;

-- 4. 查看修复后的状态
SELECT 
    COUNT(*) AS total_count,
    SUM(CASE WHEN is_deleted = 0 THEN 1 ELSE 0 END) AS active_count,
    SUM(CASE WHEN is_deleted = 1 THEN 1 ELSE 0 END) AS deleted_count,
    SUM(CASE WHEN sort_order IS NULL THEN 1 ELSE 0 END) AS null_sort_order
FROM dict_data;

SELECT '数据修复完成！' AS status;

