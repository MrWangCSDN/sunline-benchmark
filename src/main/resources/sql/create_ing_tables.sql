-- =====================================================
-- 在途数据表创建脚本
-- =====================================================

-- 1. 字典技术衍生表（在途）
DROP TABLE IF EXISTS `dict_data_ing`;
CREATE TABLE `dict_data_ing` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `sort_order` INT(11) DEFAULT NULL COMMENT '排序号（Excel中的行号，用于保持顺序）',
    `version_id` BIGINT(20) DEFAULT NULL COMMENT '版本ID',
    `data_hash` VARCHAR(64) DEFAULT NULL COMMENT '数据MD5值',
    `change_type` VARCHAR(20) DEFAULT NULL COMMENT '变更类型：NEW/UPDATE/DELETE/UNCHANGED',
    `is_deleted` INT(1) DEFAULT 0 COMMENT '是否已删除：0-否，1-是',
    `deleted_at` DATETIME DEFAULT NULL COMMENT '删除时间',
    `data_item_code` VARCHAR(50) NOT NULL COMMENT '数据项编号',
    `english_abbr` VARCHAR(200) DEFAULT NULL COMMENT '英文简称',
    `english_name` VARCHAR(200) DEFAULT NULL COMMENT '英文名称',
    `chinese_name` VARCHAR(200) DEFAULT NULL COMMENT '中文名称',
    `dict_attr` VARCHAR(50) DEFAULT NULL COMMENT '字典属性',
    `domain_chinese_name` VARCHAR(200) DEFAULT NULL COMMENT '域中文名称',
    `data_type` VARCHAR(50) DEFAULT NULL COMMENT '数据类型',
    `data_format` VARCHAR(100) DEFAULT NULL COMMENT '数据格式',
    `value_range` VARCHAR(200) DEFAULT NULL COMMENT '取值范围',
    `java_esf_name` VARCHAR(200) DEFAULT NULL COMMENT 'JAVA/ESF规范命名',
    `esf_data_format` VARCHAR(100) DEFAULT NULL COMMENT 'ESF数据格式',
    `gaussdb_data_format` VARCHAR(100) DEFAULT NULL COMMENT 'GaussDB数据格式',
    `goldendb_data_format` VARCHAR(100) DEFAULT NULL COMMENT 'GoldenDB数据格式',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_sort_order` (`sort_order`),
    KEY `idx_data_item_code` (`data_item_code`),
    KEY `idx_english_abbr` (`english_abbr`),
    KEY `idx_chinese_name` (`chinese_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典技术衍生表（在途）';

-- 2. 域清单表（在途）
DROP TABLE IF EXISTS `domain_data_ing`;
CREATE TABLE `domain_data_ing` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='域清单数据表（在途）';

-- 3. 代码扩展清单表（在途）
DROP TABLE IF EXISTS `code_extension_data_ing`;
CREATE TABLE `code_extension_data_ing` (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码扩展清单数据表（在途）';

