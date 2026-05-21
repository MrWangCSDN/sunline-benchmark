-- 调用关系表 v2：数据库驱动类型判定
-- 类型值域：pbf(交易) / pcs / pbs / pbcb / pbcp / pbcc / pbct / bcc(表DAO)
DROP TABLE IF EXISTS call_relation;
CREATE TABLE call_relation (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,

    caller_id           VARCHAR(150) NOT NULL,
    caller_type         VARCHAR(20)  NOT NULL,
    caller_method       VARCHAR(150),
    caller_longname     VARCHAR(500),
    caller_domain       VARCHAR(20),
    caller_service_id   VARCHAR(200),

    callee_id           VARCHAR(150) NOT NULL,
    callee_type         VARCHAR(20)  NOT NULL,
    callee_method       VARCHAR(150),
    callee_longname     VARCHAR(500),
    callee_domain       VARCHAR(20),
    callee_class        VARCHAR(200),
    callee_service_id   VARCHAR(200),
    callee_service_name VARCHAR(200),

    from_jar            VARCHAR(200),
    is_direct           TINYINT DEFAULT 1,
    cross_domain        TINYINT DEFAULT 0,
    rule_violation      TINYINT DEFAULT 0,
    violation_desc      VARCHAR(500),
    create_time         DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_relation (caller_id, caller_type, caller_method, callee_id, callee_type, callee_method),
    INDEX idx_caller (caller_id, caller_type),
    INDEX idx_callee (callee_id, callee_type),
    INDEX idx_violation (rule_violation),
    INDEX idx_cross_domain (cross_domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 字段注释
ALTER TABLE call_relation
    MODIFY caller_id           VARCHAR(150) NOT NULL    COMMENT '调用方ID（service/component/flowtran的id）',
    MODIFY caller_type         VARCHAR(20)  NOT NULL    COMMENT '调用方类型：pbf/pcs/pbs/pbcb/pbcp/pbcc/pbct',
    MODIFY caller_method       VARCHAR(150)             COMMENT '调用方骨架方法名（Java方法英文名）',
    MODIFY caller_longname     VARCHAR(500)             COMMENT '调用方中文名称（来自service/component/flowtran.longname）',
    MODIFY caller_domain       VARCHAR(20)              COMMENT '调用方领域：comm/dept/sett/loan',
    MODIFY caller_service_id   VARCHAR(200)             COMMENT '调用方方法级service_id（来自service_detail/component_detail）',

    MODIFY callee_id           VARCHAR(150) NOT NULL    COMMENT '被调用方ID（getInstance类名或KxxxDao名称）',
    MODIFY callee_type         VARCHAR(20)  NOT NULL    COMMENT '被调用方类型：pcs/pbs/pbcb/pbcp/pbcc/pbct/bcc',
    MODIFY callee_method       VARCHAR(150)             COMMENT '被调用方方法名',
    MODIFY callee_longname     VARCHAR(500)             COMMENT '被调用方中文名称（bcc暂不填）',
    MODIFY callee_domain       VARCHAR(20)              COMMENT '被调用方领域',
    MODIFY callee_class        VARCHAR(200)             COMMENT '被调用方原始Java类名',
    MODIFY callee_service_id   VARCHAR(200)             COMMENT '被调用方service_id（来自detail表map匹配）',
    MODIFY callee_service_name VARCHAR(200)             COMMENT '被调用方service_name（来自detail表map匹配）',

    MODIFY from_jar            VARCHAR(200)             COMMENT '来源工程名称',
    MODIFY is_direct           TINYINT DEFAULT 1        COMMENT '1=直接调用 0=间接调用',
    MODIFY cross_domain        TINYINT DEFAULT 0        COMMENT '0=同域调用 1=跨域调用',
    MODIFY rule_violation      TINYINT DEFAULT 0        COMMENT '0=合规 1=违反分层或跨域规则',
    MODIFY violation_desc      VARCHAR(500)             COMMENT '违规描述（合规时为NULL）';
