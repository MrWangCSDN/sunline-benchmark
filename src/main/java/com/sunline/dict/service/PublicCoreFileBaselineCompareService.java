package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 对公核心文件基线比对服务接口
 */
public interface PublicCoreFileBaselineCompareService {

    /**
     * 比较两个对公核心文件基线 Excel
     *
     * @param baselineFile 基线文件（比对基准）
     * @param compareFile  对比文件（作为输出底本）
     * @return 比较结果信息（fileName、totalRows、totalChanges 等）
     */
    Map<String, Object> compareFiles(MultipartFile baselineFile, MultipartFile compareFile)
            throws Exception;

    /**
     * 获取结果文件
     */
    File getResultFile(String fileName);
}
