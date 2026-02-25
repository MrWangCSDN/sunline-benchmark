package com.sunline.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.DictDataIng;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.service.DictDataIngService;
import com.sunline.dict.service.DictDataService;
import com.sunline.dict.service.ExcelValidationService;
import com.sunline.dict.config.ExcelImportConfig;
import com.sunline.dict.dto.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 字典数据控制器（在途）
 */
@RestController
@RequestMapping("/api/dict-ing")
public class DictDataIngController {
    
    private static final Logger log = LoggerFactory.getLogger(DictDataIngController.class);
    
    @Autowired
    private DictDataIngService dictDataIngService;
    
    @Autowired
    private DictDataService dictDataService;
    
    @Autowired
    private ExcelValidationService excelValidationService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    /**
     * 分页查询
     */
    @GetMapping("/page")
    public Result<Page<DictDataIng>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        try {
            Page<DictDataIng> page = dictDataIngService.pageQuery(current, size, keyword);
            return Result.success(page);
        } catch (Exception e) {
            log.error("分页查询失败（在途）", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取数据总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        try {
            long count = dictDataIngService.count();
            return Result.success(count);
        } catch (Exception e) {
            log.error("统计数据失败（在途）", e);
            return Result.error("统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新（带口令验证）
     */
    @PutMapping("/update-with-password")
    public Result<Boolean> updateWithPassword(
            @RequestBody DictDataIng dictDataIng,
            @RequestParam("password") String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("更新失败（在途）：口令验证失败");
                return Result.error("更新失败：口令错误，请输入正确的6位数字口令");
            }
            
            // 校验数据
            DictDataIng existingData = dictDataIngService.getById(dictDataIng.getId());
            if (existingData == null) {
                return Result.error("数据不存在");
            }
            
            // 转换为DictData进行校验
            DictData dictData = convertToDictData(dictDataIng);
            
            // 获取所有贯标数据
            java.util.List<DictData> standardDataList = dictDataService.getAllActiveData();
            
            // 获取所有在途数据（排除当前记录）
            java.util.List<DictDataIng> ingDataList = dictDataIngService.list();
            ingDataList.removeIf(d -> d.getId().equals(dictDataIng.getId()));
            
            // 检查是否与贯标数据重复
            java.util.Set<String> standardEnglishAbbrSet = standardDataList.stream()
                .map(DictData::getEnglishAbbr)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
            
            java.util.Set<String> standardChineseNameSet = standardDataList.stream()
                .map(DictData::getChineseName)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
            
            java.util.Set<String> standardJavaEsfNameSet = standardDataList.stream()
                .map(DictData::getJavaEsfName)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
            
            // 检查是否与在途数据重复
            java.util.Set<String> ingEnglishAbbrSet = ingDataList.stream()
                .map(DictDataIng::getEnglishAbbr)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
            
            java.util.Set<String> ingChineseNameSet = ingDataList.stream()
                .map(DictDataIng::getChineseName)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
            
            java.util.Set<String> ingJavaEsfNameSet = ingDataList.stream()
                .map(DictDataIng::getJavaEsfName)
                .filter(v -> v != null && !v.trim().isEmpty())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
            
            ValidationResult validationResult = new ValidationResult();
            
            // 检查英文简称
            if (dictData.getEnglishAbbr() != null && !dictData.getEnglishAbbr().trim().isEmpty()) {
                String englishAbbr = dictData.getEnglishAbbr().trim();
                if (standardEnglishAbbrSet.contains(englishAbbr)) {
                    validationResult.addError("字典技术衍生表（在途）", "英文简称", "duplicate_with_standard",
                        java.util.Collections.singletonList(1), englishAbbr + "（与贯标数据重复）");
                } else if (ingEnglishAbbrSet.contains(englishAbbr)) {
                    validationResult.addError("字典技术衍生表（在途）", "英文简称", "duplicate_with_ing",
                        java.util.Collections.singletonList(1), englishAbbr + "（与在途数据重复）");
                }
            }
            
            // 检查中文名称
            if (dictData.getChineseName() != null && !dictData.getChineseName().trim().isEmpty()) {
                String chineseName = dictData.getChineseName().trim();
                if (standardChineseNameSet.contains(chineseName)) {
                    validationResult.addError("字典技术衍生表（在途）", "中文名称", "duplicate_with_standard",
                        java.util.Collections.singletonList(1), chineseName + "（与贯标数据重复）");
                } else if (ingChineseNameSet.contains(chineseName)) {
                    validationResult.addError("字典技术衍生表（在途）", "中文名称", "duplicate_with_ing",
                        java.util.Collections.singletonList(1), chineseName + "（与在途数据重复）");
                }
            }
            
            // 检查JAVA/ESF规范命名
            if (dictData.getJavaEsfName() != null && !dictData.getJavaEsfName().trim().isEmpty()) {
                String javaEsfName = dictData.getJavaEsfName().trim();
                if (standardJavaEsfNameSet.contains(javaEsfName)) {
                    validationResult.addError("字典技术衍生表（在途）", "JAVA/ESF规范命名", "duplicate_with_standard",
                        java.util.Collections.singletonList(1), javaEsfName + "（与贯标数据重复）");
                } else if (ingJavaEsfNameSet.contains(javaEsfName)) {
                    validationResult.addError("字典技术衍生表（在途）", "JAVA/ESF规范命名", "duplicate_with_ing",
                        java.util.Collections.singletonList(1), javaEsfName + "（与在途数据重复）");
                }
            }
            
            // 校验Java命名规范和在途数据内部重复性
            java.util.List<DictData> tempList = new java.util.ArrayList<>();
            // 将当前在途数据转换为DictData并添加到列表中进行校验
            for (DictDataIng ing : ingDataList) {
                tempList.add(convertToDictData(ing));
            }
            tempList.add(dictData);
            ValidationResult namingResult = excelValidationService.validateDictData(tempList);
            if (namingResult.hasErrors()) {
                // 添加所有错误（包括Java命名规范和内部重复性错误）
                for (ValidationResult.ValidationError error : namingResult.getErrors()) {
                    // 如果是内部重复错误，需要转换为在途数据的错误信息
                    if ("duplicate".equals(error.getErrorType())) {
                        validationResult.addError("字典技术衍生表（在途）", error.getFieldName(), "duplicate_with_ing",
                            error.getRows(), error.getDuplicateValue() + "（与在途数据重复）");
                    } else {
                        validationResult.getErrors().add(error);
                    }
                }
            }
            
            if (validationResult.hasErrors()) {
                StringBuilder errorMsg = new StringBuilder("数据校验失败：");
                for (ValidationResult.ValidationError error : validationResult.getErrors()) {
                    errorMsg.append(error.getFieldName()).append(" ").append(error.getDuplicateValue()).append("; ");
                }
                return Result.error(errorMsg.toString());
            }
            
            boolean result = dictDataIngService.updateById(dictDataIng);
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
            
            boolean result = dictDataIngService.removeById(id);
            return result ? Result.success("删除成功", true) : Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除数据失败（在途）", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换为DictData用于校验
     */
    private DictData convertToDictData(DictDataIng ing) {
        DictData data = new DictData();
        data.setEnglishAbbr(ing.getEnglishAbbr());
        data.setChineseName(ing.getChineseName());
        data.setJavaEsfName(ing.getJavaEsfName());
        return data;
    }
}

