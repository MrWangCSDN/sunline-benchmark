package com.sunline.dict.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 服务实现 XML 解析服务接口
 * 解析 .pcsImpl/.pbsImpl/.pbcbImpl/.pbcpImpl/.pbccImpl/.pbctImpl.xml 文件并保存到 serviceImpl 表
 */
public interface ServiceImplXmlParseService {

    /**
     * 解析服务实现 XML 字符串并保存
     * @param xmlContent      XML 内容字符串
     * @param sourceInfo      来源标识（文件路径），写入 serviceImpl.from_jar
     * @param serviceImplType 服务实现类型（pcsImpl/pbsImpl/...），由文件后缀决定
     * @return 结果 map，包含 serviceImplCount
     */
    Map<String, Object> parseAndSave(String xmlContent, String sourceInfo, String serviceImplType) throws Exception;

    /**
     * 解析服务实现 XML 输入流并保存
     */
    Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo, String serviceImplType) throws Exception;

    /**
     * 按来源标识删除 serviceImpl 记录（文件删除时调用）
     * @param sourceInfo 与 serviceImpl.from_jar 一致
     * @return 删除的记录数
     */
    int deleteBySourceInfo(String sourceInfo);
}
