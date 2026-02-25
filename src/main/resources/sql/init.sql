-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS apimega DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE apimega;

-- 删除表（如果存在）
DROP TABLE IF EXISTS dict_data;

-- 创建字典数据表
CREATE TABLE dict_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    sort_order INT COMMENT '排序号（Excel中的行号，用于保持顺序）',
    data_item_code VARCHAR(50) NOT NULL COMMENT '数据项编号',
    english_abbr VARCHAR(200) COMMENT '英文简称',
    chinese_name VARCHAR(200) COMMENT '中文名称',
    dict_attr VARCHAR(50) COMMENT '字典属性',
    domain_chinese_name VARCHAR(200) COMMENT '域中文名称',
    data_type VARCHAR(50) COMMENT '数据类型',
    data_format VARCHAR(100) COMMENT '数据格式',
    java_esf_name VARCHAR(200) COMMENT 'JAVA/ESF规范命名',
    esf_data_format VARCHAR(100) COMMENT 'ESF数据格式',
    gaussdb_data_format VARCHAR(100) COMMENT 'GaussDB数据格式',
    goldendb_data_format VARCHAR(100) COMMENT 'GoldenDB数据格式',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_sort_order (sort_order),
    INDEX idx_data_item_code (data_item_code),
    INDEX idx_english_abbr (english_abbr),
    INDEX idx_chinese_name (chinese_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

