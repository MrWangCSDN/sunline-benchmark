-- 创建分层调用规则表
CREATE TABLE IF NOT EXISTS layer_call_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    rule_name VARCHAR(200) NOT NULL COMMENT '规则名称',
    rule_description VARCHAR(500) COMMENT '规则描述',
    status TINYINT(1) DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_rule_name (rule_name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分层调用规则表';

-- 创建分层调用规则项表
CREATE TABLE IF NOT EXISTS layer_call_rule_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    rule_id BIGINT NOT NULL COMMENT '规则ID',
    caller_layer VARCHAR(50) NOT NULL COMMENT '调用方层级',
    callee_layer VARCHAR(50) NOT NULL COMMENT '被调用方层级',
    domain_constraint VARCHAR(50) NOT NULL COMMENT '领域约束：same_domain-本领域，cross_domain-可跨领域',
    item_order INT DEFAULT 0 COMMENT '规则项排序',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_rule_id (rule_id),
    INDEX idx_caller_layer (caller_layer),
    INDEX idx_callee_layer (callee_layer),
    FOREIGN KEY (rule_id) REFERENCES layer_call_rule(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分层调用规则项表';

