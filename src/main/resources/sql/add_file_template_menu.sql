-- 添加文件模版菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time)
SELECT 'file-template-export', '文件模版', id, 2, '📄', 12, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE
    menu_name = '文件模版',
    icon = '📄',
    sort_order = 12,
    update_time = NOW();
