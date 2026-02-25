-- 创建用户菜单权限表
CREATE TABLE IF NOT EXISTS sys_user_menu_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    permission_type VARCHAR(20) NOT NULL DEFAULT 'READ_ONLY' COMMENT '权限类型：READ_ONLY-只读，READ_WRITE-读写',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_menu (user_id, menu_id) COMMENT '用户和菜单唯一索引',
    INDEX idx_user_id (user_id),
    INDEX idx_menu_id (menu_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (menu_id) REFERENCES sys_menu(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户菜单权限表';

