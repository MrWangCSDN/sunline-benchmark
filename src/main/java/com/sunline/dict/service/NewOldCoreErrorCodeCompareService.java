package com.sunline.dict.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

/**
 * 新老核心错误码比对服务接口
 */
public interface NewOldCoreErrorCodeCompareService {

    /**
     * 比较新老两版核心错误码 Excel
     *
     * @param oldFile        旧版本（基准）
     * @param newFile        新版本（作为输出底本）
     * @param firstSheetOnly 文件含多个 sheet 时是否已确认"只比对第一个 sheet"
     * @return 比较结果信息；未确认且存在多个 sheet 时返回 needConfirm 与 sheet 名
     */
    Map<String, Object> compareFiles(MultipartFile oldFile, MultipartFile newFile, boolean firstSheetOnly)
            throws Exception;

    /**
     * 获取结果文件
     */
    File getResultFile(String fileName);
}
