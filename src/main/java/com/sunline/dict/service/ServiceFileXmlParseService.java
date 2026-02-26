package com.sunline.dict.service;

import java.io.InputStream;
import java.util.Map;

/**
 * 服务 XML 解析服务接口
 * 解析 .pcs.xml / .pbs.xml 文件并保存到 service / service_detail 表
 */
public interface ServiceFileXmlParseService {

    /**
     * 解析服务 XML 字符串并保存
     * @param xmlContent  XML 内容字符串
     * @param sourceInfo  来源标识（文件路径），写入 service.from_jar
     * @param serviceType 服务类型（pcs / pbs），由文件后缀决定
     * @return 结果 map，包含 serviceCount / serviceDetailCount
     */
    Map<String, Object> parseAndSave(String xmlContent, String sourceInfo, String serviceType) throws Exception;

    /**
     * 解析服务 XML 输入流并保存
     */
    Map<String, Object> parseAndSave(InputStream xmlContent, String sourceInfo, String serviceType) throws Exception;

    /**
     * 按来源标识删除 service 及关联的 service_detail（文件删除时调用）
     * @param sourceInfo 与 service.from_jar 一致
     * @return [删除的 service 数, 删除的 service_detail 数]
     */
    int[] deleteBySourceInfo(String sourceInfo);
}
