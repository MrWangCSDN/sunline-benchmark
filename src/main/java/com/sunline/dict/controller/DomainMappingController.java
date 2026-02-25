package com.sunline.dict.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.DomainMapping;
import com.sunline.dict.service.DomainMappingService;
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
 * 域清单映射关系Controller
 */
@RestController
@RequestMapping("/api/domain-mapping")
public class DomainMappingController {
    
    private static final Logger log = LoggerFactory.getLogger(DomainMappingController.class);
    
    @Autowired
    private DomainMappingService domainMappingService;
    
    @Autowired
    private ExcelImportConfig excelImportConfig;
    
    /**
     * 分页查询域清单映射关系
     */
    @GetMapping("/page")
    public Result<Page<DomainMapping>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String domainChineseName,
            @RequestParam(required = false) String domainEnglishAbbr) {
        try {
            Page<DomainMapping> page = new Page<>(pageNum, pageSize);
            QueryWrapper<DomainMapping> queryWrapper = new QueryWrapper<>();
            
            // 添加搜索条件
            if (domainChineseName != null && !domainChineseName.trim().isEmpty()) {
                queryWrapper.like("domain_chinese_name", domainChineseName);
            }
            if (domainEnglishAbbr != null && !domainEnglishAbbr.trim().isEmpty()) {
                queryWrapper.like("domain_english_abbr", domainEnglishAbbr);
            }
            
            // 按创建时间倒序
            queryWrapper.orderByDesc("create_time");
            
            Page<DomainMapping> result = domainMappingService.page(page, queryWrapper);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            log.error("分页查询域清单映射关系失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 新增域清单映射关系
     */
    @PostMapping
    public Result<DomainMapping> add(@RequestBody DomainMapping domainMapping) {
        try {
            // 校验必填字段
            if (domainMapping.getDomainChineseName() == null || domainMapping.getDomainChineseName().trim().isEmpty()) {
                return Result.error("域中文名称不能为空");
            }
            
            // 检查域中文名称是否已存在
            QueryWrapper<DomainMapping> nameQueryWrapper = new QueryWrapper<>();
            nameQueryWrapper.eq("domain_chinese_name", domainMapping.getDomainChineseName().trim());
            
            DomainMapping existingByName = domainMappingService.getOne(nameQueryWrapper);
            if (existingByName != null) {
                // 返回已存在的记录信息，使用特殊的错误码400
                Result<DomainMapping> result = new Result<>(400, "该域中文名称已存在映射关系", existingByName);
                return result;
            }
            
            // 检查域英文简称是否已被其他域中文名称使用
            if (domainMapping.getDomainEnglishAbbr() != null && !domainMapping.getDomainEnglishAbbr().trim().isEmpty()) {
                QueryWrapper<DomainMapping> abbrQueryWrapper = new QueryWrapper<>();
                abbrQueryWrapper.eq("domain_english_abbr", domainMapping.getDomainEnglishAbbr().trim());
                
                DomainMapping existingByAbbr = domainMappingService.getOne(abbrQueryWrapper);
                if (existingByAbbr != null) {
                    // 返回已存在的记录信息，使用特殊的错误码401
                    Result<DomainMapping> result = new Result<>(401, "该域英文简称已被使用", existingByAbbr);
                    return result;
                }
            }
            
            boolean success = domainMappingService.save(domainMapping);
            if (success) {
                return Result.success("新增成功", domainMapping);
            } else {
                return Result.error("新增失败");
            }
        } catch (Exception e) {
            log.error("新增域清单映射关系失败", e);
            return Result.error("新增失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新域清单映射关系
     */
    @PutMapping("/{id}")
    public Result<DomainMapping> update(@PathVariable Long id, @RequestBody DomainMapping domainMapping) {
        try {
            domainMapping.setId(id);
            
            // 校验必填字段
            if (domainMapping.getDomainChineseName() == null || domainMapping.getDomainChineseName().trim().isEmpty()) {
                return Result.error("域中文名称不能为空");
            }
            
            // 检查域中文名称是否被其他记录使用（排除当前记录）
            QueryWrapper<DomainMapping> nameQueryWrapper = new QueryWrapper<>();
            nameQueryWrapper.eq("domain_chinese_name", domainMapping.getDomainChineseName().trim());
            nameQueryWrapper.ne("id", id);
            
            DomainMapping existingByName = domainMappingService.getOne(nameQueryWrapper);
            if (existingByName != null) {
                // 返回已存在的记录信息，使用特殊的错误码400
                Result<DomainMapping> result = new Result<>(400, "该域中文名称已被其他记录使用", existingByName);
                return result;
            }
            
            // 检查域英文简称是否已被其他记录使用（排除当前记录）
            if (domainMapping.getDomainEnglishAbbr() != null && !domainMapping.getDomainEnglishAbbr().trim().isEmpty()) {
                QueryWrapper<DomainMapping> abbrQueryWrapper = new QueryWrapper<>();
                abbrQueryWrapper.eq("domain_english_abbr", domainMapping.getDomainEnglishAbbr().trim());
                abbrQueryWrapper.ne("id", id);
                
                DomainMapping existingByAbbr = domainMappingService.getOne(abbrQueryWrapper);
                if (existingByAbbr != null) {
                    // 返回已存在的记录信息，使用特殊的错误码401
                    Result<DomainMapping> result = new Result<>(401, "该域英文简称已被其他记录使用", existingByAbbr);
                    return result;
                }
            }
            
            boolean success = domainMappingService.updateById(domainMapping);
            if (success) {
                return Result.success("更新成功", domainMapping);
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新域清单映射关系失败", e);
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除域清单映射关系
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            boolean success = domainMappingService.removeById(id);
            if (success) {
                return Result.success("删除成功", null);
            } else {
                return Result.error("删除失败");
            }
        } catch (Exception e) {
            log.error("删除域清单映射关系失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据ID查询域清单映射关系
     */
    @GetMapping("/{id}")
    public Result<DomainMapping> getById(@PathVariable Long id) {
        try {
            DomainMapping domainMapping = domainMappingService.getById(id);
            if (domainMapping != null) {
                return Result.success("查询成功", domainMapping);
            } else {
                return Result.error("记录不存在");
            }
        } catch (Exception e) {
            log.error("查询域清单映射关系失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        try {
            long count = domainMappingService.count();
            return Result.success("查询成功", count);
        } catch (Exception e) {
            log.error("查询域清单映射关系总数失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 导出域清单映射关系到Excel
     * Excel列：域中文名称、域英文简称
     */
    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> exportExcel() {
        try {
            log.info("开始导出域清单映射关系");
            
            // 查询所有映射关系
            List<DomainMapping> mappingList = domainMappingService.list();
            
            // 创建Excel工作簿
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("域清单映射关系");
            
            // 创建样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            // 创建表头
            Row headerRow = sheet.createRow(0);
            String[] headers = {"域中文名称", "域英文简称"};
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }
            
            // 填充数据
            int rowNum = 1;
            for (DomainMapping mapping : mappingList) {
                Row row = sheet.createRow(rowNum++);
                int colNum = 0;
                
                createCell(row, colNum++, mapping.getDomainChineseName(), dataStyle);
                createCell(row, colNum++, mapping.getDomainEnglishAbbr(), dataStyle);
            }
            
            // 写入字节数组
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();
            
            byte[] excelBytes = outputStream.toByteArray();
            log.info("域清单映射关系导出成功，文件大小: {} bytes，记录数: {}", excelBytes.length, mappingList.size());
            
            ByteArrayResource resource = new ByteArrayResource(excelBytes);
            
            // 对文件名进行URL编码以支持中文
            String fileName = "全量域清单映射关系维护.xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(excelBytes.length)
                .body(resource);
        } catch (Exception e) {
            log.error("导出域清单映射关系失败", e);
            throw new RuntimeException("导出失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * Excel导入域清单映射关系
     * @param file Excel文件
     * @param mode 导入模式：full=全量覆盖，incremental=增量添加
     */
    @PostMapping("/import-excel")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "incremental") String mode,
            @RequestParam(value = "password", required = false) String password) {
        try {
            // 验证口令
            if (!excelImportConfig.validatePassword(password)) {
                log.warn("Excel导入失败：口令验证失败");
                return Result.error("导入失败：口令错误，请输入正确的6位数字口令");
            }
            
            log.info("开始导入域清单映射Excel文件: {}, 模式: {}", file.getOriginalFilename(), mode);
            
            // 解析Excel文件
            List<DomainMapping> dataList = parseExcel(file);
            
            if (dataList.isEmpty()) {
                return Result.error("Excel文件中没有有效数据");
            }
            
            // 全量模式：先清空现有数据
            if ("full".equals(mode)) {
                log.info("全量导入模式：清空现有数据");
                domainMappingService.remove(null);
            }
            
            // 批量保存数据
            int successCount = 0;
            int skipCount = 0;
            int errorCount = 0;
            
            for (DomainMapping mapping : dataList) {
                try {
                    // 增量模式：检查是否已存在
                    if ("incremental".equals(mode)) {
                        QueryWrapper<DomainMapping> queryWrapper = new QueryWrapper<>();
                        queryWrapper.eq("domain_chinese_name", mapping.getDomainChineseName().trim());
                        
                        long count = domainMappingService.count(queryWrapper);
                        if (count > 0) {
                            skipCount++;
                            log.info("跳过已存在的映射: {}", mapping.getDomainChineseName());
                            continue;
                        }
                    }
                    
                    // 保存数据
                    boolean success = domainMappingService.save(mapping);
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
            
            log.info("========== 导入统计 ==========");
            log.info("总数据: {} 条", dataList.size());
            log.info("成功: {} 条", successCount);
            log.info("跳过: {} 条", skipCount);
            log.info("失败: {} 条", errorCount);
            log.info("==============================");
            
            return Result.success(message.toString(), null);
            
        } catch (Exception e) {
            log.error("导入Excel失败", e);
            return Result.error("导入失败: " + e.getMessage());
        }
    }
    
    /**
     * 解析Excel文件
     * Excel格式：
     * 第1列：域中文名称（必填）
     * 第2列：域英文简称（选填）
     */
    private List<DomainMapping> parseExcel(MultipartFile file) throws Exception {
        List<DomainMapping> dataList = new ArrayList<>();
        
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
                    DomainMapping mapping = new DomainMapping();
                    
                    // 第1列：读取域中文名称（必填）
                    String domainChineseName = getCellValue(row.getCell(0));
                    if (domainChineseName == null || domainChineseName.trim().isEmpty()) {
                        log.warn("第{}行：域中文名称为空，跳过", i + 1);
                        continue;
                    }
                    mapping.setDomainChineseName(domainChineseName.trim());
                    
                    // 第2列：读取域英文简称（选填）
                    String domainEnglishAbbr = getCellValue(row.getCell(1));
                    if (domainEnglishAbbr != null && !domainEnglishAbbr.trim().isEmpty()) {
                        mapping.setDomainEnglishAbbr(domainEnglishAbbr.trim());
                    }
                    
                    mapping.setCreateTime(LocalDateTime.now());
                    mapping.setUpdateTime(LocalDateTime.now());
                    
                    log.info("第{}行解析成功: [域中文名称={}, 域英文简称={}]", 
                        i + 1, 
                        mapping.getDomainChineseName(),
                        mapping.getDomainEnglishAbbr());
                    
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
}

