-- 复合类型表（对应 .c_schema.xml 文件的 schema 标签）
CREATE TABLE IF NOT EXISTS `complex` (
    `id`           VARCHAR(200)  NOT NULL            COMMENT 'schema.id 属性',
    `longname`     VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.longname 属性',
    `package_path` VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.package 属性',
    `from_jar`     VARCHAR(1000) DEFAULT NULL        COMMENT '来源文件路径（projectName:master:filePath）',
    `create_time`  DATETIME      DEFAULT NULL        COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT NULL        COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_complex_from_jar` (`from_jar`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复合类型表';

-- 复合类型明细表（complexType 标签下的 element 标签，一行一个 element）
CREATE TABLE IF NOT EXISTS `complex_detail` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `complex_id`           VARCHAR(200) NOT NULL               COMMENT '所属 schema.id',
    `complex_pojo_id`      VARCHAR(200) DEFAULT NULL           COMMENT 'complexType.id',
    `complex_pojo_longname`VARCHAR(500) DEFAULT NULL           COMMENT 'complexType.longname',
    `element_id`           VARCHAR(200) DEFAULT NULL           COMMENT 'element.id',
    `element_longname`     VARCHAR(500) DEFAULT NULL           COMMENT 'element.longname',
    `element_required`     VARCHAR(10)  DEFAULT NULL           COMMENT '是否必输（true/false）',
    `element_multi`        VARCHAR(10)  DEFAULT NULL           COMMENT '是否多值（true/false）',
    `element_ref`          VARCHAR(500) DEFAULT NULL           COMMENT '字典来源（element.ref）',
    `element_type`         VARCHAR(200) DEFAULT NULL           COMMENT '来源类型（element.type）',
    `create_time`          DATETIME     DEFAULT NULL           COMMENT '创建时间',
    `update_time`          DATETIME     DEFAULT NULL           COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_complex_detail_complex_id` (`complex_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复合类型明细表';
