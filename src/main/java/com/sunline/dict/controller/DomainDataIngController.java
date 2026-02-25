package com.sunline.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.DomainDataIng;
import com.sunline.dict.entity.DomainData;
import com.sunline.dict.service.DomainDataIngService;
import com.sunline.dict.service.DomainDataService;
import com.sunline.dict.service.ExcelValidationService;
import com.sunline.dict.config.ExcelImportConfig;
import com.sunline.dict.dto.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 域清单数据Controller（在途）
 */
@RestController
@RequestMapping("/api/domain-ing")
public class DomainDataIngController {
    
    private static final Logger log = LoggerFactory.getLogger(DomainDataIngController.class);
    
    @Autowired
    private DomainDataIngService domainDataIngService;
    
    @Autowired
    private DomainDataService domainDataService;
    
    @Autowired
    private ExcelValidationService excelValidationService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    /**
     * 分页查询域清单数据
     */
    @GetMapping("/page")
    public Result<Page<DomainDataIng>> page(
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
            Page<DomainDataIng> page = new Page<>(current, size);
            QueryWrapper<DomainDataIng> queryWrapper = new QueryWrapper<>();
            
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
            
            Page<DomainDataIng> result = domainDataIngService.page(page, queryWrapper);
            
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询域清单失败（在途）", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取域清单总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        try {
            long count = domainDataIngService.count();
            return Result.success("查询成功", count);
        } catch (Exception e) {
            log.error("查询域清单总数失败（在途）", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新（带口令验证）
     */
    @PutMapping("/update-with-password")
    public Result<Boolean> updateWithPassword(
            @RequestBody DomainDataIng domainDataIng,
            @RequestParam("password") String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("更新失败（在途）：口令验证失败");
                return Result.error("更新失败：口令错误，请输入正确的6位数字口令");
            }
            
            // 校验数据
            DomainDataIng existingData = domainDataIngService.getById(domainDataIng.getId());
            if (existingData == null) {
                return Result.error("数据不存在");
            }
            
            // 转换为DomainData进行校验
            DomainData domainData = convertToDomainData(domainDataIng);
            
            // 获取所有贯标数据
            java.util.List<DomainData> standardDataList = domainDataService.list();
            
            // 获取所有在途数据（排除当前记录）
            java.util.List<DomainDataIng> ingDataList = domainDataIngService.list();
            ingDataList.removeIf(d -> d.getId().equals(domainDataIng.getId()));
            
            // 检查是否与贯标数据重复
            java.util.Set<String> standardChineseNameSet = standardDataList.stream()
                .map(DomainData::getChineseName)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
            
            // 检查是否与在途数据重复
            java.util.Set<String> ingChineseNameSet = ingDataList.stream()
                .map(DomainDataIng::getChineseName)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
            
            ValidationResult validationResult = new ValidationResult();
            
            // 检查域中文名称
            if (domainData.getChineseName() != null && !domainData.getChineseName().trim().isEmpty()) {
                String chineseName = domainData.getChineseName().trim();
                if (standardChineseNameSet.contains(chineseName)) {
                    validationResult.addError("域清单（在途）", "域中文名称", "duplicate_with_standard",
                        java.util.Collections.singletonList(1), chineseName + "（与贯标数据重复）");
                } else if (ingChineseNameSet.contains(chineseName)) {
                    validationResult.addError("域清单（在途）", "域中文名称", "duplicate_with_ing",
                        java.util.Collections.singletonList(1), chineseName + "（与在途数据重复）");
                }
            }
            
            if (validationResult.hasErrors()) {
                StringBuilder errorMsg = new StringBuilder("数据校验失败：");
                for (ValidationResult.ValidationError error : validationResult.getErrors()) {
                    errorMsg.append(error.getFieldName()).append(" ").append(error.getDuplicateValue()).append("; ");
                }
                return Result.error(errorMsg.toString());
            }
            
            boolean result = domainDataIngService.updateById(domainDataIng);
            return result ? Result.success("更新成功", true) : Result.error("更新失败");
        } catch (Exception e) {
            log.error("更新数据失败（在途）", e);
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
                log.warn("删除失败（在途）：口令验证失败");
                return Result.error("删除失败：口令错误，请输入正确的6位数字口令");
            }
            
            boolean result = domainDataIngService.removeById(id);
            return result ? Result.success("删除成功", true) : Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除数据失败（在途）", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换为DomainData用于校验
     */
    private DomainData convertToDomainData(DomainDataIng ing) {
        DomainData data = new DomainData();
        data.setChineseName(ing.getChineseName());
        return data;
    }
}

