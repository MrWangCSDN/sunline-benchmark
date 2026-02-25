package com.sunline.dict.common;

import java.io.Serializable;

/**
 * 统一响应结果
 */
public class Result<T> implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 状态码
     */
    private Integer code;
    
    /**
     * 提示信息
     */
    private String message;
    
    /**
     * 数据
     */
    private T data;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    public Result() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getter and Setter methods
    
    public Integer getCode() {
        return code;
    }
    
    public void setCode(Integer code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    /**
     * 成功
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }
    
    /**
     * 成功
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }
    
    /**
     * 成功
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }
    
    /**
     * 失败
     */
    public static <T> Result<T> error() {
        return new Result<>(500, "操作失败", null);
    }
    
    /**
     * 失败
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
    
    /**
     * 失败
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}

