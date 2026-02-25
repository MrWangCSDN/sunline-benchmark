-- 添加迁移中间表比对菜单
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time) 
SELECT 'migration-table-compare', '迁移中间表比对', id, 2, '🔄', 11, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE 
    menu_name = '迁移中间表比对',
    icon = '🔄',
    sort_order = 11,
    update_time = NOW();
