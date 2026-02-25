package com.sunline.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.EnumMapping;
import com.sunline.dict.entity.EnumImportHistory;
import com.sunline.dict.service.EnumMappingService;
import com.sunline.dict.service.EnumImportHistoryService;
import com.sunline.dict.config.ExcelImportConfig;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 枚举映射关系Controller
 */
@RestController
@RequestMapping("/api/enum-mapping")
public class EnumMappingController {
    
    private static final Logger log = LoggerFactory.getLogger(EnumMappingController.class);
    
    @Autowired
    private EnumMappingService enumMappingService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    @Autowired
    private EnumImportHistoryService importHistoryService;
    
    // 全量导入锁（使用synchronized实现简单锁机制）
    private static volatile boolean isFullImportLocked = false;
    private static volatile String lockedBy = null;
    
    /**
     * 分页查询枚举映射关系
     */
    @GetMapping("/page")
    public Result<Page<EnumMapping>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String domainChineseName,
            @RequestParam(required = false) String domainEnglishAbbr,
            @RequestParam(required = false) String enumFieldId,
            @RequestParam(required = false) String codeValue,
            @RequestParam(required = false) String valueChineseName,
            @RequestParam(required = false) String codeDescription) {
        try {
            Page<EnumMapping> page = new Page<>(pageNum, pageSize);
            QueryWrapper<EnumMapping> queryWrapper = new QueryWrapper<>();
            
            // 添加搜索条件
            if (domainChineseName != null && !domainChineseName.trim().isEmpty()) {
                queryWrapper.like("domain_chinese_name", domainChineseName);
            }
            if (domainEnglishAbbr != null && !domainEnglishAbbr.trim().isEmpty()) {
                queryWrapper.like("domain_english_abbr", domainEnglishAbbr);
            }
            if (enumFieldId != null && !enumFieldId.trim().isEmpty()) {
                queryWrapper.like("enum_field_id", enumFieldId);
            }
            if (codeValue != null && !codeValue.trim().isEmpty()) {
                queryWrapper.like("code_value", codeValue);
            }
            if (valueChineseName != null && !valueChineseName.trim().isEmpty()) {
                queryWrapper.like("value_chinese_name", valueChineseName);
            }
            if (codeDescription != null && !codeDescription.trim().isEmpty()) {
                queryWrapper.like("code_description", codeDescription);
            }
            
            // 按域英文简称（枚举ID）字母排序
            queryWrapper.orderByAsc("domain_english_abbr");
            
            Page<EnumMapping> result = enumMappingService.page(page, queryWrapper);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询枚举映射关系失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 新增枚举映射关系
     */
    @PostMapping
    public Result<EnumMapping> add(@RequestBody EnumMapping enumMapping) {
        try {
            normalizeEnumMapping(enumMapping);
            String requiredError = validateRequiredFields(enumMapping);
            if (requiredError != null) {
                return Result.error(requiredError);
            }
            
            String uniqueError = checkUniqueConstraints(enumMapping, null);
            if (uniqueError != null) {
                return Result.error(uniqueError);
            }
            
            enumMapping.setCreateTime(LocalDateTime.now());
            enumMapping.setUpdateTime(LocalDateTime.now());
            
            boolean success = enumMappingService.save(enumMapping);
            if (success) {
                return Result.success("新增成功", enumMapping);
            } else {
                return Result.error("新增失败");
            }
        } catch (Exception e) {
            log.error("新增枚举映射关系失败", e);
            return Result.error("新增失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新枚举映射关系
     */
    @PutMapping("/{id}")
    public Result<EnumMapping> update(@PathVariable Long id, @RequestBody EnumMapping enumMapping) {
        try {
            enumMapping.setId(id);
            
            normalizeEnumMapping(enumMapping);
            String requiredError = validateRequiredFields(enumMapping);
            if (requiredError != null) {
                return Result.error(requiredError);
            }
            
            String uniqueError = checkUniqueConstraints(enumMapping, id);
            if (uniqueError != null) {
                return Result.error(uniqueError);
            }
            
            enumMapping.setUpdateTime(LocalDateTime.now());
            boolean success = enumMappingService.updateById(enumMapping);
            if (success) {
                return Result.success("更新成功", enumMapping);
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新枚举映射关系失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除枚举映射关系
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            boolean success = enumMappingService.removeById(id);
            if (success) {
                return Result.success("删除成功", null);
            } else {
                return Result.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除枚举映射关系失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID查询枚举映射关系
     */
    @GetMapping("/{id}")
    public Result<EnumMapping> getById(@PathVariable Long id) {
        try {
            EnumMapping enumMapping = enumMappingService.getById(id);
            if (enumMapping != null) {
                return Result.success("查询成功", enumMapping);
            } else {
                return Result.error("记录不存在");
            }
        } catch (Exception e) {
            log.error("查询枚举映射关系失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        try {
            long count = enumMappingService.count();
            return Result.success("查询成功", count);
        } catch (Exception e) {
            log.error("查询枚举映射关系总数失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    private void normalizeEnumMapping(EnumMapping mapping) {
        if (mapping.getDomainEnglishAbbr() != null) {
            mapping.setDomainEnglishAbbr(mapping.getDomainEnglishAbbr().trim());
        }
        if (mapping.getEnumFieldId() != null) {
            mapping.setEnumFieldId(mapping.getEnumFieldId().trim());
        }
        if (mapping.getDomainChineseName() != null) {
            mapping.setDomainChineseName(mapping.getDomainChineseName().trim());
        }
        if (mapping.getCodeValue() != null) {
            mapping.setCodeValue(mapping.getCodeValue().trim());
        }
        if (mapping.getValueChineseName() != null) {
            mapping.setValueChineseName(mapping.getValueChineseName().trim());
        }
        if (mapping.getCodeDescription() != null) {
            mapping.setCodeDescription(mapping.getCodeDescription().trim());
        }
    }
    
    private String validateRequiredFields(EnumMapping mapping) {
        if (mapping.getDomainEnglishAbbr() == null || mapping.getDomainEnglishAbbr().isEmpty()) {
            return "域英文简称（枚举ID）不能为空";
        }
        if (mapping.getEnumFieldId() == null || mapping.getEnumFieldId().isEmpty()) {
            return "枚举字段ID不能为空";
        }
        if (mapping.getDomainChineseName() == null || mapping.getDomainChineseName().isEmpty()) {
            return "域中文名称不能为空";
        }
        if (mapping.getCodeValue() == null || mapping.getCodeValue().isEmpty()) {
            return "代码取值不能为空";
        }
        return null;
    }
    
    private String checkUniqueConstraints(EnumMapping mapping, Long excludeId) {
        // 唯一1：域中文名称 + 代码取值
        String domainCodeDuplicate = checkDomainCodeDuplicate(mapping, excludeId);
        if (domainCodeDuplicate != null) {
            return domainCodeDuplicate;
        }
        
        // 唯一2：域英文简称 + 枚举字段ID
        String enumFieldDuplicate = checkEnumFieldDuplicate(mapping, excludeId);
        if (enumFieldDuplicate != null) {
            return enumFieldDuplicate;
        }
        
        return null;
    }
    
    /**
     * 检查"域中文名称 + 代码取值"是否重复
     */
    private String checkDomainCodeDuplicate(EnumMapping mapping, Long excludeId) {
        QueryWrapper<EnumMapping> domainCodeWrapper = new QueryWrapper<>();
        domainCodeWrapper.eq("domain_chinese_name", mapping.getDomainChineseName());
        domainCodeWrapper.eq("code_value", mapping.getCodeValue());
        if (excludeId != null) {
            domainCodeWrapper.ne("id", excludeId);
        }
        long domainCodeCount = enumMappingService.count(domainCodeWrapper);
        if (domainCodeCount > 0) {
            return "该域中文名称与代码取值的映射已存在";
        }
        return null;
    }
    
    /**
     * 检查"域英文简称 + 枚举字段ID"是否重复
     */
    private String checkEnumFieldDuplicate(EnumMapping mapping, Long excludeId) {
        QueryWrapper<EnumMapping> enumFieldWrapper = new QueryWrapper<>();
        enumFieldWrapper.eq("domain_english_abbr", mapping.getDomainEnglishAbbr());
        enumFieldWrapper.eq("enum_field_id", mapping.getEnumFieldId());
        if (excludeId != null) {
            enumFieldWrapper.ne("id", excludeId);
        }
        long enumFieldCount = enumMappingService.count(enumFieldWrapper);
        if (enumFieldCount > 0) {
            return "该枚举ID与枚举字段ID的组合已存在";
        }
        return null;
    }
    
    /**
     * 从文件名中提取版本号
     * 文件名格式：全量枚举映射关系维护_V2.xlsx 或 全量枚举映射关系维护_V2.xls
     * @param fileName 文件名
     * @return 版本号（如V2），如果未找到则返回null
     */
    private String extractVersionFromFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return null;
        }
        
        // 匹配模式：_V数字.xlsx 或 _V数字.xls
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("_V(\\d+)\\.(xlsx|xls)$");
        java.util.regex.Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            return "V" + matcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 导出枚举映射关系到Excel
     * Excel列：域英文简称（枚举ID）、枚举字段ID、域中文名称、代码取值、取值含义中文名称、代码描述
     */
    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> exportExcel() {
        try {
            log.info("开始导出枚举映射关系");
            
            // 获取最新版本号
            String version = "V1";
            try {
                EnumImportHistory latestHistory = importHistoryService.getLatestHistory();
                if (latestHistory != null && latestHistory.getVersion() != null) {
                    version = latestHistory.getVersion();
                }
            } catch (Exception e) {
                log.warn("获取最新版本号失败，使用默认版本V1", e);
            }
            
            // 查询所有映射关系，按域英文简称（枚举ID）字母排序
            QueryWrapper<EnumMapping> queryWrapper = new QueryWrapper<>();
            queryWrapper.orderByAsc("domain_english_abbr");
            List<EnumMapping> mappingList = enumMappingService.list(queryWrapper);
            
            // 创建Excel工作簿
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("枚举映射关系");
            
            // 创建样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "域英文简称（枚举ID）", "枚举字段ID", "域中文名称",
                "代码取值", "取值含义中文名称", "代码描述"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }
            
            // 填充数据
            int rowNum = 1;
            for (EnumMapping mapping : mappingList) {
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;
                
                createCell(row, colNum++, mapping.getDomainEnglishAbbr(), dataStyle);
                createCell(row, colNum++, mapping.getEnumFieldId(), dataStyle);
                createCell(row, colNum++, mapping.getDomainChineseName(), dataStyle);
                createCell(row, colNum++, mapping.getCodeValue(), dataStyle);
                createCell(row, colNum++, mapping.getValueChineseName(), dataStyle);
                createCell(row, colNum++, mapping.getCodeDescription(), dataStyle);
            }
            
            // 写入字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            
            byte[] excelBytes = outputStream.toByteArray();
            log.info("枚举映射关系导出成功，文件大小: {} bytes，记录数: {}", excelBytes.length, mappingList.size());
            
            ByteArrayResource resource = new ByteArrayResource(excelBytes);
            
            // 对文件名进行URL编码以支持中文，文件名拼接版本号
            String fileName = String.format("全量枚举映射关系维护_%s.xlsx", version);
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(excelBytes.length)
                .body(resource);
        } catch (Exception e) {
            log.error("导出枚举映射关系失败", e);
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建表头样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        
        return style;
    }
    
    /**
     * 创建数据样式
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        
        return style;
    }
    
    /**
     * 创建单元格并设置值
     */
    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }
    
    /**
     * Excel导入枚举映射关系
     * @param file Excel文件
     * @param mode 导入模式：full=全量覆盖，incremental=增量添加
     */
    @PostMapping("/import-excel")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "incremental") String mode,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "modifier", required = false) String modifier,
            @RequestParam(value = "modifyContent", required = false) String modifyContent,
            @RequestParam(value = "version", required = false) String version) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("Excel导入失败：口令验证失败");
                return Result.error("导入失败：口令错误，请输入正确的6位数字口令");
            }
            
            // 全量导入时检查锁
            if ("full".equals(mode)) {
                synchronized (EnumMappingController.class) {
                    if (isFullImportLocked) {
                        return Result.error("全量导入正在进行中，请稍候再试（操作人：" + lockedBy + "）");
                    }
                    // 获取锁
                    isFullImportLocked = true;
                    lockedBy = modifier != null ? modifier : "未知用户";
                }
            }
            
            try {
                log.info("开始导入枚举映射Excel文件: {}, 模式: {}", file.getOriginalFilename(), mode);
                
                // 解析Excel文件
                List<EnumMapping> dataList = parseExcel(file);
                
                if (dataList.isEmpty()) {
                    return Result.error("Excel文件中没有有效数据");
                }
                
                // 全量模式：先进行完整校验，校验通过后才执行导入
                if ("full".equals(mode)) {
                    // 验证必填字段
                    if (modifier == null || modifier.trim().isEmpty()) {
                        return Result.error("全量导入必须填写修改人");
                    }
                    if (modifyContent == null || modifyContent.trim().isEmpty()) {
                        return Result.error("全量导入必须填写修改内容");
                    }
                    if (version == null || version.trim().isEmpty()) {
                        return Result.error("全量导入必须提供版本号");
                    }
                    
                    // 检查文件名中的版本号是否与传入的版本号一致
                    String fileName = file.getOriginalFilename();
                    String fileVersion = "V1";
                    if (fileName != null) {
                        // 从文件名中提取版本号（格式：全量枚举映射关系维护_V2.xlsx）
                        fileVersion = extractVersionFromFileName(fileName);
                    }
                    
                    // 检查导入版本号是否与数据库中的最新历史版本一致
                    EnumImportHistory latestHistory = importHistoryService.getLatestHistory();
                    String currentLatestVersion = latestHistory != null ? latestHistory.getVersion() : null;
                    
                    if (currentLatestVersion == null) {
                        // 如果没有历史记录，导入的版本号应该是V1
                        if (!fileVersion.equals("V1")) {
                            return Result.error(String.format("当前无导入历史，导入版本号应为V1，但提供的版本号是[%s]", fileVersion));
                        }
                    } else {
                        // 如果有历史记录，导入的版本号必须与当前最新版本号一致
                        if (!fileVersion.equals(currentLatestVersion)) {
                            return Result.error(String.format("导入版本号[%s]与当前最新版本号[%s]不一致，请使用最新版本号", 
                            fileVersion, currentLatestVersion));
                        }
                    }
                    
                    // 全量导入：先进行完整校验，收集所有错误
                    List<String> validationErrors = new ArrayList<>(); // 收集所有校验错误
                    java.util.Map<String, Integer> domainCodeMap = new java.util.HashMap<>(); // 用于检查Excel内部重复
                    
                    // 1. 检查Excel文件内部重复
                    for (int i = 0; i < dataList.size(); i++) {
                        EnumMapping mapping = dataList.get(i);
                        if (mapping.getDomainChineseName() != null && mapping.getCodeValue() != null) {
                            String key = mapping.getDomainChineseName() + "|" + mapping.getCodeValue();
                            if (domainCodeMap.containsKey(key)) {
                                int firstRow = domainCodeMap.get(key) + 1;
                                int currentRow = i + 1;
                                String errorMsg = String.format("第%d行和第%d行：域中文名称[%s] + 代码取值[%s] 重复",
                                    firstRow, currentRow,
                                    mapping.getDomainChineseName(),
                                    mapping.getCodeValue());
                                validationErrors.add(errorMsg);
                            } else {
                                domainCodeMap.put(key, i);
                            }
                        }
                    }
                    
                    // 2. 检查必填字段和格式
                    for (int i = 0; i < dataList.size(); i++) {
                        EnumMapping mapping = dataList.get(i);
                        normalizeEnumMapping(mapping);
                        String requiredError = validateRequiredFields(mapping);
                        if (requiredError != null) {
                            String errorMsg = String.format("第%d行：%s", i + 1, requiredError);
                            validationErrors.add(errorMsg);
                        }
                    }
                    
                    // 如果有校验错误，返回所有错误信息，不执行导入
                    if (!validationErrors.isEmpty()) {
                        StringBuilder errorMessage = new StringBuilder("数据校验失败，发现以下问题：\n\n");
                        for (int i = 0; i < validationErrors.size(); i++) {
                            errorMessage.append(String.format("%d. %s\n", i + 1, validationErrors.get(i)));
                        }
                        return Result.error(errorMessage.toString());
                    }
                    
                    // 所有校验通过，开始导入
                    log.info("全量导入模式：校验通过，开始清空现有数据并导入");
                    enumMappingService.remove(null);
                }
            
            // 批量保存数据
            int successCount = 0;
            int skipCount = 0;
            int errorCount = 0;
            List<String> duplicateErrors = new ArrayList<>(); // 收集重复错误信息（增量导入用）
            
            for (int i = 0; i < dataList.size(); i++) {
                EnumMapping mapping = dataList.get(i);
                try {
                    // 全量导入已经校验过，直接保存
                    if ("full".equals(mode)) {
                        normalizeEnumMapping(mapping);
                    } else {
                        // 增量导入：继续执行原有逻辑
                        normalizeEnumMapping(mapping);
                        String requiredError = validateRequiredFields(mapping);
                        if (requiredError != null) {
                            skipCount++;
                            log.warn("跳过数据：{}，原因：{}", mapping, requiredError);
                            continue;
                        }
                        
                        // 校验"域中文名称+代码取值"是否重复
                        String domainCodeDuplicate = checkDomainCodeDuplicate(mapping, null);
                        if (domainCodeDuplicate != null) {
                            errorCount++;
                            String errorMsg = String.format("第%d行：域中文名称[%s] + 代码取值[%s] 已存在",
                                i + 1,
                                mapping.getDomainChineseName(),
                                mapping.getCodeValue());
                            duplicateErrors.add(errorMsg);
                            log.warn("重复数据：{}", errorMsg);
                            continue;
                        }
                        
                        // 增量导入时，还要校验"域英文简称+枚举字段ID"是否重复
                        String enumFieldDuplicate = checkEnumFieldDuplicate(mapping, null);
                        if (enumFieldDuplicate != null) {
                            skipCount++;
                            log.info("跳过已存在的映射: [{}] - {}", mapping, enumFieldDuplicate);
                            continue;
                        }
                    }
                    
                    // 保存数据
                    log.info("尝试保存: [域英文简称={}, 枚举字段ID={}, 域中文名称={}, 代码取值={}, 取值含义中文名称={}]",
                        mapping.getDomainEnglishAbbr(),
                        mapping.getEnumFieldId(),
                        mapping.getDomainChineseName(),
                        mapping.getCodeValue(),
                        mapping.getValueChineseName());
                    
                    boolean success = enumMappingService.save(mapping);
                    if (success) {
                        successCount++;
                        log.info("✓ 第{}条保存成功", successCount);
                    } else {
                        errorCount++;
                        log.error("✗ 保存失败（返回false）");
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.error("✗ 保存异常: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                    if (e.getCause() != null) {
                        log.error("  原因: {}", e.getCause().getMessage());
                    }
                }
            }
            
                StringBuilder message = new StringBuilder();
                message.append(String.format("导入完成！成功: %d 条", successCount));
                if (skipCount > 0) {
                    message.append(String.format("，跳过: %d 条", skipCount));
                }
                if (errorCount > 0) {
                    message.append(String.format("，失败: %d 条", errorCount));
                }
                
                // 如果有重复错误，添加到消息中
                if (!duplicateErrors.isEmpty()) {
                    message.append("\n\n重复数据详情：\n");
                    for (String error : duplicateErrors) {
                        message.append(error).append("\n");
                    }
                }
                
                // 全量导入时记录导入历史
                if ("full".equals(mode)) {
                    EnumImportHistory history = new EnumImportHistory();
                    history.setVersion(version);
                    history.setModifier(modifier);
                    history.setModifyContent(modifyContent);
                    history.setRecordCount(successCount);
                    history.setImportTime(LocalDateTime.now());
                    importHistoryService.save(history);
                    log.info("已记录导入历史：版本={}, 修改人={}, 记录数={}", version, modifier, successCount);
                }
                
                log.info("========== 导入统计 ==========");
                log.info("总数据: {} 条", dataList.size());
                log.info("成功: {} 条", successCount);
                log.info("跳过: {} 条", skipCount);
                log.info("失败: {} 条", errorCount);
                log.info("==============================");
                
                return Result.success(message.toString(), null);
                
            } finally {
                // 全量导入完成后释放锁
                if ("full".equals(mode)) {
                    synchronized (EnumMappingController.class) {
                        isFullImportLocked = false;
                        lockedBy = null;
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("导入Excel失败", e);
            // 确保异常时也释放锁
            if ("full".equals(mode)) {
                synchronized (EnumMappingController.class) {
                    isFullImportLocked = false;
                    lockedBy = null;
                }
            }
            return Result.error("导入失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取下一个版本号
     */
    @GetMapping("/next-version")
    public Result<String> getNextVersion() {
        try {
            String nextVersion = importHistoryService.getNextVersion();
            return Result.success(nextVersion);
        } catch (Exception e) {
            log.error("获取版本号失败", e);
            return Result.error("获取版本号失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取最新的导入历史记录
     */
    @GetMapping("/latest-history")
    public Result<EnumImportHistory> getLatestHistory() {
        try {
            EnumImportHistory history = importHistoryService.getLatestHistory();
            return Result.success(history);
        } catch (Exception e) {
            log.error("获取最新导入历史失败", e);
            return Result.error("获取最新导入历史失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析Excel文件
     * Excel格式：
     * 第1列：域英文简称（枚举ID）
     * 第2列：枚举字段ID
     * 第3列：域中文名称
     * 第4列：代码取值
     * 第5列：取值含义中文名称
     * 第6列：代码描述
     */
    private List<EnumMapping> parseExcel(MultipartFile file) throws Exception {
        List<EnumMapping> dataList = new ArrayList<>();
        
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            
            log.info("Excel总行数: {}", rowCount);
            
            // 跳过表头，从第二行开始读取
            for (int i = 1; i < rowCount; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                
                try {
                    // 创建映射对象
                    EnumMapping mapping = new EnumMapping();
                    
                    // 第1列：域英文简称（枚举ID）
                    String domainEnglishAbbr = getCellValue(row.getCell(0));
                    if (domainEnglishAbbr == null || domainEnglishAbbr.trim().isEmpty()) {
                        log.warn("第{}行：域英文简称为空，跳过", i + 1);
                        continue;
                    }
                    mapping.setDomainEnglishAbbr(domainEnglishAbbr.trim());
                    
                    // 第2列：枚举字段ID
                    String enumFieldId = getCellValue(row.getCell(1));
                    if (enumFieldId == null || enumFieldId.trim().isEmpty()) {
                        log.warn("第{}行：枚举字段ID为空，跳过", i + 1);
                        continue;
                    }
                    mapping.setEnumFieldId(enumFieldId.trim());
                    
                    // 第3列：域中文名称
                    String domainChineseName = getCellValue(row.getCell(2));
                    if (domainChineseName == null || domainChineseName.trim().isEmpty()) {
                        log.warn("第{}行：域中文名称为空，跳过", i + 1);
                        continue;
                    }
                    mapping.setDomainChineseName(domainChineseName.trim());
                    
                    // 第4列：代码取值
                    String codeValue = getCellValue(row.getCell(3));
                    if (codeValue == null || codeValue.trim().isEmpty()) {
                        log.warn("第{}行：代码取值为空，跳过", i + 1);
                        continue;
                    }
                    mapping.setCodeValue(codeValue.trim());
                    
                    // 第5列：取值含义中文名称
                    String valueChineseName = getCellValue(row.getCell(4));
                    if (valueChineseName != null && !valueChineseName.trim().isEmpty()) {
                        mapping.setValueChineseName(valueChineseName.trim());
                    }
                    
                    // 第6列：代码描述
                    String codeDescription = getCellValue(row.getCell(5));
                    if (codeDescription != null && !codeDescription.trim().isEmpty()) {
                        mapping.setCodeDescription(codeDescription.trim());
                    }
                    
                    mapping.setCreateTime(LocalDateTime.now());
                    mapping.setUpdateTime(LocalDateTime.now());
                    
                    // 详细日志
                    log.info("第{}行解析成功: [域英文简称={}, 枚举字段ID={}, 域中文名称={}, 代码取值={}, 取值含义中文名称={}, 代码描述={}]", 
                        i + 1, 
                        mapping.getDomainEnglishAbbr(), 
                        mapping.getEnumFieldId(),
                        mapping.getDomainChineseName(),
                        mapping.getCodeValue(),
                        mapping.getValueChineseName(),
                        mapping.getCodeDescription());
                    
                    dataList.add(mapping);
                    
                } catch (Exception e) {
                    log.error("解析第{}行数据失败: {}", i + 1, e.getMessage(), e);
                }
            }
        }
        
        log.info("成功解析 {} 条数据", dataList.size());
        return dataList;
    }
    
    /**
     * 获取单元格的值
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return null;
            default:
                return null;
        }
    }
}

