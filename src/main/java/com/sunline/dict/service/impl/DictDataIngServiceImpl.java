package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.DictDataIng;
import com.sunline.dict.mapper.DictDataIngMapper;
import com.sunline.dict.service.DictDataIngService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 字典数据服务实现类（在途）
 */
@Service
public class DictDataIngServiceImpl extends ServiceImpl<DictDataIngMapper, DictDataIng> implements DictDataIngService {
    
    private static final Logger log = LoggerFactory.getLogger(DictDataIngServiceImpl.class);
    
    @Override
    public Page<DictDataIng> pageQuery(int current, int size, String keyword) {
        Page<DictDataIng> page = new Page<>(current, size);
        LambdaQueryWrapper<DictDataIng> wrapper = new LambdaQueryWrapper<>();
        
        if (StringUtils.hasText(keyword)) {
            // 解析keyword，格式为 field1:value1|field2:value2
            String[] parts = keyword.split("\\|");
            for (String part : parts) {
                if (part.contains(":")) {
                    String[] kv = part.split(":", 2);
                    if (kv.length == 2) {
                        String field = kv[0].trim();
                        String value = kv[1].trim();
                        
                        switch (field) {
                            case "dataItemCode":
                                wrapper.like(DictDataIng::getDataItemCode, value);
                                break;
                            case "englishAbbr":
                                wrapper.like(DictDataIng::getEnglishAbbr, value);
                                break;
                            case "englishName":
                                wrapper.like(DictDataIng::getEnglishName, value);
                                break;
                            case "chineseName":
                                wrapper.like(DictDataIng::getChineseName, value);
                                break;
                            case "dictAttr":
                                wrapper.like(DictDataIng::getDictAttr, value);
                                break;
                            case "domainChineseName":
                                wrapper.like(DictDataIng::getDomainChineseName, value);
                                break;
                            case "dataType":
                                wrapper.like(DictDataIng::getDataType, value);
                                break;
                            case "dataFormat":
                                wrapper.like(DictDataIng::getDataFormat, value);
                                break;
                            case "valueRange":
                                wrapper.like(DictDataIng::getValueRange, value);
                                break;
                            case "javaEsfName":
                                wrapper.like(DictDataIng::getJavaEsfName, value);
                                break;
                            case "esfDataFormat":
                                wrapper.like(DictDataIng::getEsfDataFormat, value);
                                break;
                            case "gaussdbDataFormat":
                                wrapper.like(DictDataIng::getGaussdbDataFormat, value);
                                break;
                            case "goldendbDataFormat":
                                wrapper.like(DictDataIng::getGoldendbDataFormat, value);
                                break;
                        }
                    }
                }
            }
        }
        
        wrapper.orderByAsc(DictDataIng::getSortOrder);
        return this.page(page, wrapper);
    }
    
    @Override
    public List<DictDataIng> getAllData() {
        LambdaQueryWrapper<DictDataIng> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DictDataIng::getSortOrder);
        return this.list(wrapper);
    }
    
    @Override
    public List<DictDataIng> getAllActiveData() {
        LambdaQueryWrapper<DictDataIng> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictDataIng::getIsDeleted, 0).or().isNull(DictDataIng::getIsDeleted);
        wrapper.orderByAsc(DictDataIng::getSortOrder);
        return this.list(wrapper);
    }
}

