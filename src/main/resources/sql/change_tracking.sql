-- 数据变更追踪功能 - 数据库表结构

USE apimega;

-- 1. 版本表
CREATE TABLE IF NOT EXISTS dict_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    version_number VARCHAR(50) NOT NULL COMMENT '版本号，格式：V+时间戳',
    import_time DATETIME NOT NULL COMMENT '导入时间',
    file_name VARCHAR(200) COMMENT 'Excel文件名',
    file_size BIGINT COMMENT '文件大小（字节）',
    file_md5 VARCHAR(32) COMMENT '文件MD5值',
    total_count INT DEFAULT 0 COMMENT '总记录数',
    new_count INT DEFAULT 0 COMMENT '新增数量',
    update_count INT DEFAULT 0 COMMENT '修改数量',
    delete_count INT DEFAULT 0 COMMENT '删除数量',
    unchanged_count INT DEFAULT 0 COMMENT '不变数量',
    operator VARCHAR(50) COMMENT '操作人',
    remark TEXT COMMENT '备注',
    status VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAILED/PREVIEW',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    UNIQUE KEY uk_version (version_number),
    INDEX idx_import_time (import_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典版本表';

-- 2. 变更日志表
CREATE TABLE IF NOT EXISTS dict_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    version_id BIGINT NOT NULL COMMENT '版本ID',
    data_item_code VARCHAR(50) NOT NULL COMMENT '数据项编号',
    change_type VARCHAR(10) NOT NULL COMMENT '变更类型：NEW/UPDATE/DELETE/UNCHANGED',
    field_name VARCHAR(50) COMMENT '变更字段（UPDATE时使用）',
    old_value TEXT COMMENT '旧值',
    new_value TEXT COMMENT '新值',
    change_time DATETIME NOT NULL COMMENT '变更时间',
    
    INDEX idx_version (version_id),
    INDEX idx_code (data_item_code),
    INDEX idx_type (change_type),
    INDEX idx_time (change_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典变更日志表';

-- 3. 修改主表，添加变更追踪字段
ALTER TABLE dict_data 
ADD COLUMN IF NOT EXISTS version_id BIGINT COMMENT '版本ID' AFTER sort_order,
ADD COLUMN IF NOT EXISTS data_hash VARCHAR(32) COMMENT '数据MD5值' AFTER version_id,
ADD COLUMN IF NOT EXISTS change_type VARCHAR(10) COMMENT '变更类型：NEW/UPDATE/DELETE/UNCHANGED' AFTER data_hash,
ADD COLUMN IF NOT EXISTS is_deleted TINYINT DEFAULT 0 COMMENT '是否已删除：0-否，1-是' AFTER change_type,
ADD COLUMN IF NOT EXISTS deleted_at DATETIME COMMENT '删除时间' AFTER is_deleted;

-- 添加索引
ALTER TABLE dict_data 
ADD INDEX IF NOT EXISTS idx_version_id (version_id),
ADD INDEX IF NOT EXISTS idx_data_hash (data_hash),
ADD INDEX IF NOT EXISTS idx_change_type (change_type),
ADD INDEX IF NOT EXISTS idx_deleted (is_deleted);

-- 查看表结构
DESC dict_version;
DESC dict_change_log;
DESC dict_data;

SELECT '数据变更追踪表结构创建完成！' AS status;

