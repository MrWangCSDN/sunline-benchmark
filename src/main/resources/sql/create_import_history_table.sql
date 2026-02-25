-- 导入历史表
CREATE TABLE IF NOT EXISTS import_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    import_time DATETIME NOT NULL COMMENT '导入时间',
    file_name VARCHAR(255) COMMENT '文件名',
    operator VARCHAR(100) COMMENT '操作人',
    dict_new_count INT DEFAULT 0 COMMENT '字典新增数量',
    dict_update_count INT DEFAULT 0 COMMENT '字典修改数量',
    dict_delete_count INT DEFAULT 0 COMMENT '字典删除数量',
    domain_new_count INT DEFAULT 0 COMMENT '域清单新增数量',
    domain_update_count INT DEFAULT 0 COMMENT '域清单修改数量',
    domain_delete_count INT DEFAULT 0 COMMENT '域清单删除数量',
    code_new_count INT DEFAULT 0 COMMENT '代码扩展新增数量',
    code_update_count INT DEFAULT 0 COMMENT '代码扩展修改数量',
    code_delete_count INT DEFAULT 0 COMMENT '代码扩展删除数量',
    total_count INT DEFAULT 0 COMMENT '总记录数',
    status VARCHAR(20) DEFAULT 'SUCCESS' COMMENT '导入状态：SUCCESS/FAILED',
    change_description TEXT COMMENT '变更说明',
    remark TEXT COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入历史表';

-- 创建索引
CREATE INDEX idx_import_time ON import_history(import_time DESC);
CREATE INDEX idx_status ON import_history(status);

