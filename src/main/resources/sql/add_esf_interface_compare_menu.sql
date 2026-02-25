-- 添加ESF接口文档比对菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time) 
SELECT 'esf-interface-compare', 'ESF接口文档比对', id, 2, '📡', 10, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE 
    menu_name = 'ESF接口文档比对',
    icon = '📡',
    sort_order = 10,
    update_time = NOW();
