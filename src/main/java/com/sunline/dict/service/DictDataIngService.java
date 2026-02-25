package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.DictDataIng;

import java.util.List;

/**
 * 字典数据服务接口（在途）
 */
public interface DictDataIngService extends IService<DictDataIng> {
    
    /**
     * 分页查询
     */
    Page<DictDataIng> pageQuery(int current, int size, String keyword);
    
    /**
     * 获取所有数据
     */
    List<DictDataIng> getAllData();
    
    /**
     * 获取所有活跃数据（未删除的）
     */
    List<DictDataIng> getAllActiveData();
}

