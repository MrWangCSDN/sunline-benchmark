-- 添加保护分支设置菜单
INSERT INTO sys_menu (menu_code, menu_name, menu_url, parent_code, sort_order, menu_type, create_time, update_time)
VALUES ('protected-branch-settings', '保护分支设置', '/protected-branch-settings.html', 'git-management', 4, 'page', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    menu_name = '保护分支设置',
    menu_url = '/protected-branch-settings.html',
    parent_code = 'git-management',
    sort_order = 4,
    update_time = NOW();

