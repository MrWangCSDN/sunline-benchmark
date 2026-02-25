-- 添加"在途字典使用分析"菜单项到"数标管理"菜单下

-- 插入"在途字典使用分析"菜单项
INSERT INTO sys_menu (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status) 
SELECT 'dict-usage-analysis', '在途字典使用分析', id, 2, '📊', 8, 1 FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE 
    menu_name = '在途字典使用分析',
    icon = '📊',
    sort_order = 8,
    update_time = NOW();

-- 说明：
-- menu_code: 菜单编码，唯一标识（dict-usage-analysis）
-- menu_name: 菜单名称，显示在界面上（在途字典使用分析）
-- parent_id: 父菜单ID，通过SELECT子查询获取"数标管理"的ID
-- menu_type: 菜单类型：1-父菜单，2-子菜单（这里是子菜单，所以是2）
-- icon: 菜单图标（📊）
-- sort_order: 排序顺序，数字越小越靠前（8）
-- status: 状态：0-禁用，1-启用（这里是启用，所以是1）
-- ON DUPLICATE KEY UPDATE: 如果菜单已存在则更新

