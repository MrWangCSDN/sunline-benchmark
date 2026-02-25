-- 在 service_type_file 表中添加 incorrect_calls 字段，用于记录违规调用列表

ALTER TABLE service_type_file 
ADD COLUMN incorrect_calls VARCHAR(2000) COMMENT '错误调用列表，格式：接口ID(分层-领域)，多个用逗号分割';

-- 说明：
-- incorrect_calls 字段用于记录违反分层调用规则的情况
-- 格式示例：
-- - LnAcctRpymtBcsSvtp(pbcb-comm)
-- - TxPbccSvtp(pbcc-dept)
-- - 多个违规：LnAcctRpymtBcsSvtp(pbcb-comm),TxPbccSvtp(pbcc-dept)

