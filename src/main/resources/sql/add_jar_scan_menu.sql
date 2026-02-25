-- 添加Jar包扫描分析菜单
INSERT INTO sys_menu (menu_code, menu_name, menu_url, parent_code, sort_order, menu_type, create_time, update_time)
VALUES ('jar-scan-analysis', '扫描分析', '/jar-scan-analysis.html', 'git-management', 3, 'page', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    menu_name = '扫描分析',
    menu_url = '/jar-scan-analysis.html',
    parent_code = 'git-management',
    sort_order = 3,
    update_time = NOW();

