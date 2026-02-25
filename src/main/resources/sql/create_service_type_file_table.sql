-- 创建service_type_file表
CREATE TABLE IF NOT EXISTS service_type_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '自增主键ID',
    service_type_id VARCHAR(200) NOT NULL COMMENT 'ServiceType ID（来源于XML中<serviceType>节点的id属性）',
    service_type_long_name VARCHAR(500) COMMENT 'ServiceType长名称（来源于XML中<serviceType>节点的longname属性）',
    service_type_kind VARCHAR(50) COMMENT 'ServiceType类型（从文件名提取，如pbs、pcs、pbcb等）',
    service_type_from_jar VARCHAR(100) COMMENT '来源jar包名称（不含版本号）',
    service_type_package VARCHAR(500) COMMENT 'ServiceType包路径（来源于XML中<serviceType>节点的package属性）',
    service_id VARCHAR(200) COMMENT 'Service ID（来源于XML中<service>节点的id属性）',
    service_name VARCHAR(200) COMMENT 'Service名称（来源于XML中<service>节点的name属性）',
    service_long_name VARCHAR(500) COMMENT 'Service长名称（来源于XML中<service>节点的longname属性）',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_service_type_id (service_type_id),
    INDEX idx_service_type_kind (service_type_kind),
    INDEX idx_service_type_from_jar (service_type_from_jar),
    INDEX idx_service_id (service_id),
    INDEX idx_service_name (service_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ServiceType文件信息表';
