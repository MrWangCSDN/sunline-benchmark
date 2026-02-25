-- 创建用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(255) NOT NULL COMMENT '密码（加密后）',
    chinese_name VARCHAR(100) COMMENT '用户中文名',
    git_username VARCHAR(100) COMMENT 'Git用户名',
    is_admin TINYINT(1) DEFAULT 0 COMMENT '是否超级管理员：0-否，1-是',
    status TINYINT(1) DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 插入默认超级管理员用户（密码：Liang@201314，使用BCrypt加密后的值）
-- BCrypt加密后的密码：Liang@201314 -> $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwK8pJ5C
-- 如果首次登录时密码未加密，系统会自动加密
INSERT INTO sys_user (username, password, chinese_name, git_username, is_admin, status) 
VALUES ('admin', 'Liang@201314', '超级管理员', 'c-wangsh8', 1, 1)
ON DUPLICATE KEY UPDATE username=username;

