package com.sunline.dict.service;

import java.io.InputStream;
import java.util.Map;

/**
 * FlowTrans XML解析服务接口
 */
public interface FlowXmlParseService {
    
    /**
     * 解析flowtrans.xml文件并保存到数据库
     * @param xmlContent XML文件内容（输入流）
     * @param sourceInfo 来源信息（例如：项目名+分支+文件路径）
     * @return 解析结果（包含flowtran数量和flow_step数量）
     */
    Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo) throws Exception;
    
    /**
     * 解析flowtrans.xml文件并保存到数据库
     * @param xmlContent XML文件内容（字符串）
     * @param sourceInfo 来源信息
     * @return 解析结果
     */
    Map<String, Object> parseAndSave(String xmlContent, String sourceInfo) throws Exception;
}
