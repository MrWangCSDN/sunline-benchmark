package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易信息实体类
 */
@TableName("flowtran")
public class Flowtran implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 交易ID
     */
    @TableId(type = IdType.INPUT)
    private String id;
    
    /**
     * 交易名称
     */
    private String longname;
    
    /**
     * 包路径
     */
    private String packagePath;
    
    /**
     * 事务模式
     */
    private String txnMode;
    
    /**
     * 来源jar包
     */
    private String fromJar;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    // Getter and Setter methods
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getLongname() {
        return longname;
    }
    
    public void setLongname(String longname) {
        this.longname = longname;
    }
    
    public String getPackagePath() {
        return packagePath;
    }
    
    public void setPackagePath(String packagePath) {
        this.packagePath = packagePath;
    }
    
    public String getTxnMode() {
        return txnMode;
    }
    
    public void setTxnMode(String txnMode) {
        this.txnMode = txnMode;
    }
    
    public String getFromJar() {
        return fromJar;
    }
    
    public void setFromJar(String fromJar) {
        this.fromJar = fromJar;
    }
    
    public LocalDateTime getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
    
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
    
    @Override
    public String toString() {
        return "Flowtran{" +
                "id='" + id + '\'' +
                ", longname='" + longname + '\'' +
                ", packagePath='" + packagePath + '\'' +
                ", txnMode='" + txnMode + '\'' +
                ", fromJar='" + fromJar + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                '}';
    }
}

