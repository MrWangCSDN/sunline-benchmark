package com.sunline.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.service.DictDataService;
import com.sunline.dict.service.DictDataIngService;
import com.sunline.dict.service.ProgressService;
import com.sunline.dict.config.ExcelImportConfig;
import com.sunline.dict.entity.DictDataIng;
import com.sunline.dict.dto.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 字典数据控制器
 */
@RestController
@RequestMapping("/api/dict")
public class DictDataController {
    
    private static final Logger log = LoggerFactory.getLogger(DictDataController.class);
    
    @Autowired
    private DictDataService dictDataService;
    
    @Autowired
    private ProgressService progressService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    @Autowired
    private com.sunline.dict.service.ExcelValidationService excelValidationService;
    
    @Autowired
    private DictDataIngService dictDataIngService;
    
    /**
     * 创建SSE连接（用于接收导入进度）
     */
    @GetMapping("/progress/{clientId}")
    public SseEmitter progress(@PathVariable String clientId) {
        log.info("客户端{}建立SSE连接", clientId);
        return progressService.createEmitter(clientId);
    }
    
    /**
     * 导入Excel文件
     */
    @PostMapping("/import")
    public Result<Integer> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("clientId") String clientId,
            @RequestParam(value = "password", required = false) String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("Excel导入失败：口令验证失败");
                return Result.error("导入失败：口令错误，请输入正确的6位数字口令");
            }
            
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            String filename = file.getOriginalFilename();
            if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
                return Result.error("只支持Excel文件(.xlsx或.xls)");
            }
            
            log.info("开始导入Excel文件: {}, 客户端ID: {}", filename, clientId);
            
            int count = dictDataService.importExcel(file, clientId);
            
            return Result.success("成功导入" + count + "条数据", count);
            
        } catch (Exception e) {
            log.error("导入Excel失败", e);
            return Result.error("导入失败: " + e.getMessage());
        }
    }
    
    /**
     * 分页查询
     */
    @GetMapping("/page")
    public Result<Page<DictData>> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        try {
            Page<DictData> page = dictDataService.pageQuery(current, size, keyword);
            return Result.success(page);
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有数据
     */
    @GetMapping("/list")
    public Result<List<DictData>> list() {
        try {
            List<DictData> list = dictDataService.getAllData();
            return Result.success(list);
        } catch (Exception e) {
            log.error("查询列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public Result<DictData> getById(@PathVariable Long id) {
        try {
            DictData dictData = dictDataService.getById(id);
            if (dictData == null) {
                return Result.error("数据不存在");
            }
            return Result.success(dictData);
        } catch (Exception e) {
            log.error("查询数据失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 新增
     */
    @PostMapping
    public Result<Boolean> save(@RequestBody DictData dictData) {
        try {
            boolean result = dictDataService.save(dictData);
            return result ? Result.success("新增成功", true) : Result.error("新增失败");
        } catch (Exception e) {
            log.error("新增数据失败", e);
            return Result.error("新增失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新
     */
    @PutMapping
    public Result<Boolean> update(@RequestBody DictData dictData) {
        try {
            boolean result = dictDataService.updateById(dictData);
            return result ? Result.success("更新成功", true) : Result.error("更新失败");
        } catch (Exception e) {
            log.error("更新数据失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新（带口令验证）
     */
    @PutMapping("/update-with-password")
    public Result<Boolean> updateWithPassword(
            @RequestBody DictData dictData,
            @RequestParam("password") String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("更新失败：口令验证失败");
                return Result.error("更新失败：口令错误，请输入正确的6位数字口令");
            }
            
            // 校验数据
            DictData existingData = dictDataService.getById(dictData.getId());
            if (existingData == null) {
                return Result.error("数据不存在");
            }
            
            // 获取所有贯标数据（排除当前记录）
            java.util.List<DictData> standardDataList = dictDataService.getAllActiveData();
            standardDataList.removeIf(d -> d.getId().equals(dictData.getId()));
            
            // 获取所有在途数据
            java.util.List<DictDataIng> ingDataList = dictDataIngService.getAllActiveData();
            
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
                    validationResult.addError("字典技术衍生表", "英文简称", "duplicate_with_standard",
                        java.util.Collections.singletonList(1), englishAbbr + "（与贯标数据重复）");
                } else if (ingEnglishAbbrSet.contains(englishAbbr)) {
                    validationResult.addError("字典技术衍生表", "英文简称", "duplicate_with_ing",
                        java.util.Collections.singletonList(1), englishAbbr + "（与在途数据重复）");
                }
            }
            
            // 检查中文名称
            if (dictData.getChineseName() != null && !dictData.getChineseName().trim().isEmpty()) {
                String chineseName = dictData.getChineseName().trim();
                if (standardChineseNameSet.contains(chineseName)) {
                    validationResult.addError("字典技术衍生表", "中文名称", "duplicate_with_standard",
                        java.util.Collections.singletonList(1), chineseName + "（与贯标数据重复）");
                } else if (ingChineseNameSet.contains(chineseName)) {
                    validationResult.addError("字典技术衍生表", "中文名称", "duplicate_with_ing",
                        java.util.Collections.singletonList(1), chineseName + "（与在途数据重复）");
                }
            }
            
            // 检查JAVA/ESF规范命名
            if (dictData.getJavaEsfName() != null && !dictData.getJavaEsfName().trim().isEmpty()) {
                String javaEsfName = dictData.getJavaEsfName().trim();
                if (standardJavaEsfNameSet.contains(javaEsfName)) {
                    validationResult.addError("字典技术衍生表", "JAVA/ESF规范命名", "duplicate_with_standard",
                        java.util.Collections.singletonList(1), javaEsfName + "（与贯标数据重复）");
                } else if (ingJavaEsfNameSet.contains(javaEsfName)) {
                    validationResult.addError("字典技术衍生表", "JAVA/ESF规范命名", "duplicate_with_ing",
                        java.util.Collections.singletonList(1), javaEsfName + "（与在途数据重复）");
                }
            }
            
            // 校验Java命名规范和贯标数据内部重复性
            java.util.List<DictData> tempList = new java.util.ArrayList<>(standardDataList);
            tempList.add(dictData);
            ValidationResult internalResult = excelValidationService.validateDictData(tempList);
            if (internalResult.hasErrors()) {
                // 添加Java命名规范错误和贯标数据内部重复错误
                for (ValidationResult.ValidationError error : internalResult.getErrors()) {
                    validationResult.getErrors().add(error);
                }
            }
            
            if (validationResult.hasErrors()) {
                StringBuilder errorMsg = new StringBuilder("数据校验失败：");
                for (ValidationResult.ValidationError error : validationResult.getErrors()) {
                    errorMsg.append(error.getFieldName()).append(" ").append(error.getDuplicateValue()).append("; ");
                }
                return Result.error(errorMsg.toString());
            }
            
            boolean result = dictDataService.updateById(dictData);
            return result ? Result.success("更新成功", true) : Result.error("更新失败");
        } catch (Exception e) {
            log.error("更新数据失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        try {
            boolean result = dictDataService.removeById(id);
            return result ? Result.success("删除成功", true) : Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除数据失败", e);
            return Result.error("删除失败: " + e.getMessage());
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
            
            boolean result = dictDataService.removeById(id);
            return result ? Result.success("删除成功", true) : Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除数据失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 清空表数据
     */
    @DeleteMapping("/truncate")
    public Result<Boolean> truncate() {
        try {
            dictDataService.truncateTable();
            return Result.success("清空成功", true);
        } catch (Exception e) {
            log.error("清空数据失败", e);
            return Result.error("清空失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取数据总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        try {
            long count = dictDataService.count();
            return Result.success(count);
        } catch (Exception e) {
            log.error("统计数据失败", e);
            return Result.error("统计失败: " + e.getMessage());
        }
    }
}

