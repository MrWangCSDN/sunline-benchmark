package com.sunline.dict.service;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 数据库导出服务接口
 */
public interface DatabaseExportService {
    
    /**
     * 按分组导出数据库表到Excel
     * @param environment 环境名称
     * @param groups 分组列表
     * @return 导出结果信息
     */
    Map<String, Object> exportTablesByGroups(String environment, List<String> groups) throws Exception;
    
    /**
     * 获取分组配置
     * @param group 分组名称
     * @return 表列表（换行分隔）
     */
    String getGroupConfig(String group) throws Exception;
    
    /**
     * 保存分组配置
     * @param group 分组名称
     * @param tables 表列表（换行分隔）
     */
    void saveGroupConfig(String group, String tables) throws Exception;
    
    /**
     * 获取结果文件
     * @param fileName 文件名
     * @return 文件对象
     */
    File getResultFile(String fileName);
}
