package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.DictData;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 字典数据服务接口
 */
public interface DictDataService extends IService<DictData> {
    
    /**
     * 导入Excel文件
     * @param file Excel文件
     * @param clientId 客户端ID（用于推送进度）
     * @return 导入的数据条数
     */
    int importExcel(MultipartFile file, String clientId) throws IOException;
    
    /**
     * 分页查询
     * @param current 当前页
     * @param size 每页大小
     * @param keyword 关键词
     * @return 分页结果
     */
    Page<DictData> pageQuery(int current, int size, String keyword);
    
    /**
     * 获取所有数据
     * @return 数据列表
     */
    List<DictData> getAllData();
    
    /**
     * 获取所有活跃数据（未删除的）
     * @return 活跃数据列表
     */
    List<DictData> getAllActiveData();
    
    /**
     * 清空表数据
     */
    void truncateTable();
}

