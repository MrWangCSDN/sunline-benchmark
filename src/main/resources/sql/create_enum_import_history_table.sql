-- 创建枚举映射导入历史记录表
CREATE TABLE IF NOT EXISTS enum_import_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    version VARCHAR(50) NOT NULL COMMENT '版本号（V1, V2, V3...）',
    modifier VARCHAR(100) NOT NULL COMMENT '修改人',
    modify_content VARCHAR(500) COMMENT '修改内容',
    import_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
    record_count INT DEFAULT 0 COMMENT '导入记录数',
    UNIQUE KEY uk_version (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='枚举映射导入历史记录表';

-- 创建索引
CREATE INDEX idx_import_time ON enum_import_history(import_time);

