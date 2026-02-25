package com.sunline.dict.common;

/**
 * 导入进度信息
 */
public class ImportProgress {
    
    /**
     * 总行数
     */
    private int total;
    
    /**
     * 已处理行数
     */
    private int current;
    
    /**
     * 进度百分比
     */
    private int percentage;
    
    /**
     * 状态: parsing(解析中), importing(导入中), completed(完成), error(错误)
     */
    private String status;
    
    /**
     * 消息
     */
    private String message;
    
    public ImportProgress() {
    }
    
    public ImportProgress(int total, int current, String status, String message) {
        this.total = total;
        this.current = current;
        this.percentage = total > 0 ? (current * 100 / total) : 0;
        this.status = status;
        this.message = message;
    }
    
    // Getter and Setter methods
    
    public int getTotal() {
        return total;
    }
    
    public void setTotal(int total) {
        this.total = total;
    }
    
    public int getCurrent() {
        return current;
    }
    
    public void setCurrent(int current) {
        this.current = current;
    }
    
    public int getPercentage() {
        return percentage;
    }
    
    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}

