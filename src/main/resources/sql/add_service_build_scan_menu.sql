-- 添加"服务和构建扫描"菜单项
INSERT INTO sys_menu (parent_id, menu_name, menu_code, menu_type, path, component, icon, sort_order, status, create_time, update_time)
VALUES (
    (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_code = 'git-management') AS temp),
    '服务和构建扫描',
    'service-build-scan',
    'MENU',
    '/service-build-scan',
    'Layout',
    'el-icon-tools',
    5,
    0,
    NOW(),
    NOW()
);

