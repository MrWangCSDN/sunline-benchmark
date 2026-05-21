package com.sunline.dict.service;

/**
 * 文件模版导出服务
 */
public interface FileTemplateExportService {

    /**
     * 导出文件模版工作簿
     *
     * @param scope 导出范围：all/deposit/loan/public/settlement
     * @return Excel 二进制内容
     */
    byte[] exportTemplateWorkbook(String scope) throws Exception;
}
