-- 添加"代码方法栈扫描"菜单项
INSERT INTO sys_menu (parent_id, menu_name, menu_code, menu_type, path, component, icon, sort_order, status, create_time, update_time)
VALUES (
    (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_code = 'git-management') AS temp),
    '代码方法栈扫描',
    'method-stack-scan',
    'MENU',
    '/method-stack-scan',
    'Layout',
    'el-icon-connection',
    7,
    0,
    NOW(),
    NOW()
);

