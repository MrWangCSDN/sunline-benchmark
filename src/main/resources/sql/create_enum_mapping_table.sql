-- 创建枚举映射关系表
CREATE TABLE IF NOT EXISTS enum_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    domain_english_abbr VARCHAR(200) NOT NULL COMMENT '域英文简称（枚举ID）',
    enum_field_id VARCHAR(200) NOT NULL COMMENT '枚举字段ID',
    domain_chinese_name VARCHAR(200) NOT NULL COMMENT '域中文名称',
    code_value VARCHAR(200) NOT NULL COMMENT '代码取值',
    value_chinese_name VARCHAR(200) COMMENT '取值含义中文名称',
    code_description VARCHAR(500) COMMENT '代码描述',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_domain_code_value (domain_chinese_name, code_value),
    UNIQUE KEY uk_enum_field (domain_english_abbr, enum_field_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='枚举映射关系表';

-- 创建普通索引
CREATE INDEX idx_domain_chinese_name ON enum_mapping(domain_chinese_name);
CREATE INDEX idx_domain_english_abbr ON enum_mapping(domain_english_abbr);
CREATE INDEX idx_code_value ON enum_mapping(code_value);
CREATE INDEX idx_enum_field_id ON enum_mapping(enum_field_id);

