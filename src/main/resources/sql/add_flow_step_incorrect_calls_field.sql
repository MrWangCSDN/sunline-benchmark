-- 在 flow_step 表中添加 incorrect_calls 字段，用于记录违规调用列表

ALTER TABLE flow_step 
ADD COLUMN incorrect_calls VARCHAR(1000) COMMENT '错误调用列表，多个用逗号分割，记录违规的分层或领域';

-- 说明：
-- incorrect_calls 字段用于记录违反分层调用规则的情况
-- 例如：
-- - 违规调用了pbcb层，记录：pbcb
-- - 违规跨领域调用了comm领域，记录：comm
-- - 多个违规用逗号分割：pbcb,comm

