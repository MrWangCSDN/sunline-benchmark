package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 新老核心接口文档比对服务接口
 */
public interface NewOldCoreInterfaceCompareService {

    /**
     * 比较新老两版核心接口 Excel 文档
     *
     * @param oldFile       旧版本（基准）
     * @param newFile       新版本（作为输出底本）
     * @param excludeSheets 排除的 sheet 名，逗号分隔；为空 / null 视为"不排除任何 sheet"
     * @return 比较结果信息（fileName、totalSheets、totalChanges 等）
     */
    Map<String, Object> compareFiles(MultipartFile oldFile, MultipartFile newFile, String excludeSheets)
            throws Exception;

    /**
     * 获取结果文件
     */
    File getResultFile(String fileName);
}
