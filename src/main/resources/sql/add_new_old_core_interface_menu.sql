-- 添加"新老核心接口文档比对"菜单（紧跟在"迁移中间表比对"之后）
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time)
SELECT 'new-old-core-interface-compare', '新老核心接口文档比对', id, 2, '🆚', 12, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE
    menu_name  = '新老核心接口文档比对',
    icon       = '🆚',
    sort_order = 12,
    update_time = NOW();
