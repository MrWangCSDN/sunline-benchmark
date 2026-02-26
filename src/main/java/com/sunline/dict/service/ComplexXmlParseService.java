package com.sunline.dict.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 复合类型 XML 解析服务接口
 * 解析 .c_schema.xml 文件并保存到 complex / complex_detail 表
 */
public interface ComplexXmlParseService {

    /**
     * 解析 .c_schema.xml 字符串并保存
     * @param xmlContent XML 内容字符串
     * @param sourceInfo 来源标识（文件路径），写入 complex.from_jar
     * @return 结果 map，包含 complexCount / complexDetailCount
     */
    Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception;

    /**
     * 解析 .c_schema.xml 输入流并保存
     */
    Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception;

    /**
     * 按来源标识删除 complex 及关联的 complex_detail（文件删除时调用）
     * @param sourceInfo 与 complex.from_jar 一致
     * @return [删除的 complex 数, 删除的 complex_detail 数]
     */
    int[] deleteBySourceInfo(String sourceInfo);
}
