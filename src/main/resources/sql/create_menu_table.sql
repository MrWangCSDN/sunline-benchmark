-- 创建菜单表
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    menu_code VARCHAR(50) NOT NULL UNIQUE COMMENT '菜单编码（用于前端路由标识）',
    menu_name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父菜单ID，0表示顶级菜单',
    menu_type TINYINT(1) DEFAULT 1 COMMENT '菜单类型：1-父菜单，2-子菜单',
    icon VARCHAR(50) COMMENT '菜单图标',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    status TINYINT(1) DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_parent_id (parent_id),
    INDEX idx_menu_code (menu_code),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单表';

-- 插入菜单数据
-- 数标管理（父菜单）
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) VALUES
('dict-management', '数标管理', 0, 1, '📊', 1, 1)
ON DUPLICATE KEY UPDATE menu_name=menu_name;

-- 数标管理子菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'data-list', '数据列表', id, 2, '📋', 1, 1 FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'excel-import', 'Excel导入分析', id, 2, '📊', 2, 1 FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'excel-import-ing', 'Excel导入分析(在途)', id, 2, '📊', 3, 1 FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'change-history', '变更历史', id, 2, '📜', 4, 1 FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'excel-export', 'Excel导出', id, 2, '📥', 5, 1 FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'enum-mapping', '枚举映射关系维护', id, 2, '🔗', 6, 1 FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'domain-mapping', '域清单映射关系维护', id, 2, '🗂️', 7, 1 FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

-- Git代码管理（父菜单）
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) VALUES
('git-management', 'Git代码管理', 0, 1, '🌿', 2, 1)
ON DUPLICATE KEY UPDATE menu_name=menu_name;

-- Git代码管理子菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'git-branch-create', '分支创建', id, 2, '🌿', 1, 1 FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'git-permission-manage', '权限管理', id, 2, '👥', 2, 1 FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

-- 系统管理（父菜单）
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) VALUES
('system-management', '系统管理', 0, 1, '⚙️', 3, 1)
ON DUPLICATE KEY UPDATE menu_name=menu_name;

-- 系统管理子菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'user-management', '用户管理', id, 2, '👥', 1, 1 FROM sys_menu WHERE menu_code = 'system-management'
ON DUPLICATE KEY UPDATE menu_name=menu_name;

