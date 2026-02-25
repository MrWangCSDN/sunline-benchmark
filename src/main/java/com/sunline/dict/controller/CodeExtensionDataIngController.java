package com.sunline.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.CodeExtensionDataIng;
import com.sunline.dict.entity.CodeExtensionData;
import com.sunline.dict.service.CodeExtensionDataIngService;
import com.sunline.dict.service.CodeExtensionDataService;
import com.sunline.dict.service.ExcelValidationService;
import com.sunline.dict.config.ExcelImportConfig;
import com.sunline.dict.dto.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 代码扩展清单数据Controller（在途）
 */
@RestController
@RequestMapping("/api/code-extension-ing")
public class CodeExtensionDataIngController {
    
    private static final Logger log = LoggerFactory.getLogger(CodeExtensionDataIngController.class);
    
    @Autowired
    private CodeExtensionDataIngService codeExtensionDataIngService;
    
    @Autowired
    private CodeExtensionDataService codeExtensionDataService;
    
    @Autowired
    private ExcelValidationService excelValidationService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    /**
     * 分页查询代码扩展清单数据
     */
    @GetMapping("/page")
    public Result<Page<CodeExtensionDataIng>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String codeDomainNumber,
            @RequestParam(required = false) String codeDomainChineseName,
            @RequestParam(required = false) String codeValue,
            @RequestParam(required = false) String valueChineseName,
            @RequestParam(required = false) String codeEnglishName,
            @RequestParam(required = false) String codeEnglishAbbr,
            @RequestParam(required = false) String codeDescription,
            @RequestParam(required = false) String domainRule,
            @RequestParam(required = false) String codeDomainSource,
            @RequestParam(required = false) String sourceNumber,
            @RequestParam(required = false) String remark) {
        try {
            Page<CodeExtensionDataIng> page = new Page<>(current, size);
            QueryWrapper<CodeExtensionDataIng> queryWrapper = new QueryWrapper<>();
            
            // 多字段模糊查询
            if (codeDomainNumber != null && !codeDomainNumber.isEmpty()) {
                queryWrapper.like("code_domain_number", codeDomainNumber);
            }
            if (codeDomainChineseName != null && !codeDomainChineseName.isEmpty()) {
                queryWrapper.like("code_domain_chinese_name", codeDomainChineseName);
            }
            if (codeValue != null && !codeValue.isEmpty()) {
                queryWrapper.like("code_value", codeValue);
            }
            if (valueChineseName != null && !valueChineseName.isEmpty()) {
                queryWrapper.like("value_chinese_name", valueChineseName);
            }
            if (codeEnglishName != null && !codeEnglishName.isEmpty()) {
                queryWrapper.like("code_english_name", codeEnglishName);
            }
            if (codeEnglishAbbr != null && !codeEnglishAbbr.isEmpty()) {
                queryWrapper.like("code_english_abbr", codeEnglishAbbr);
            }
            if (codeDescription != null && !codeDescription.isEmpty()) {
                queryWrapper.like("code_description", codeDescription);
            }
            if (domainRule != null && !domainRule.isEmpty()) {
                queryWrapper.like("domain_rule", domainRule);
            }
            if (codeDomainSource != null && !codeDomainSource.isEmpty()) {
                queryWrapper.like("code_domain_source", codeDomainSource);
            }
            if (sourceNumber != null && !sourceNumber.isEmpty()) {
                queryWrapper.like("source_number", sourceNumber);
            }
            if (remark != null && !remark.isEmpty()) {
                queryWrapper.like("remark", remark);
            }
            
            // 按代码域中文名称和代码取值排序
            queryWrapper.orderByAsc("code_domain_chinese_name", "code_value");
            
            Page<CodeExtensionDataIng> result = codeExtensionDataIngService.page(page, queryWrapper);
            
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询代码扩展清单失败（在途）", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取代码扩展清单总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        try {
            long count = codeExtensionDataIngService.count();
            return Result.success("查询成功", count);
        } catch (Exception e) {
            log.error("查询代码扩展清单总数失败（在途）", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新（带口令验证）
     */
    @PutMapping("/update-with-password")
    public Result<Boolean> updateWithPassword(
            @RequestBody CodeExtensionDataIng codeExtensionDataIng,
            @RequestParam("password") String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("更新失败（在途）：口令验证失败");
                return Result.error("更新失败：口令错误，请输入正确的6位数字口令");
            }
            
            // 校验数据
            CodeExtensionDataIng existingData = codeExtensionDataIngService.getById(codeExtensionDataIng.getId());
            if (existingData == null) {
                return Result.error("数据不存在");
            }
            
            // 转换为CodeExtensionData进行校验
            CodeExtensionData codeExtensionData = convertToCodeExtensionData(codeExtensionDataIng);
            
            // 获取所有贯标数据
            java.util.List<CodeExtensionData> standardDataList = codeExtensionDataService.list();
            
            // 获取所有在途数据（排除当前记录）
            java.util.List<CodeExtensionDataIng> ingDataList = codeExtensionDataIngService.list();
            ingDataList.removeIf(d -> d.getId().equals(codeExtensionDataIng.getId()));
            
            // 检查是否与贯标数据重复（代码域中文名称+代码取值）
            java.util.Set<String> standardCompositeKeySet = standardDataList.stream()
                .filter(d -> d.getCodeDomainChineseName() != null && !d.getCodeDomainChineseName().trim().isEmpty() &&
                             d.getCodeValue() != null && !d.getCodeValue().trim().isEmpty())
                .map(d -> d.getCodeDomainChineseName().trim() + "|" + d.getCodeValue().trim())
                .collect(java.util.stream.Collectors.toSet());
            
            // 检查是否与在途数据重复
            java.util.Set<String> ingCompositeKeySet = ingDataList.stream()
                .filter(d -> d.getCodeDomainChineseName() != null && !d.getCodeDomainChineseName().trim().isEmpty() &&
                             d.getCodeValue() != null && !d.getCodeValue().trim().isEmpty())
                .map(d -> d.getCodeDomainChineseName().trim() + "|" + d.getCodeValue().trim())
                .collect(java.util.stream.Collectors.toSet());
            
            ValidationResult validationResult = new ValidationResult();
            
            // 检查组合键
            if (codeExtensionData.getCodeDomainChineseName() != null && !codeExtensionData.getCodeDomainChineseName().trim().isEmpty() &&
                codeExtensionData.getCodeValue() != null && !codeExtensionData.getCodeValue().trim().isEmpty()) {
                String compositeKey = codeExtensionData.getCodeDomainChineseName().trim() + "|" + codeExtensionData.getCodeValue().trim();
                if (standardCompositeKeySet.contains(compositeKey)) {
                    validationResult.addError("代码扩展清单（在途）", "代码域中文名称+代码取值", "duplicate_with_standard",
                        java.util.Collections.singletonList(1), compositeKey + "（与贯标数据重复）");
                } else if (ingCompositeKeySet.contains(compositeKey)) {
                    validationResult.addError("代码扩展清单（在途）", "代码域中文名称+代码取值", "duplicate_with_ing",
                        java.util.Collections.singletonList(1), compositeKey + "（与在途数据重复）");
                }
            }
            
            if (validationResult.hasErrors()) {
                StringBuilder errorMsg = new StringBuilder("数据校验失败：");
                for (ValidationResult.ValidationError error : validationResult.getErrors()) {
                    errorMsg.append(error.getFieldName()).append(" ").append(error.getDuplicateValue()).append("; ");
                }
                return Result.error(errorMsg.toString());
            }
            
            boolean result = codeExtensionDataIngService.updateById(codeExtensionDataIng);
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
            
            boolean result = codeExtensionDataIngService.removeById(id);
            return result ? Result.success("删除成功", true) : Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除数据失败（在途）", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换为CodeExtensionData用于校验
     */
    private CodeExtensionData convertToCodeExtensionData(CodeExtensionDataIng ing) {
        CodeExtensionData data = new CodeExtensionData();
        data.setCodeDomainChineseName(ing.getCodeDomainChineseName());
        data.setCodeValue(ing.getCodeValue());
        return data;
    }
}

