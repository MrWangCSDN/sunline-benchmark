-- 为flow_step表添加文件信息字段
ALTER TABLE flow_step 
ADD COLUMN file_name VARCHAR(500) COMMENT '文件名（service类型时，匹配到的文件名）',
ADD COLUMN file_path VARCHAR(1000) COMMENT '文件路径（service类型时，匹配到的文件路径）',
ADD COLUMN file_jar_name VARCHAR(100) COMMENT '文件来源jar包（service类型时，匹配到的jar包名称）';

