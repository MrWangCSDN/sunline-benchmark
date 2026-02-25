-- 创建hard_code_method_stack表
CREATE TABLE IF NOT EXISTS hard_code_method_stack (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    service_type_id VARCHAR(200) COMMENT 'ServiceType ID',
    service_type_impl_id VARCHAR(200) COMMENT 'ServiceImpl ID',
    service_type_kind VARCHAR(50) COMMENT 'ServiceType类型（如pbs、pcs等）',
    service_id VARCHAR(200) COMMENT 'Service ID',
    service_name VARCHAR(200) COMMENT 'Service名称',
    code_service_type VARCHAR(500) COMMENT '代码中调用的ServiceType（SysUtil.getInstance/getRemoteInstance中的类名）',
    code_method_type VARCHAR(200) COMMENT '调用的方法名',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_service_type_id (service_type_id),
    INDEX idx_service_type_impl_id (service_type_impl_id),
    INDEX idx_service_id (service_id),
    INDEX idx_code_service_type (code_service_type),
    INDEX idx_code_method_type (code_method_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='硬编码方法调用栈表';

