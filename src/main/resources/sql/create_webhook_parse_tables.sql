-- ============================================================
-- Webhook 解析落库建表脚本
-- 涵盖以下文件类型：
--   .d_schema.xml  → dict / dict_detail
--   .u_schema.xml  → uschema / uschema_detail
--   .e_schema.xml  → eschema / eschema_detail
--   .tables.xml    → metadata_tables / metadata_tables_detail / metadata_tables_indexes
--   .pbcb/.pbcp/.pbcc/.pbct.xml → component / component_detail
--   .pcs/.pbs.xml  → service / service_detail
--   .pcsImpl/.pbsImpl/.pbcbImpl/.pbcpImpl/.pbccImpl/.pbctImpl.xml → serviceImpl
-- ============================================================


-- ------------------------------------------------------------
-- 字典类型表（对应 .d_schema.xml 文件的 schema 标签）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `dict` (
    `id`           VARCHAR(200)  NOT NULL            COMMENT 'schema.id 属性',
    `longname`     VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.longname 属性',
    `package_path` VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.package 属性',
    `from_jar`     VARCHAR(1000) DEFAULT NULL        COMMENT '来源文件路径（projectName:master:filePath）',
    `create_time`  DATETIME      DEFAULT NULL        COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT NULL        COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dict_from_jar` (`from_jar`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

-- 字典类型明细表（complexType 标签下的 element 标签，一行一个 element）
CREATE TABLE IF NOT EXISTS `dict_detail` (
    `id`                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `dict_id`                   VARCHAR(200) NOT NULL               COMMENT '所属 schema.id',
    `dict_complex_type_id`      VARCHAR(200) DEFAULT NULL           COMMENT 'complexType.id',
    `dict_complex_type_longname`VARCHAR(500) DEFAULT NULL           COMMENT 'complexType.longname',
    `element_id`                VARCHAR(200) DEFAULT NULL           COMMENT 'element.id',
    `element_longname`          VARCHAR(500) DEFAULT NULL           COMMENT 'element.longname',
    `element_dbname`            VARCHAR(200) DEFAULT NULL           COMMENT 'element.dbname（可选）',
    `element_desc`              VARCHAR(500) DEFAULT NULL           COMMENT 'element.desc 描述（可选）',
    `element_version_type`      VARCHAR(100) DEFAULT NULL           COMMENT 'element.versionType（可选）',
    `element_type`              VARCHAR(200) DEFAULT NULL           COMMENT '来源类型（element.type）',
    `create_time`               DATETIME     DEFAULT NULL           COMMENT '创建时间',
    `update_time`               DATETIME     DEFAULT NULL           COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dict_detail_dict_id` (`dict_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型明细表';


-- ------------------------------------------------------------
-- 自定义类型表（对应 .u_schema.xml 文件的 schema 标签）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `uschema` (
    `id`           VARCHAR(200)  NOT NULL            COMMENT 'schema.id 属性',
    `longname`     VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.longname 属性',
    `package_path` VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.package 属性',
    `from_jar`     VARCHAR(1000) DEFAULT NULL        COMMENT '来源文件路径（projectName:master:filePath）',
    `create_time`  DATETIME      DEFAULT NULL        COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT NULL        COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_uschema_from_jar` (`from_jar`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义类型表';

-- 自定义类型明细表（restrictionType 标签，一行一个 restrictionType）
CREATE TABLE IF NOT EXISTS `uschema_detail` (
    `id`                              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `uschema_id`                      VARCHAR(200) NOT NULL               COMMENT '所属 schema.id',
    `restriction_type_id`             VARCHAR(200) DEFAULT NULL           COMMENT 'restrictionType.id',
    `restriction_type_longname`       VARCHAR(500) DEFAULT NULL           COMMENT 'restrictionType.longname',
    `restriction_type_base`           VARCHAR(200) DEFAULT NULL           COMMENT 'restrictionType.base（可选）',
    `restriction_type_min_length`     VARCHAR(100) DEFAULT NULL           COMMENT 'restrictionType.minLength（可选）',
    `restriction_type_max_length`     VARCHAR(100) DEFAULT NULL           COMMENT 'restrictionType.maxLength（可选）',
    `restriction_type_fraction_digits`VARCHAR(100) DEFAULT NULL           COMMENT 'restrictionType.fractionDigits（可选）',
    `restriction_type_db_length`      VARCHAR(100) DEFAULT NULL           COMMENT 'restrictionType.dbLength（可选）',
    `create_time`                     DATETIME     DEFAULT NULL           COMMENT '创建时间',
    `update_time`                     DATETIME     DEFAULT NULL           COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_uschema_detail_uschema_id` (`uschema_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自定义类型明细表';


-- ------------------------------------------------------------
-- 枚举类型表（对应 .e_schema.xml 文件的 schema 标签）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `eschema` (
    `id`           VARCHAR(200)  NOT NULL            COMMENT 'schema.id 属性',
    `longname`     VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.longname 属性',
    `package_path` VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.package 属性',
    `from_jar`     VARCHAR(1000) DEFAULT NULL        COMMENT '来源文件路径（projectName:master:filePath）',
    `create_time`  DATETIME      DEFAULT NULL        COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT NULL        COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_eschema_from_jar` (`from_jar`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='枚举类型表';

-- 枚举类型明细表（restrictionType 下的 enumeration 标签，一行一个枚举项）
CREATE TABLE IF NOT EXISTS `eschema_detail` (
    `id`                        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `eschema_id`                VARCHAR(200) NOT NULL               COMMENT '所属 schema.id',
    `restriction_type_id`       VARCHAR(200) DEFAULT NULL           COMMENT 'restrictionType.id',
    `restriction_type_longname` VARCHAR(500) DEFAULT NULL           COMMENT 'restrictionType.longname（可选）',
    `restriction_type_base`     VARCHAR(200) DEFAULT NULL           COMMENT 'restrictionType.base（可选）',
    `enumeration_id`            VARCHAR(200) DEFAULT NULL           COMMENT 'enumeration.id（可选）',
    `enumeration_value`         VARCHAR(500) DEFAULT NULL           COMMENT 'enumeration.value（可选）',
    `enumeration_longname`      VARCHAR(500) DEFAULT NULL           COMMENT 'enumeration.longname（可选）',
    `create_time`               DATETIME     DEFAULT NULL           COMMENT '创建时间',
    `update_time`               DATETIME     DEFAULT NULL           COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_eschema_detail_eschema_id` (`eschema_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='枚举类型明细表';


-- ------------------------------------------------------------
-- 表定义主表（对应 .tables.xml 文件的 schema 标签）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `metadata_tables` (
    `id`           VARCHAR(200)  NOT NULL            COMMENT 'schema.id 属性',
    `longname`     VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.longname 属性',
    `package_path` VARCHAR(500)  DEFAULT NULL        COMMENT 'schema.package 属性',
    `from_jar`     VARCHAR(1000) DEFAULT NULL        COMMENT '来源文件路径（projectName:master:filePath）',
    `create_time`  DATETIME      DEFAULT NULL        COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT NULL        COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_metadata_tables_from_jar` (`from_jar`(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表定义主表';

-- 表定义字段明细表（table/fields/field 标签，一行一个字段）
CREATE TABLE IF NOT EXISTS `metadata_tables_detail` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `metadata_tables_id` VARCHAR(200) NOT NULL               COMMENT '所属 schema.id',
    `table_id`           VARCHAR(200) DEFAULT NULL           COMMENT 'table.id',
    `table_name`         VARCHAR(200) DEFAULT NULL           COMMENT 'table.name',
    `table_longname`     VARCHAR(500) DEFAULT NULL           COMMENT 'table.longname（可选）',
    `table_extension`    VARCHAR(200) DEFAULT NULL           COMMENT 'table.extension（可选）',
    `field_id`           VARCHAR(200) DEFAULT NULL           COMMENT 'field.id（可选）',
    `field_dbname`       VARCHAR(200) DEFAULT NULL           COMMENT 'field.dyname（可选）',
    `field_longname`     VARCHAR(500) DEFAULT NULL           COMMENT 'field.longname（可选）',
    `field_type`         VARCHAR(200) DEFAULT NULL           COMMENT 'field.type（可选）',
    `field_nullable`     VARCHAR(20)  DEFAULT NULL           COMMENT 'field.nullable（可选）',
    `field_primarykey`   VARCHAR(20)  DEFAULT NULL           COMMENT 'field.primarykey（可选）',
    `field_ref`          VARCHAR(200) DEFAULT NULL           COMMENT 'field.ref（可选）',
    `create_time`        DATETIME     DEFAULT NULL           COMMENT '创建时间',
    `update_time`        DATETIME     DEFAULT NULL           COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_metadata_tables_detail_pid` (`metadata_tables_id`),
    KEY `idx_metadata_tables_detail_tid` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表定义字段明细表';

-- 表定义索引表（table/odbindexes/index 与 table/indexes/index 标签，一行一个索引项）
CREATE TABLE IF NOT EXISTS `metadata_tables_indexes` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `metadata_tables_id`  VARCHAR(200) NOT NULL               COMMENT '所属 schema.id',
    `table_id`            VARCHAR(200) DEFAULT NULL           COMMENT 'table.id',
    `odbindex_id`         VARCHAR(200) DEFAULT NULL           COMMENT 'odbindexes/index.id（可选）',
    `odbindex_type`       VARCHAR(200) DEFAULT NULL           COMMENT 'odbindexes/index.type（可选）',
    `odbindex_fields`     VARCHAR(500) DEFAULT NULL           COMMENT 'odbindexes/index.fields（可选）',
    `odbindex_operate`    VARCHAR(200) DEFAULT NULL           COMMENT 'odbindexes/index.operate（可选）',
    `index_id`            VARCHAR(200) DEFAULT NULL           COMMENT 'indexes/index.id（可选）',
    `index_type`          VARCHAR(200) DEFAULT NULL           COMMENT 'indexes/index.type（可选）',
    `index_fields`        VARCHAR(500) DEFAULT NULL           COMMENT 'indexes/index.fields（可选）',
    `create_time`         DATETIME     DEFAULT NULL           COMMENT '创建时间',
    `update_time`         DATETIME     DEFAULT NULL           COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_metadata_tables_indexes_pid` (`metadata_tables_id`),
    KEY `idx_metadata_tables_indexes_tid` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表定义索引表';


-- ------------------------------------------------------------
-- 构件主表（对应 .pbcb/.pbcp/.pbcc/.pbct.xml 文件的 serviceType 标签）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `component` (
    `id`             VARCHAR(200)  NOT NULL            COMMENT 'serviceType.id 属性',
    `longname`       VARCHAR(500)  DEFAULT NULL        COMMENT 'serviceType.longname 属性',
    `package_path`   VARCHAR(500)  DEFAULT NULL        COMMENT 'serviceType.package 属性',
    `kind`           VARCHAR(200)  DEFAULT NULL        COMMENT 'serviceType.kind（可选）',
    `component_type` VARCHAR(20)   DEFAULT NULL        COMMENT '构件类型：pbcb/pbcp/pbcc/pbct',
    `from_jar`       VARCHAR(1000) DEFAULT NULL        COMMENT '来源文件路径（projectName:master:filePath）',
    `create_time`    DATETIME      DEFAULT NULL        COMMENT '创建时间',
    `update_time`    DATETIME      DEFAULT NULL        COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_component_from_jar` (`from_jar`(255)),
    KEY `idx_component_type` (`component_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='构件主表';

-- 构件明细表（serviceType/service/interface 下的 input/output field，一行一个 field）
CREATE TABLE IF NOT EXISTS `component_detail` (
    `id`                             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `component_id`                   VARCHAR(200) NOT NULL               COMMENT '所属 serviceType.id',
    `service_id`                     VARCHAR(200) DEFAULT NULL           COMMENT 'service.id',
    `service_name`                   VARCHAR(200) DEFAULT NULL           COMMENT 'service.name（可选）',
    `service_longname`               VARCHAR(500) DEFAULT NULL           COMMENT 'service.longname（可选）',
    `interface_input_field_id`       VARCHAR(200) DEFAULT NULL           COMMENT 'input/field.id（可选）',
    `interface_input_field_longname` VARCHAR(500) DEFAULT NULL           COMMENT 'input/field.longname（可选）',
    `interface_input_field_type`     VARCHAR(200) DEFAULT NULL           COMMENT 'input/field.type（可选）',
    `interface_input_field_required` VARCHAR(20)  DEFAULT NULL           COMMENT 'input/field.required（可选）',
    `interface_input_field_multi`    VARCHAR(20)  DEFAULT NULL           COMMENT 'input/field.multi（可选）',
    `interface_output_field_id`      VARCHAR(200) DEFAULT NULL           COMMENT 'output/field.id（可选）',
    `interface_output_field_longname`VARCHAR(500) DEFAULT NULL           COMMENT 'output/field.longname（可选）',
    `interface_output_field_type`    VARCHAR(200) DEFAULT NULL           COMMENT 'output/field.type（可选）',
    `interface_output_field_required`VARCHAR(20)  DEFAULT NULL           COMMENT 'output/field.required（可选）',
    `interface_output_field_multi`   VARCHAR(20)  DEFAULT NULL           COMMENT 'output/field.multi（可选）',
    `create_time`                    DATETIME     DEFAULT NULL           COMMENT '创建时间',
    `update_time`                    DATETIME     DEFAULT NULL           COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_component_detail_component_id` (`component_id`),
    KEY `idx_component_detail_service_id` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='构件明细表';


-- ------------------------------------------------------------
-- 服务主表（对应 .pcs.xml / .pbs.xml 文件的 serviceType 标签）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `service` (
    `id`           VARCHAR(200)  NOT NULL            COMMENT 'serviceType.id 属性',
    `longname`     VARCHAR(500)  DEFAULT NULL        COMMENT 'serviceType.longname 属性',
    `package_path` VARCHAR(500)  DEFAULT NULL        COMMENT 'serviceType.package 属性',
    `kind`         VARCHAR(200)  DEFAULT NULL        COMMENT 'serviceType.kind（可选）',
    `out_bound`    VARCHAR(200)  DEFAULT NULL        COMMENT 'serviceType.outBound（可选）',
    `service_type` VARCHAR(20)   DEFAULT NULL        COMMENT '服务类型：pcs/pbs',
    `from_jar`     VARCHAR(1000) DEFAULT NULL        COMMENT '来源文件路径（projectName:master:filePath）',
    `create_time`  DATETIME      DEFAULT NULL        COMMENT '创建时间',
    `update_time`  DATETIME      DEFAULT NULL        COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_from_jar` (`from_jar`(255)),
    KEY `idx_service_type` (`service_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务主表';

-- 服务明细表（serviceType/service/interface 下的 input/output field，一行一个 field）
CREATE TABLE IF NOT EXISTS `service_detail` (
    `id`                              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `service_type_id`                 VARCHAR(200) NOT NULL               COMMENT '所属 serviceType.id',
    `service_id`                      VARCHAR(200) DEFAULT NULL           COMMENT 'service.id',
    `service_name`                    VARCHAR(200) DEFAULT NULL           COMMENT 'service.name（可选）',
    `service_longname`                VARCHAR(500) DEFAULT NULL           COMMENT 'service.longname（可选）',
    `interface_input_field_id`        VARCHAR(200) DEFAULT NULL           COMMENT 'input/field.id（可选）',
    `interface_input_field_longname`  VARCHAR(500) DEFAULT NULL           COMMENT 'input/field.longname（可选）',
    `interface_input_field_type`      VARCHAR(200) DEFAULT NULL           COMMENT 'input/field.type（可选）',
    `interface_input_field_required`  VARCHAR(20)  DEFAULT NULL           COMMENT 'input/field.required（可选）',
    `interface_input_field_multi`     VARCHAR(20)  DEFAULT NULL           COMMENT 'input/field.multi（可选）',
    `interface_output_field_id`       VARCHAR(200) DEFAULT NULL           COMMENT 'output/field.id（可选）',
    `interface_output_field_longname` VARCHAR(500) DEFAULT NULL           COMMENT 'output/field.longname（可选）',
    `interface_output_field_type`     VARCHAR(200) DEFAULT NULL           COMMENT 'output/field.type（可选）',
    `interface_output_field_required` VARCHAR(20)  DEFAULT NULL           COMMENT 'output/field.required（可选）',
    `interface_output_field_multi`    VARCHAR(20)  DEFAULT NULL           COMMENT 'output/field.multi（可选）',
    `create_time`                     DATETIME     DEFAULT NULL           COMMENT '创建时间',
    `update_time`                     DATETIME     DEFAULT NULL           COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_detail_service_type_id` (`service_type_id`),
    KEY `idx_service_detail_service_id` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务明细表';


-- ------------------------------------------------------------
-- 服务实现表
-- 对应 .pcsImpl/.pbsImpl/.pbcbImpl/.pbcpImpl/.pbccImpl/.pbctImpl.xml
-- 文件的 serviceImpl 标签（仅主表，无明细）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `serviceImpl` (
    `id`                VARCHAR(200)  NOT NULL            COMMENT 'serviceImpl.id 属性',
    `longname`          VARCHAR(500)  DEFAULT NULL        COMMENT 'serviceImpl.longname（可选）',
    `package_path`      VARCHAR(500)  DEFAULT NULL        COMMENT 'serviceImpl.package（可选）',
    `service_type`      VARCHAR(200)  DEFAULT NULL        COMMENT 'serviceImpl.serviceType（可选）',
    `service_impl_type` VARCHAR(50)   DEFAULT NULL        COMMENT '服务实现类型：pcsImpl/pbsImpl/pbcbImpl/pbcpImpl/pbccImpl/pbctImpl',
    `from_jar`          VARCHAR(1000) DEFAULT NULL        COMMENT '来源文件路径（projectName:master:filePath）',
    `create_time`       DATETIME      DEFAULT NULL        COMMENT '创建时间',
    `update_time`       DATETIME      DEFAULT NULL        COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_impl_from_jar` (`from_jar`(255)),
    KEY `idx_service_impl_type` (`service_impl_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务实现表';
