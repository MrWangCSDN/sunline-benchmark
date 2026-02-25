package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统用户实体类
 */
@TableName("sys_user")
public class SysUser implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String username;           // 用户名
    private String password;           // 密码（加密后）
    private String chineseName;        // 用户中文名
    private String gitUsername;        // Git用户名
    private Integer isAdmin;           // 是否超级管理员：0-否，1-是
    private Integer status;            // 状态：0-禁用，1-启用
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getChineseName() {
        return chineseName;
    }
    
    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }
    
    public String getGitUsername() {
        return gitUsername;
    }
    
    public void setGitUsername(String gitUsername) {
        this.gitUsername = gitUsername;
    }
    
    public Integer getIsAdmin() {
        return isAdmin;
    }
    
    public void setIsAdmin(Integer isAdmin) {
        this.isAdmin = isAdmin;
    }
    
    public Integer getStatus() {
        return status;
    }
    
    public void setStatus(Integer status) {
        this.status = status;
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
    
    /**
     * 判断是否为超级管理员
     */
    public boolean isAdmin() {
        return isAdmin != null && isAdmin == 1;
    }
}

