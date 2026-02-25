package com.sunline.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.CodeExtensionData;
import com.sunline.dict.service.CodeExtensionDataService;
import com.sunline.dict.service.EnumMappingService;
import com.sunline.dict.service.ExcelValidationService;
import com.sunline.dict.config.ExcelImportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 代码扩展清单数据Controller
 */
@RestController
@RequestMapping("/api/code-extension")
public class CodeExtensionDataController {
    
    private static final Logger log = LoggerFactory.getLogger(CodeExtensionDataController.class);
    
    @Autowired
    private CodeExtensionDataService codeExtensionDataService;
    
    @Autowired
    private EnumMappingService enumMappingService;
    
    @Autowired
    private ExcelValidationService excelValidationService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    /**
     * 分页查询代码扩展清单数据
     */
    @GetMapping("/page")
    public Result<Page<CodeExtensionData>> page(
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
            Page<CodeExtensionData> page = new Page<>(current, size);
            QueryWrapper<CodeExtensionData> queryWrapper = new QueryWrapper<>();
            
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
            
            Page<CodeExtensionData> result = codeExtensionDataService.page(page, queryWrapper);
            
            // 填充映射的代码含义英文简称
            fillMappedCodeEnglishAbbr(result.getRecords());
            
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询代码扩展清单失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取代码扩展清单总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        try {
            long count = codeExtensionDataService.count();
            return Result.success("查询成功", count);
        } catch (Exception e) {
            log.error("查询代码扩展清单总数失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 填充映射的代码含义英文简称
     * 通过代码域中文名称+取值含义中文名称去枚举映射表中查找
     */
    private void fillMappedCodeEnglishAbbr(java.util.List<CodeExtensionData> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }
        
        // 一次性加载所有映射到Map中，避免N+1查询问题
        java.util.Map<String, String> mappings = enumMappingService.getAllCodeMappings();
        
        for (CodeExtensionData code : dataList) {
            String domainChineseName = code.getCodeDomainChineseName();
            String codeValue = code.getCodeValue();
            
            if (domainChineseName != null && !domainChineseName.trim().isEmpty() &&
                codeValue != null && !codeValue.trim().isEmpty()) {
                // 组合键：域中文名称|代码取值
                String key = domainChineseName.trim() + "|" + codeValue.trim();
                // 从Map中查找映射
                String mappedAbbr = mappings.get(key);
                if (mappedAbbr != null && !mappedAbbr.isEmpty()) {
                    // 如果找到映射，填充代码含义英文简称
                    code.setCodeEnglishAbbr(mappedAbbr);
                }
            }
        }
    }
    
    /**
     * 更新（带口令验证）
     */
    @PutMapping("/update-with-password")
    public Result<Boolean> updateWithPassword(
            @RequestBody CodeExtensionData codeExtensionData,
            @RequestParam("password") String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("更新失败：口令验证失败");
                return Result.error("更新失败：口令错误，请输入正确的6位数字口令");
            }
            
            // 校验数据
            java.util.List<CodeExtensionData> allData = codeExtensionDataService.list();
            CodeExtensionData existingData = allData.stream()
                .filter(d -> d.getId().equals(codeExtensionData.getId()))
                .findFirst()
                .orElse(null);
            
            if (existingData == null) {
                return Result.error("数据不存在");
            }
            
            // 创建临时列表用于校验（排除当前记录）
            java.util.List<CodeExtensionData> tempList = new java.util.ArrayList<>(allData);
            tempList.removeIf(d -> d.getId().equals(codeExtensionData.getId()));
            tempList.add(codeExtensionData);
            
            com.sunline.dict.dto.ValidationResult validationResult = 
                excelValidationService.validateCodeExtensionData(tempList);
            
            if (validationResult.hasErrors()) {
                StringBuilder errorMsg = new StringBuilder("数据校验失败：");
                for (com.sunline.dict.dto.ValidationResult.ValidationError error : validationResult.getErrors()) {
                    errorMsg.append(error.getFieldName()).append(" ").append(error.getDuplicateValue()).append("; ");
                }
                return Result.error(errorMsg.toString());
            }
            
            boolean result = codeExtensionDataService.updateById(codeExtensionData);
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
            
            boolean result = codeExtensionDataService.removeById(id);
            return result ? Result.success("删除成功", true) : Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除数据失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}

