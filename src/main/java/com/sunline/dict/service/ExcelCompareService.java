package com.sunline.dict.service;

import com.sunline.dict.common.CompareMode;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * Excel文档比对服务接口
 */
public interface ExcelCompareService {
    
    /**
     * 比较两个Excel文件（普通模式）
     * @param baseFile 基本版文件
     * @param compareFile 比较版文件
     * @return 比较结果信息
     */
    Map<String, Object> compareExcelFiles(MultipartFile baseFile, MultipartFile compareFile) throws Exception;
    
    /**
     * 比较两个Excel文件（指定模式）
     * @param baseFile 基本版文件
     * @param compareFile 比较版文件
     * @param mode 比对模式
     * @return 比较结果信息
     */
    Map<String, Object> compareExcelFiles(MultipartFile baseFile, MultipartFile compareFile, CompareMode mode) throws Exception;
    
    /**
     * 新老核心接口文档比对模式
     * 只比对每个 sheet 的 J 列及右半边内容
     *
     * @param oldFile        旧版本核心接口 Excel
     * @param newFile        新版本核心接口 Excel（作为输出底本）
     * @param excludeSheets  排除的 sheet 名，逗号分隔；为空 / null 视为"不排除任何 sheet"
     * @return 比较结果信息，包含 fileName, totalSheets, totalChanges 等字段
     */
    Map<String, Object> compareNewOldCoreInterfaces(
            MultipartFile oldFile, MultipartFile newFile, String excludeSheets) throws Exception;

    /**
     * 获取结果文件
     * @param fileName 文件名
     * @return 文件对象
     */
    File getResultFile(String fileName);
}

