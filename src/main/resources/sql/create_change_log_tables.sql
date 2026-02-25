-- 创建域清单变更日志表
CREATE TABLE IF NOT EXISTS domain_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    version_id BIGINT NOT NULL COMMENT '版本ID（关联import_history.id）',
    chinese_name VARCHAR(255) COMMENT '域中文名称',
    change_type VARCHAR(20) NOT NULL COMMENT '变更类型：NEW/UPDATE/DELETE',
    field_name VARCHAR(100) COMMENT '变更字段名',
    old_value TEXT COMMENT '原值',
    new_value TEXT COMMENT '新值',
    change_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    INDEX idx_version_id (version_id),
    INDEX idx_chinese_name (chinese_name),
    INDEX idx_change_type (change_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='域清单变更日志表';

-- 创建代码扩展清单变更日志表
CREATE TABLE IF NOT EXISTS code_extension_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    version_id BIGINT NOT NULL COMMENT '版本ID（关联import_history.id）',
    code_domain_chinese_name VARCHAR(255) COMMENT '代码域中文名称',
    code_value VARCHAR(100) COMMENT '代码取值',
    change_type VARCHAR(20) NOT NULL COMMENT '变更类型：NEW/UPDATE/DELETE',
    field_name VARCHAR(100) COMMENT '变更字段名',
    old_value TEXT COMMENT '原值',
    new_value TEXT COMMENT '新值',
    change_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
    INDEX idx_version_id (version_id),
    INDEX idx_code_domain (code_domain_chinese_name),
    INDEX idx_change_type (change_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码扩展清单变更日志表';

-- 验证表创建成功
SHOW TABLES LIKE '%change_log';

-- 查看表结构
DESC domain_change_log;
DESC code_extension_change_log;

SELECT '✅ 变更日志表创建完成！' AS message;

