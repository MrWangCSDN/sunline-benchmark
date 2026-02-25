-- 创建域清单映射关系表
CREATE TABLE IF NOT EXISTS domain_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    domain_chinese_name VARCHAR(200) NOT NULL COMMENT '域中文名称',
    domain_english_abbr VARCHAR(200) COMMENT '域英文简称',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_domain_chinese_name (domain_chinese_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='域清单映射关系表（非代码类）';

-- 创建索引
CREATE INDEX idx_domain_chinese_name ON domain_mapping(domain_chinese_name);

