package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.DomainDataIng;

import java.util.List;

/**
 * 域清单数据服务接口（在途）
 */
public interface DomainDataIngService extends IService<DomainDataIng> {
    
    /**
     * 批量导入域清单数据
     */
    int batchImport(List<DomainDataIng> dataList);
    
    /**
     * 清空域清单数据
     */
    boolean clearAll();
}

