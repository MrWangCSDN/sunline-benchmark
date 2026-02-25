-- =====================================================
-- 域清单表结构调整脚本
-- 调整为严格的13个字段
-- =====================================================

-- 备份现有表（可选）
-- CREATE TABLE domain_data_backup AS SELECT * FROM domain_data;

-- 删除旧表，重新创建
DROP TABLE IF EXISTS `domain_data`;

CREATE TABLE `domain_data` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `domain_number` INT(11) DEFAULT NULL COMMENT '域编号',
    `domain_type` VARCHAR(50) DEFAULT NULL COMMENT '域类型',
    `domain_group` VARCHAR(50) DEFAULT NULL COMMENT '域组',
    `chinese_name` VARCHAR(200) DEFAULT NULL COMMENT '域中文名称',
    `english_name` VARCHAR(200) DEFAULT NULL COMMENT '域英文名称',
    `english_abbr` VARCHAR(100) DEFAULT NULL COMMENT '域英文简称',
    `domain_definition` VARCHAR(500) DEFAULT NULL COMMENT '域定义',
    `data_format` VARCHAR(100) DEFAULT NULL COMMENT '数据格式',
    `domain_rule` VARCHAR(500) DEFAULT NULL COMMENT '域规则',
    `value_range` VARCHAR(200) DEFAULT NULL COMMENT '取值范围',
    `domain_source` VARCHAR(100) DEFAULT NULL COMMENT '域来源',
    `source_number` VARCHAR(100) DEFAULT NULL COMMENT '来源编号',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_domain_number` (`domain_number`),
    KEY `idx_domain_group` (`domain_group`),
    KEY `idx_chinese_name` (`chinese_name`),
    KEY `idx_english_name` (`english_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='域清单数据表';

-- 说明：
-- 1. 域编号 (domain_number) - INT
-- 2. 域类型 (domain_type) - VARCHAR(50)
-- 3. 域组 (domain_group) - VARCHAR(50)
-- 4. 域中文名称 (chinese_name) - VARCHAR(200)
-- 5. 域英文名称 (english_name) - VARCHAR(200) [新增]
-- 6. 域英文简称 (english_abbr) - VARCHAR(100) [新增]
-- 7. 域定义 (domain_definition) - VARCHAR(500)
-- 8. 数据格式 (data_format) - VARCHAR(100)
-- 9. 域规则 (domain_rule) - VARCHAR(500)
-- 10. 取值范围 (value_range) - VARCHAR(200)
-- 11. 域来源 (domain_source) - VARCHAR(100)
-- 12. 来源编号 (source_number) - VARCHAR(100)
-- 13. 备注 (remark) - VARCHAR(500)

