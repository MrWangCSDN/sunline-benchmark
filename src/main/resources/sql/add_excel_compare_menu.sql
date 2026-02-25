-- 添加Excel文档比对菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time) 
SELECT 'excel-compare', 'Excel文档比对', id, 2, '📊', 6, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE 
    menu_name = 'Excel文档比对',
    icon = '📊',
    sort_order = 6,
    update_time = NOW();

