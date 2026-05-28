-- 添加"对公核心文件基线比对"菜单（紧跟在"新老核心接口文档比对"之后）
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time)
SELECT 'public-core-file-baseline-compare', '对公核心文件基线比对', id, 2, '📊', 13, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE
    menu_name  = '对公核心文件基线比对',
    icon       = '📊',
    sort_order = 13,
    update_time = NOW();
