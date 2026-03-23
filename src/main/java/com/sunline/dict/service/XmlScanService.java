package com.sunline.dict.service;

import java.util.Map;

/**
 * XML 全量扫描服务接口
 * 扫描本地文件夹下的所有目标 XML 文件并批量落库
 * 支持的文件类型：
 *   .pcs.xml / .pbs.xml                → service / service_detail
 *   .pcsImpl.xml / .pbsImpl.xml / ...  → service_impl
 *   .c_schema.xml                      → complex / complex_detail
 *   .pbcb.xml / .pbcp.xml / .pbcc.xml / .pbct.xml → component / component_detail
 *   .tables.xml                        → metadata_tables / metadata_tables_detail / metadata_tables_indexes
 */
public interface XmlScanService {

    /**
     * 扫描指定文件夹下的所有目标 XML 文件并落库
     * @param folderPath 本地绝对路径
     * @return 扫描结果统计，包含各类型文件数量和入库记录数
     */
    Map<String, Object> scanAndSave(String folderPath) throws Exception;
}
