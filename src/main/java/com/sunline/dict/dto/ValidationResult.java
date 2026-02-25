package com.sunline.dict.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据校验结果
 */
public class ValidationResult {
    
    /**
     * 是否通过校验
     */
    private boolean valid;
    
    /**
     * 错误信息列表
     */
    private List<ValidationError> errors;
    
    public ValidationResult() {
        this.valid = true;
        this.errors = new ArrayList<>();
    }
    
    /**
     * 添加错误
     */
    public void addError(String sheetName, String fieldName, String errorType, List<Integer> rows, String duplicateValue) {
        ValidationError error = new ValidationError();
        error.setSheetName(sheetName);
        error.setFieldName(fieldName);
        error.setErrorType(errorType);
        error.setRows(rows);
        error.setDuplicateValue(duplicateValue);
        this.errors.add(error);
        this.valid = false;
    }
    
    /**
     * 是否有错误
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    // Getter and Setter
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public List<ValidationError> getErrors() {
        return errors;
    }
    
    public void setErrors(List<ValidationError> errors) {
        this.errors = errors;
    }
    
    /**
     * 校验错误详情
     */
    public static class ValidationError {
        /**
         * Sheet页名称
         */
        private String sheetName;
        
        /**
         * 字段名称
         */
        private String fieldName;
        
        /**
         * 错误类型（duplicate: 重复）
         */
        private String errorType;
        
        /**
         * 违规的行号列表
         */
        private List<Integer> rows;
        
        /**
         * 重复的值
         */
        private String duplicateValue;
        
        // Getter and Setter
        
        public String getSheetName() {
            return sheetName;
        }
        
        public void setSheetName(String sheetName) {
            this.sheetName = sheetName;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }
        
        public String getErrorType() {
            return errorType;
        }
        
        public void setErrorType(String errorType) {
            this.errorType = errorType;
        }
        
        public List<Integer> getRows() {
            return rows;
        }
        
        public void setRows(List<Integer> rows) {
            this.rows = rows;
        }
        
        public String getDuplicateValue() {
            return duplicateValue;
        }
        
        public void setDuplicateValue(String duplicateValue) {
            this.duplicateValue = duplicateValue;
        }
    }
}

