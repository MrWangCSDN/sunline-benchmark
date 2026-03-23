-- service 表新增 incorrect_calls 字段（分层调用规则检查写入违规信息）
ALTER TABLE `service` ADD COLUMN `incorrect_calls` varchar(2000) DEFAULT NULL COMMENT '错误调用列表（违规的分层或领域调用）';

-- component 表新增 incorrect_calls 字段
ALTER TABLE `component` ADD COLUMN `incorrect_calls` varchar(2000) DEFAULT NULL COMMENT '错误调用列表（违规的分层或领域调用）';
