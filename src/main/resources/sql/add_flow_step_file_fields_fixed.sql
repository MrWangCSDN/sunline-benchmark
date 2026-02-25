-- 为flow_step表添加文件信息字段
-- 如果字段已存在，则不会报错（使用 IF NOT EXISTS）

-- 检查并添加 file_name 字段
SET @dbname = DATABASE();
SET @tablename = 'flow_step';
SET @columnname = 'file_name';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(500) COMMENT ''文件名（service类型时，匹配到的文件名）''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 检查并添加 file_path 字段
SET @columnname = 'file_path';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(1000) COMMENT ''文件路径（service类型时，匹配到的文件路径）''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 检查并添加 file_jar_name 字段
SET @columnname = 'file_jar_name';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (TABLE_SCHEMA = @dbname)
      AND (TABLE_NAME = @tablename)
      AND (COLUMN_NAME = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(100) COMMENT ''文件来源jar包（service类型时，匹配到的jar包名称）''')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 或者直接使用简单的ALTER TABLE语句（如果字段不存在会报错，但可以忽略）
-- ALTER TABLE flow_step 
-- ADD COLUMN file_name VARCHAR(500) COMMENT '文件名（service类型时，匹配到的文件名）',
-- ADD COLUMN file_path VARCHAR(1000) COMMENT '文件路径（service类型时，匹配到的文件路径）',
-- ADD COLUMN file_jar_name VARCHAR(100) COMMENT '文件来源jar包（service类型时，匹配到的jar包名称）';

