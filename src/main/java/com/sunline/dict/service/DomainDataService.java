package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.DomainData;

import java.util.List;

/**
 * 域清单数据服务接口
 */
public interface DomainDataService extends IService<DomainData> {
    
    /**
     * 批量导入域清单数据
     */
    int batchImport(List<DomainData> dataList);
    
    /**
     * 清空域清单数据
     */
    boolean clearAll();
}

