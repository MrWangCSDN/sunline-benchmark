-- 添加Excel数据清洗菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time) 
SELECT 'excel-clean', 'Excel数据清洗', id, 2, '🧹', 7, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE 
    menu_name = 'Excel数据清洗',
    icon = '🧹',
    sort_order = 7,
    update_time = NOW();

