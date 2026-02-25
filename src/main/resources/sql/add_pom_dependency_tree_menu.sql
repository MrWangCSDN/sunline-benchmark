-- 添加POM依赖树检查菜单
INSERT INTO sys_menu (menu_code, menu_name, menu_type, parent_code, menu_url, menu_icon, sort_order, status, create_time, update_time)
VALUES ('pom-dependency-tree', 'POM依赖树检查', 'MENU', 'git-management', '/pom-dependency-tree.html', '📦', 7, 1, NOW(), NOW());

