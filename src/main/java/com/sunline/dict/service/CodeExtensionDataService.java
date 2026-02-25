package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.CodeExtensionData;

import java.util.List;

/**
 * 代码扩展清单数据服务接口
 */
public interface CodeExtensionDataService extends IService<CodeExtensionData> {
    
    /**
     * 批量导入代码扩展清单数据
     */
    int batchImport(List<CodeExtensionData> dataList);
    
    /**
     * 清空代码扩展清单数据
     */
    boolean clearAll();
}

