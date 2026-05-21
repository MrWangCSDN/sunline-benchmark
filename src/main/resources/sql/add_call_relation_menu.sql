-- 添加"调用关系图谱扫描"菜单项到"GIT代码管理"菜单下

INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status, create_time, update_time)
SELECT 'call-relation', '调用关系图谱扫描', id, 2, '🕸️', 9, 1, NOW(), NOW()
FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE
    menu_name  = '调用关系图谱扫描',
    icon       = '🕸️',
    sort_order = 9,
    update_time = NOW();

-- 说明：
-- menu_code:  菜单编码，唯一标识（call-relation），对应页面 /call-relation.html
-- menu_name:  菜单名称，显示在界面上（调用关系图谱扫描）
-- parent_id:  父菜单ID，通过 SELECT 子查询获取 "GIT代码管理" 的 id
-- menu_type:  菜单类型：1-父菜单，2-子菜单（这里是子菜单，所以是 2）
-- icon:       菜单图标（🕸️）
-- sort_order: 排序顺序，数字越小越靠前（9，排在 flow-tree-query 之后）
-- status:     状态：0-禁用，1-启用（这里是启用，所以是 1）
-- ON DUPLICATE KEY UPDATE：如果菜单已存在则更新，幂等可重复执行
