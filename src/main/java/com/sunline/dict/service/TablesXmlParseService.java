package com.sunline.dict.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 表定义 XML 解析服务接口
 * 解析 .tables.xml 文件并保存到 metadata_tables / metadata_tables_detail / metadata_tables_indexes 表
 */
public interface TablesXmlParseService {

    /**
     * 解析 .tables.xml 字符串并保存
     * @param xmlContent XML 内容字符串
     * @param sourceInfo 来源标识（文件路径），写入 metadata_tables.from_jar
     * @return 结果 map，包含 tablesCount / detailCount / indexesCount
     */
    Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception;

    /**
     * 解析 .tables.xml 输入流并保存
     */
    Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception;

    /**
     * 按来源标识删除主表及关联明细（文件删除时调用）
     * @param sourceInfo 与 metadata_tables.from_jar 一致
     * @return [删除的 metadata_tables 数, 删除的 detail 数, 删除的 indexes 数]
     */
    int[] deleteBySourceInfo(String sourceInfo);
}
