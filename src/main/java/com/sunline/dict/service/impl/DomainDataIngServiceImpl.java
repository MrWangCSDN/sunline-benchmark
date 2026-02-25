package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.DomainDataIng;
import com.sunline.dict.mapper.DomainDataIngMapper;
import com.sunline.dict.service.DomainDataIngService;
import com.sunline.dict.util.PinyinUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 域清单数据服务实现类（在途）
 */
@Service
public class DomainDataIngServiceImpl extends ServiceImpl<DomainDataIngMapper, DomainDataIng> implements DomainDataIngService {
    
    private static final Logger log = LoggerFactory.getLogger(DomainDataIngServiceImpl.class);
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchImport(List<DomainDataIng> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            log.warn("域清单数据（在途）为空，跳过导入");
            return 0;
        }
        
        log.info("开始批量导入域清单数据（在途）: {} 条", dataList.size());

        // 统一生成域英文名称
        for (DomainDataIng domainData : dataList) {
            if (domainData.getChineseName() != null) {
                String generated = PinyinUtils.toUpperCaseWithUnderscore(domainData.getChineseName());
                if (generated != null && !generated.isEmpty()) {
                    domainData.setEnglishName(generated);
                }
            }
        }
        
        // 分批插入，每批500条
        int batchSize = 500;
        int totalCount = 0;
        
        for (int i = 0; i < dataList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, dataList.size());
            List<DomainDataIng> batch = dataList.subList(i, end);
            
            boolean success = this.saveBatch(batch);
            if (success) {
                totalCount += batch.size();
                log.info("域清单（在途）批量插入进度: {}/{}", totalCount, dataList.size());
            } else {
                log.error("域清单（在途）批量插入失败: 第{}批", (i / batchSize) + 1);
                throw new RuntimeException("域清单（在途）批量插入失败");
            }
        }
        
        log.info("域清单数据（在途）导入完成: {} 条", totalCount);
        return totalCount;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean clearAll() {
        log.info("清空域清单数据（在途）");
        return this.remove(null);
    }
}

