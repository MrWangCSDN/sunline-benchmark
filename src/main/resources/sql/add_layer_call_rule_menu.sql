-- 添加"分层调用规则配置"菜单项到"GIT代码管理"菜单下

-- 插入"分层调用规则配置"菜单项
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'layer-call-rule', '分层调用规则配置', id, 2, '⚙️', 6, 1 FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE 
    menu_name = '分层调用规则配置',
    icon = '⚙️',
    sort_order = 6,
    update_time = NOW();

