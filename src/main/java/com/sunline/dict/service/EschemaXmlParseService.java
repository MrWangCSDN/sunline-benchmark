package com.sunline.dict.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 枚举类型 XML 解析服务接口
 * 解析 .e_schema.xml 文件并保存到 eschema / eschema_detail 表
 */
public interface EschemaXmlParseService {

    /**
     * 解析 .e_schema.xml 字符串并保存
     * @param xmlContent XML 内容字符串
     * @param sourceInfo 来源标识（文件路径），写入 eschema.from_jar
     * @return 结果 map，包含 eschemaCount / eschemaDetailCount
     */
    Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception;

    /**
     * 解析 .e_schema.xml 输入流并保存
     */
    Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception;

    /**
     * 按来源标识删除 eschema 及关联的 eschema_detail（文件删除时调用）
     * @param sourceInfo 与 eschema.from_jar 一致
     * @return [删除的 eschema 数, 删除的 eschema_detail 数]
     */
    int[] deleteBySourceInfo(String sourceInfo);
}
