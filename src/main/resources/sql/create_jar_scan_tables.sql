-- 创建交易信息表
CREATE TABLE IF NOT EXISTS flowtran (
    id VARCHAR(100) PRIMARY KEY COMMENT '交易ID',
    longname VARCHAR(500) COMMENT '交易名称',
    package_path VARCHAR(500) COMMENT '包路径',
    txn_mode VARCHAR(50) COMMENT '事务模式',
    from_jar VARCHAR(100) COMMENT '来源jar包',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易信息表';

-- 创建流程步骤表
CREATE TABLE IF NOT EXISTS flow_step (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    flow_id VARCHAR(100) NOT NULL COMMENT '流程ID',
    node_name VARCHAR(500) COMMENT '节点名称',
    node_type VARCHAR(50) COMMENT '节点类型(service/method)',
    step INT COMMENT '步骤顺序',
    node_longname VARCHAR(500) COMMENT '节点长名称',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_flow_node (flow_id, node_name),
    INDEX idx_flow_id (flow_id),
    INDEX idx_step (flow_id, step)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程步骤表';

