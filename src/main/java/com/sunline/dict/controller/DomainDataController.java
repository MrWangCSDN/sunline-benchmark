package com.sunline.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.DomainData;
import com.sunline.dict.service.DomainDataService;
import com.sunline.dict.service.DomainMappingService;
import com.sunline.dict.service.EnumMappingService;
import com.sunline.dict.service.ExcelValidationService;
import com.sunline.dict.config.ExcelImportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 域清单数据Controller
 */
@RestController
@RequestMapping("/api/domain")
public class DomainDataController {
    
    private static final Logger log = LoggerFactory.getLogger(DomainDataController.class);
    
    @Autowired
    private DomainDataService domainDataService;
    
    @Autowired
    private EnumMappingService enumMappingService;
    
    @Autowired
    private DomainMappingService domainMappingService;
    
    @Autowired
    private ExcelValidationService excelValidationService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    /**
     * 分页查询域清单数据
     */
    @GetMapping("/page")
    public Result<Page<DomainData>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String domainNumber,
            @RequestParam(required = false) String domainType,
            @RequestParam(required = false) String domainGroup,
            @RequestParam(required = false) String chineseName,
            @RequestParam(required = false) String englishName,
            @RequestParam(required = false) String englishAbbr,
            @RequestParam(required = false) String domainDefinition,
            @RequestParam(required = false) String dataFormat,
            @RequestParam(required = false) String domainRule,
            @RequestParam(required = false) String valueRange,
            @RequestParam(required = false) String domainSource,
            @RequestParam(required = false) String sourceNumber,
            @RequestParam(required = false) String remark) {
        try {
            Page<DomainData> page = new Page<>(current, size);
            QueryWrapper<DomainData> queryWrapper = new QueryWrapper<>();
            
            // 多字段模糊查询
            if (domainNumber != null && !domainNumber.isEmpty()) {
                queryWrapper.like("domain_number", domainNumber);
            }
            if (domainType != null && !domainType.isEmpty()) {
                queryWrapper.like("domain_type", domainType);
            }
            if (domainGroup != null && !domainGroup.isEmpty()) {
                queryWrapper.like("domain_group", domainGroup);
            }
            if (chineseName != null && !chineseName.isEmpty()) {
                queryWrapper.like("chinese_name", chineseName);
            }
            if (englishName != null && !englishName.isEmpty()) {
                queryWrapper.like("english_name", englishName);
            }
            if (englishAbbr != null && !englishAbbr.isEmpty()) {
                queryWrapper.like("english_abbr", englishAbbr);
            }
            if (domainDefinition != null && !domainDefinition.isEmpty()) {
                queryWrapper.like("domain_definition", domainDefinition);
            }
            if (dataFormat != null && !dataFormat.isEmpty()) {
                queryWrapper.like("data_format", dataFormat);
            }
            if (domainRule != null && !domainRule.isEmpty()) {
                queryWrapper.like("domain_rule", domainRule);
            }
            if (valueRange != null && !valueRange.isEmpty()) {
                queryWrapper.like("value_range", valueRange);
            }
            if (domainSource != null && !domainSource.isEmpty()) {
                queryWrapper.like("domain_source", domainSource);
            }
            if (sourceNumber != null && !sourceNumber.isEmpty()) {
                queryWrapper.like("source_number", sourceNumber);
            }
            if (remark != null && !remark.isEmpty()) {
                queryWrapper.like("remark", remark);
            }
            
            // 按域编号排序
            queryWrapper.orderByAsc("domain_number");
            
            Page<DomainData> result = domainDataService.page(page, queryWrapper);
            
            // 填充映射的域英文简称
            fillMappedEnglishAbbr(result.getRecords());
            
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询域清单失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取域清单总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        try {
            long count = domainDataService.count();
            return Result.success("查询成功", count);
        } catch (Exception e) {
            log.error("查询域清单总数失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 填充映射的域英文简称
     * 1. 如果域组是"代码类"，从枚举映射表中查找
     * 2. 如果域组是"非代码类"，从域清单映射表中查找
     */
    private void fillMappedEnglishAbbr(java.util.List<DomainData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        
        // 一次性加载所有映射到Map中，避免N+1查询问题
        java.util.Map<String, String> enumMappings = enumMappingService.getAllDomainMappings(); // 代码类映射
        java.util.Map<String, String> domainMappings = domainMappingService.getAllDomainMappings(); // 非代码类映射
        
        for (DomainData domain : dataList) {
            
            String chineseName = domain.getChineseName();
            if (chineseName == null || chineseName.trim().isEmpty()) {
                continue;
            }
            
            String domainGroup = domain.getDomainGroup();
            String mappedAbbr = null;
            
            // 根据域组选择不同的映射表
            if ("代码类".equals(domainGroup)) {
                // 代码类：从枚举映射表查找
                mappedAbbr = enumMappings.get(chineseName.trim());
            } else if (domainGroup != null && !domainGroup.trim().isEmpty()) {
                // 非代码类：从域清单映射表查找
                mappedAbbr = domainMappings.get(chineseName.trim());
            }
            
            // 如果找到映射，填充域英文简称
            if (mappedAbbr != null && !mappedAbbr.trim().isEmpty()) {
                domain.setEnglishAbbr(mappedAbbr);
            }
        }
    }
    
    /**
     * 更新（带口令验证）
     */
    @PutMapping("/update-with-password")
    public Result<Boolean> updateWithPassword(
            @RequestBody DomainData domainData,
            @RequestParam("password") String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("更新失败：口令验证失败");
                return Result.error("更新失败：口令错误，请输入正确的6位数字口令");
            }
            
            // 校验数据
            java.util.List<DomainData> allData = domainDataService.list();
            DomainData existingData = allData.stream()
                .filter(d -> d.getId().equals(domainData.getId()))
                .findFirst()
                .orElse(null);
            
            if (existingData == null) {
                return Result.error("数据不存在");
            }
            
            // 创建临时列表用于校验（排除当前记录）
            java.util.List<DomainData> tempList = new java.util.ArrayList<>(allData);
            tempList.removeIf(d -> d.getId().equals(domainData.getId()));
            tempList.add(domainData);
            
            com.sunline.dict.dto.ValidationResult validationResult = 
                excelValidationService.validateDomainData(tempList);
            
            if (validationResult.hasErrors()) {
                StringBuilder errorMsg = new StringBuilder("数据校验失败：");
                for (com.sunline.dict.dto.ValidationResult.ValidationError error : validationResult.getErrors()) {
                    errorMsg.append(error.getFieldName()).append(" ").append(error.getDuplicateValue()).append("; ");
                }
                return Result.error(errorMsg.toString());
            }
            
            boolean result = domainDataService.updateById(domainData);
            return result ? Result.success("更新成功", true) : Result.error("更新失败");
        } catch (Exception e) {
            log.error("更新数据失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除（带口令验证）
     */
    @DeleteMapping("/delete-with-password/{id}")
    public Result<Boolean> deleteWithPassword(
            @PathVariable Long id,
            @RequestParam("password") String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("删除失败：口令验证失败");
                return Result.error("删除失败：口令错误，请输入正确的6位数字口令");
            }
            
            boolean result = domainDataService.removeById(id);
            return result ? Result.success("删除成功", true) : Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除数据失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}

