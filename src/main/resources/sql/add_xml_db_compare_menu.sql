-- 添加XML模型与数据库比对菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time) 
SELECT 'xml-db-compare', 'XML模型与数据库比对', id, 2, '🔄', 9, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE 
    menu_name = 'XML模型与数据库比对',
    icon = '🔄',
    sort_order = 9,
    update_time = NOW();

