-- 添加"数据检索"菜单项到"GIT代码管理"菜单下

-- 插入"数据检索"菜单项
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status)
SELECT 'xml-scan', '数据检索', id, 2, '🔍', 7, 1 FROM sys_menu WHERE menu_code = 'git-management'
ON DUPLICATE KEY UPDATE
    menu_name = '数据检索',
    icon = '🔍',
    sort_order = 7,
    update_time = NOW();

-- 说明：
-- menu_code: 菜单编码，唯一标识（xml-scan）
-- menu_name: 菜单名称，显示在界面上（数据检索）
-- parent_id: 父菜单ID，通过SELECT子查询获取"Git代码管理"的ID
-- menu_type: 菜单类型：1-父菜单，2-子菜单
-- icon: 菜单图标（🔍）
-- sort_order: 排序顺序（7）
-- status: 状态：0-禁用，1-启用
-- ON DUPLICATE KEY UPDATE: 如果菜单已存在则更新
