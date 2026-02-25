-- 添加数据库表导出菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time) 
SELECT 'db-table-export', '数据库表导出', id, 2, '💾', 8, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE 
    menu_name = '数据库表导出',
    icon = '💾',
    sort_order = 8,
    update_time = NOW();

