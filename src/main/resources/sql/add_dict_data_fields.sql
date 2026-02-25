-- 为字典技术衍生表添加新字段
-- 1. 在英文简称(english_abbr)和中文名称(chinese_name)之间添加英文名称(english_name)字段
-- 2. 在数据格式(data_format)和JAVA/ESF规范命名(java_esf_name)之间添加取值范围(value_range)字段

-- 检查并添加 english_name 字段
SET @dbname = DATABASE();
SET @tablename = 'dict_data';
SET @columnname = 'english_name';

SET @preparedStatement = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE
            (TABLE_SCHEMA = @dbname)
            AND (TABLE_NAME = @tablename)
            AND (COLUMN_NAME = @columnname)
    ) > 0,
    'SELECT ''Column english_name already exists.'' AS message;',
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' VARCHAR(200) COMMENT ''英文名称'' AFTER english_abbr;')
));

PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 检查并添加 value_range 字段
SET @columnname2 = 'value_range';

SET @preparedStatement2 = (SELECT IF(
    (
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE
            (TABLE_SCHEMA = @dbname)
            AND (TABLE_NAME = @tablename)
            AND (COLUMN_NAME = @columnname2)
    ) > 0,
    'SELECT ''Column value_range already exists.'' AS message;',
    CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname2, ' VARCHAR(200) COMMENT ''取值范围'' AFTER data_format;')
));

PREPARE alterIfNotExists2 FROM @preparedStatement2;
EXECUTE alterIfNotExists2;
DEALLOCATE PREPARE alterIfNotExists2;

-- 显示表结构确认
DESC dict_data;

