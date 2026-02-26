package com.sunline.dict.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 构件 XML 解析服务接口
 * 解析 .pbcb/.pbcp/.pbcc/.pbct.xml 文件并保存到 component / component_detail 表
 */
public interface ComponentXmlParseService {

    /**
     * 解析构件 XML 字符串并保存
     * @param xmlContent    XML 内容字符串
     * @param sourceInfo    来源标识（文件路径），写入 component.from_jar
     * @param componentType 构件类型（pbcb / pbcp / pbcc / pbct），由文件后缀决定
     * @return 结果 map，包含 componentCount / componentDetailCount
     */
    Map<String, Object> parseAndSave(String xmlContent, String sourceInfo, String componentType) throws Exception;

    /**
     * 解析构件 XML 输入流并保存
     */
    Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo, String componentType) throws Exception;

    /**
     * 按来源标识删除 component 及关联的 component_detail（文件删除时调用）
     * @param sourceInfo 与 component.from_jar 一致
     * @return [删除的 component 数, 删除的 component_detail 数]
     */
    int[] deleteBySourceInfo(String sourceInfo);
}
