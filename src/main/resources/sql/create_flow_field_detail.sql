-- ============================================================
-- 执行说明：
--   在每个环境（dev/sit/uat/prod）的 OpenGauss/PostgreSQL 数据库手动执行：
--     psql -h <host> -U <user> -d <db> -f create_flow_field_detail.sql
--   或 IDE 工具（DataGrip/Navicat）打开本文件直接执行。
--
--   表与索引带 IF NOT EXISTS，重复执行幂等。
-- ============================================================
-- Target: OpenGauss / PostgreSQL（BIGSERIAL / TIMESTAMP DEFAULT NOW() / COMMENT ON 均为 PG 方言）
-- flow_field_detail：.flowtrans.xml 的 input/output 字段平铺表
CREATE TABLE IF NOT EXISTS flow_field_detail (
    id              BIGSERIAL    PRIMARY KEY,
    flow_id         VARCHAR(64)  NOT NULL,
    io_type         VARCHAR(8)   NOT NULL,
    field_id        VARCHAR(128) NOT NULL,
    field_type      VARCHAR(256),
    longname        VARCHAR(512),
    ref             VARCHAR(256) NOT NULL,
    required        BOOLEAN,
    multi           BOOLEAN,
    array_flag      BOOLEAN      NOT NULL DEFAULT FALSE,
    source_info     VARCHAR(512),
    create_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_ffd_flow_io_field UNIQUE (flow_id, io_type, field_id)
);

CREATE INDEX IF NOT EXISTS idx_ffd_flow_io ON flow_field_detail (flow_id, io_type);
CREATE INDEX IF NOT EXISTS idx_ffd_ref     ON flow_field_detail (ref);

COMMENT ON TABLE  flow_field_detail            IS '交易字段明细表：.flowtrans.xml input/output 平铺';
COMMENT ON COLUMN flow_field_detail.flow_id    IS '关联 flowtran.id';
COMMENT ON COLUMN flow_field_detail.io_type    IS 'input | output';
COMMENT ON COLUMN flow_field_detail.field_id   IS '<field id="...">';
COMMENT ON COLUMN flow_field_detail.ref        IS 'MDict.X.yyy（无 ref 不入库）';
COMMENT ON COLUMN flow_field_detail.array_flag IS 'true=该 field 出现在 <fields> 容器内';
COMMENT ON COLUMN flow_field_detail.required   IS 'required="true" 属性；无属性时默认 false';
COMMENT ON COLUMN flow_field_detail.multi      IS 'multi="true" 属性；无属性时默认 false';
