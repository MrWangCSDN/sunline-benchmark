-- ============================================================
-- 执行说明：
--   在每个环境（dev/sit/uat/prod）的 MariaDB/MySQL 数据库手动执行：
--     mysql -h <host> -u <user> -p <db> < create_flow_field_detail.sql
--   或 IDE 工具（DataGrip/Navicat）打开本文件直接执行。
--
--   表与索引带 IF NOT EXISTS，重复执行幂等。
-- ============================================================
-- Target: MariaDB / MySQL（沿用项目既有 sibling SQL 风格：InnoDB / utf8mb4 /
--         BIGINT AUTO_INCREMENT / DATETIME ON UPDATE / 列内联 COMMENT）
-- flow_field_detail：.flowtrans.xml 的 input/output 字段平铺表
CREATE TABLE IF NOT EXISTS flow_field_detail (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT             COMMENT '主键ID',
    flow_id         VARCHAR(64)  NOT NULL                                COMMENT '关联 flowtran.id',
    io_type         VARCHAR(8)   NOT NULL                                COMMENT 'input | output',
    field_id        VARCHAR(128) NOT NULL                                COMMENT '<field id="...">',
    field_type      VARCHAR(256)                                         COMMENT 'type 属性',
    longname        VARCHAR(512)                                         COMMENT '中文描述',
    ref             VARCHAR(256) NOT NULL                                COMMENT 'MDict.X.yyy（无 ref 不入库）',
    required        TINYINT(1)                                           COMMENT 'required="true" 属性；无属性时默认 0',
    multi           TINYINT(1)                                           COMMENT 'multi="true" 属性；无属性时默认 0',
    array_flag      TINYINT(1)   NOT NULL DEFAULT 0                      COMMENT 'true=该 field 出现在 <fields> 容器内',
    source_info     VARCHAR(512)                                         COMMENT '来源标识（jar 包名或文件路径）',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP      COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                  ON UPDATE CURRENT_TIMESTAMP            COMMENT '更新时间',
    UNIQUE KEY uk_ffd_flow_io_field (flow_id, io_type, field_id),
    INDEX idx_ffd_flow_io (flow_id, io_type),
    INDEX idx_ffd_ref     (ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易字段明细表：.flowtrans.xml input/output 字段平铺';
