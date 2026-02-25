-- 数据库升级脚本：添加排序号字段
-- 用途：保持导入数据与Excel中的顺序一致

USE apimega;

-- 添加排序号字段
ALTER TABLE dict_data ADD COLUMN sort_order INT COMMENT '排序号（Excel中的行号，用于保持顺序）' AFTER id;

-- 添加排序号索引
CREATE INDEX idx_sort_order ON dict_data(sort_order);

-- 为现有数据设置排序号（按ID顺序）
SET @sort_num = 0;
UPDATE dict_data SET sort_order = (@sort_num := @sort_num + 1) ORDER BY id;

-- 查看结果
SELECT COUNT(*) as total_records FROM dict_data;
SELECT * FROM dict_data ORDER BY sort_order LIMIT 10;

