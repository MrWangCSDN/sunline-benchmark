-- =====================================================
-- 多表数据导入 - 数据库表创建脚本
-- =====================================================

-- 1. 域清单表 (严格13个字段)
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

-- 2. 代码扩展清单表
DROP TABLE IF EXISTS `code_extension_data`;
CREATE TABLE `code_extension_data` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `code_domain_number` VARCHAR(50) DEFAULT NULL COMMENT '代码域编号',
    `code_domain_chinese_name` VARCHAR(200) DEFAULT NULL COMMENT '代码域中文名称',
    `code_value` VARCHAR(50) DEFAULT NULL COMMENT '代码取值',
    `value_chinese_name` VARCHAR(200) DEFAULT NULL COMMENT '取值含义中文名称',
    `code_english_name` VARCHAR(200) DEFAULT NULL COMMENT '代码含义英文名称',
    `code_english_abbr` VARCHAR(100) DEFAULT NULL COMMENT '代码含义英文简称',
    `code_description` VARCHAR(500) DEFAULT NULL COMMENT '代码描述',
    `domain_rule` VARCHAR(500) DEFAULT NULL COMMENT '域规则',
    `code_domain_source` VARCHAR(100) DEFAULT NULL COMMENT '代码域来源',
    `source_number` VARCHAR(100) DEFAULT NULL COMMENT '来源编号',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_code_domain_name` (`code_domain_chinese_name`),
    KEY `idx_code_value` (`code_value`),
    KEY `idx_domain_source` (`code_domain_source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码扩展清单数据表';

-- 3. 查看表结构
SHOW CREATE TABLE domain_data;
SHOW CREATE TABLE code_extension_data;

-- 4. 查看表数据
SELECT COUNT(*) AS domain_count FROM domain_data;
SELECT COUNT(*) AS code_extension_count FROM code_extension_data;

