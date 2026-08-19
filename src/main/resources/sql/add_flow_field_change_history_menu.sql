-- Add the read-only Flowtrans interface change history entry for existing environments.
INSERT INTO sys_menu
    (menu_code, menu_name, parent_id, menu_type, icon, sort_order, status,
     create_time, update_time)
SELECT 'flow-field-change-history', '交易接口变动历史', id, 2, '📜', 5, 1,
       NOW(), NOW()
FROM sys_menu WHERE menu_code = 'dict-management'
ON DUPLICATE KEY UPDATE
    menu_name = '交易接口变动历史',
    parent_id = VALUES(parent_id),
    menu_type = 2,
    icon = '📜',
    sort_order = 5,
    status = 1,
    update_time = NOW();
