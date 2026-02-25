-- 创建service_type_impl_file表
CREATE TABLE IF NOT EXISTS service_type_impl_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    service_type_impl_id VARCHAR(200) NOT NULL COMMENT 'ServiceImpl ID（来源于XML中<serviceImpl>节点的id属性）',
    service_impl_long_name VARCHAR(500) COMMENT 'ServiceImpl长名称（来源于XML中<serviceImpl>节点的longname属性）',
    service_impl_kind VARCHAR(50) COMMENT 'ServiceImpl类型（从文件名提取，如pbsImpl、pcsImpl、pbcbImpl等）',
    service_impl_from_jar VARCHAR(100) COMMENT '来源jar包名称（不含版本号）',
    service_impl_package VARCHAR(500) COMMENT 'ServiceImpl包路径（来源于XML中<serviceImpl>节点的package属性）',
    service_type_id VARCHAR(200) COMMENT 'ServiceType ID（来源于XML中<serviceImpl>节点的serviceType属性）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_service_type_impl_id (service_type_impl_id),
    INDEX idx_service_impl_kind (service_impl_kind),
    INDEX idx_service_impl_from_jar (service_impl_from_jar),
    INDEX idx_service_type_id (service_type_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ServiceType实现文件信息表';

