package com.sunline.dict.service;

import com.sunline.dict.dto.MultiSheetChangeReport;
import com.sunline.dict.dto.ValidationResult;

/**
 * 数据导出服务
 */
public interface ExportService {
    
    /**
     * 导出所有数据到Excel
     * @param exportType 导出类型: "standard"(贯标数据), "ing"(在途数据), "all"(全量数据)
     * @return Excel文件的字节数组
     */
    byte[] exportAllData(String exportType) throws Exception;
    
    /**
     * 导出分析报告到Excel
     * @param changeReport 变更报告
     * @return Excel文件的字节数组
     */
    byte[] exportChangeAnalysisReport(MultiSheetChangeReport changeReport) throws Exception;
    
    /**
     * 校验全量数据导出前的数据完整性
     * @return 校验结果
     */
    ValidationResult validateAllDataForExport() throws Exception;
}

