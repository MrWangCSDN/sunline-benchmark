-- 添加交易树查询菜单
-- menu_code: 菜单编码（用于前端路由标识和权限检查），例如 'flow-tree-query'
-- menu_name: 菜单名称（中文显示名称），例如 '交易树查询'
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time) 
SELECT 'flow-tree-query', '交易树查询', id, 2, '🌳', 8, 1, NOW(), NOW() 
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE 
    menu_name = '交易树查询',
    icon = '🌳',
    sort_order = 8,
    update_time = NOW();

