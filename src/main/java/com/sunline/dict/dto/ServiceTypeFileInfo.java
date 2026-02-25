package com.sunline.dict.dto;

/**
 * ServiceType文件信息
 * 用于缓存jar包中的serviceType文件信息
 */
public class ServiceTypeFileInfo {
    
    /**
     * 文件全名（带扩展名）
     */
    private String fileName;
    
    /**
     * 文件路径（servicetype下的相对路径）
     */
    private String filePath;
    
    /**
     * jar包名称
     */
    private String jarName;
    
    public ServiceTypeFileInfo() {
    }
    
    public ServiceTypeFileInfo(String fileName, String filePath, String jarName) {
        this.fileName = fileName;
        this.filePath = filePath;
        this.jarName = jarName;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public String getJarName() {
        return jarName;
    }
    
    public void setJarName(String jarName) {
        this.jarName = jarName;
    }
    
    @Override
    public String toString() {
        return "ServiceTypeFileInfo{" +
                "fileName='" + fileName + '\'' +
                ", filePath='" + filePath + '\'' +
                ", jarName='" + jarName + '\'' +
                '}';
    }
}

