package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.ImportHistory;
import com.sunline.dict.mapper.ImportHistoryMapper;
import com.sunline.dict.service.ImportHistoryService;
import org.springframework.stereotype.Service;

/**
 * 导入历史服务实现
 */
@Service
public class ImportHistoryServiceImpl extends ServiceImpl<ImportHistoryMapper, ImportHistory> implements ImportHistoryService {
}

