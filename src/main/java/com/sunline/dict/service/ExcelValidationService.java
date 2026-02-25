package com.sunline.dict.service;

import com.sunline.dict.dto.ValidationResult;
import com.sunline.dict.entity.CodeExtensionData;
import com.sunline.dict.entity.DictData;
import com.sunline.dict.entity.DomainData;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Collections;

/**
 * Excel数据校验服务
 */
@Service
public class ExcelValidationService {
    
    /**
     * 校验字典数据
     * 规则：中文名称、数据项编号、英文简称、JAVA/ESF规范命名 都不能重复
     */
    public ValidationResult validateDictData(List<DictData> dataList) {
        ValidationResult result = new ValidationResult();
        
        if (dataList == null || dataList.isEmpty()) {
            return result;
        }
        
        // 1. 校验中文名称不能重复
        validateDuplicateField(result, dataList, "字典技术衍生表", "中文名称", 
            DictData::getChineseName);
        
        // 2. 校验数据项编号不能重复
        validateDuplicateField(result, dataList, "字典技术衍生表", "数据项编号", 
            DictData::getDataItemCode);
        
        // 3. 校验英文简称不能重复
        validateDuplicateField(result, dataList, "字典技术衍生表", "英文简称", 
            DictData::getEnglishAbbr);
        
        // 4. 校验JAVA/ESF规范命名不能重复
        validateDuplicateField(result, dataList, "字典技术衍生表", "JAVA/ESF规范命名", 
            DictData::getJavaEsfName);
        
        // 5. 校验JAVA/ESF规范命名是否符合Java命名规范
        validateJavaNamingConvention(result, dataList, "字典技术衍生表", "JAVA/ESF规范命名", 
            DictData::getJavaEsfName);
        
        return result;
    }
    
    /**
     * 校验域清单数据
     * 规则：域中文名称不能重复
     */
    public ValidationResult validateDomainData(List<DomainData> dataList) {
        ValidationResult result = new ValidationResult();
        
        if (dataList == null || dataList.isEmpty()) {
            return result;
        }
        
        // 校验域中文名称不能重复
        validateDuplicateField(result, dataList, "域清单", "域中文名称", 
            DomainData::getChineseName);
        
        return result;
    }
    
    /**
     * 校验代码扩展清单数据
     * 规则：B列-代码域中文名称 + C列-代码取值 作为唯一键，不能重复
     */
    public ValidationResult validateCodeExtensionData(List<CodeExtensionData> dataList) {
        ValidationResult result = new ValidationResult();
        
        if (dataList == null || dataList.isEmpty()) {
            return result;
        }
        
        // 检查"代码域中文名称 + 代码取值"的唯一性
        Map<String, List<Integer>> compositeKeyMap = new HashMap<>();
        
        for (int i = 0; i < dataList.size(); i++) {
            CodeExtensionData data = dataList.get(i);
            String domainName = data.getCodeDomainChineseName();
            String codeValue = data.getCodeValue();
            
            // 只有两个字段都不为空时才检查
            if (domainName != null && !domainName.trim().isEmpty() &&
                codeValue != null && !codeValue.trim().isEmpty()) {
                
                // 组合键：代码域中文名称|代码取值
                String compositeKey = domainName.trim() + "|" + codeValue.trim();
                
                compositeKeyMap.computeIfAbsent(compositeKey, k -> new ArrayList<>())
                    .add(i + 2); // +2 因为Excel从第2行开始（第1行是表头）
            }
        }
        
        // 找出重复的组合键
        for (Map.Entry<String, List<Integer>> entry : compositeKeyMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                String[] parts = entry.getKey().split("\\|");
                String displayValue = String.format("代码域：%s，代码取值：%s", parts[0], parts[1]);
                
                result.addError(
                    "代码扩展清单", 
                    "代码域中文名称+代码取值", 
                    "duplicate", 
                    entry.getValue(), 
                    displayValue
                );
            }
        }
        
        return result;
    }
    
    /**
     * 通用的重复字段校验方法
     */
    private <T> void validateDuplicateField(
            ValidationResult result, 
            List<T> dataList, 
            String sheetName, 
            String fieldName,
            java.util.function.Function<T, String> fieldExtractor) {
        
        Map<String, List<Integer>> valueRowMap = new HashMap<>();
        
        for (int i = 0; i < dataList.size(); i++) {
            T data = dataList.get(i);
            String value = fieldExtractor.apply(data);
            
            if (value != null && !value.isEmpty()) {
                valueRowMap.computeIfAbsent(value, k -> new ArrayList<>())
                    .add(i + 2); // +2 因为Excel从第2行开始（第1行是表头）
            }
        }
        
        // 找出重复的值
        for (Map.Entry<String, List<Integer>> entry : valueRowMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.addError(
                    sheetName, 
                    fieldName, 
                    "duplicate", 
                    entry.getValue(), 
                    entry.getKey()
                );
            }
        }
    }
    
    /**
     * 校验Java命名规范
     * 规则：
     * 1. 不能以数字开头
     * 2. 不能是Java关键字
     * 3. 必须符合Java标识符规范（可以创建类名和方法名时不报错）
     */
    private <T> void validateJavaNamingConvention(
            ValidationResult result, 
            List<T> dataList, 
            String sheetName, 
            String fieldName,
            java.util.function.Function<T, String> fieldExtractor) {
        
        for (int i = 0; i < dataList.size(); i++) {
            T data = dataList.get(i);
            String value = fieldExtractor.apply(data);
            
            if (value != null && !value.trim().isEmpty()) {
                String trimmedValue = value.trim();
                if (!isValidJavaName(trimmedValue)) {
                    result.addError(
                        sheetName, 
                        fieldName, 
                        "invalid_java_naming", 
                        Collections.singletonList(i + 2), 
                        trimmedValue + "（不符合Java命名规范）"
                    );
                }
            }
        }
    }
    
    /**
     * 检查是否符合Java命名规范
     * 规则：只需要保证创建类名和方法名时不报错即可
     * 1. 不能以数字开头
     * 2. 不能是Java关键字
     * 3. 必须符合Java标识符规范（字母、数字、下划线、美元符号）
     */
    private boolean isValidJavaName(String name) {
        if (name == null || name.isEmpty()) {
            return true; // 空值不校验
        }
        
        // 1. 不能以数字开头
        if (Character.isDigit(name.charAt(0))) {
            return false;
        }
        
        // 2. 检查是否是Java关键字
        if (isJavaKeyword(name)) {
            return false;
        }
        
        // 3. 检查是否符合Java标识符规范
        // Java标识符：必须以字母、下划线(_)或美元符号($)开头
        char firstChar = name.charAt(0);
        if (!Character.isLetter(firstChar) && firstChar != '_' && firstChar != '$') {
            return false;
        }
        
        // 后续字符可以是字母、数字、下划线或美元符号
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 检查是否是Java关键字
     */
    private boolean isJavaKeyword(String name) {
        // Java关键字列表
        String[] keywords = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new", "package",
            "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient",
            "try", "void", "volatile", "while", "true", "false", "null"
        };
        
        for (String keyword : keywords) {
            if (keyword.equals(name)) {
                return true;
            }
        }
        
        return false;
    }
}

