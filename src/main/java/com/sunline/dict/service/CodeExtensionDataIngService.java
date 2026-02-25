package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.CodeExtensionDataIng;

import java.util.List;

/**
 * 代码扩展清单数据服务接口（在途）
 */
public interface CodeExtensionDataIngService extends IService<CodeExtensionDataIng> {
    
    /**
     * 批量导入代码扩展清单数据
     */
    int batchImport(List<CodeExtensionDataIng> dataList);
    
    /**
     * 清空代码扩展清单数据
     */
    boolean clearAll();
}

