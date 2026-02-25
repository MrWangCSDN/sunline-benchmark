package com.sunline.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统菜单实体类
 */
@TableName("sys_menu")
public class SysMenu implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private String menuCode;        // 菜单编码（用于前端路由标识）
    private String menuName;        // 菜单名称
    private Long parentId;          // 父菜单ID，0表示顶级菜单
    private Integer menuType;       // 菜单类型：1-父菜单，2-子菜单
    private String icon;            // 菜单图标
    private Integer sortOrder;      // 排序顺序
    private Integer status;         // 状态：0-禁用，1-启用
    private LocalDateTime createTime;   // 创建时间
    private LocalDateTime updateTime;   // 更新时间
    
    // 前端展示用的字段（不映射到数据库）
    private transient List<SysMenu> children;  // 子菜单列表
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getMenuCode() {
        return menuCode;
    }
    
    public void setMenuCode(String menuCode) {
        this.menuCode = menuCode;
    }
    
    public String getMenuName() {
        return menuName;
    }
    
    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }
    
    public Long getParentId() {
        return parentId;
    }
    
    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
    
    public Integer getMenuType() {
        return menuType;
    }
    
    public void setMenuType(Integer menuType) {
        this.menuType = menuType;
    }
    
    public String getIcon() {
        return icon;
    }
    
    public void setIcon(String icon) {
        this.icon = icon;
    }
    
    public Integer getSortOrder() {
        return sortOrder;
    }
    
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
    
    public List<SysMenu> getChildren() {
        return children;
    }
    
    public void setChildren(List<SysMenu> children) {
        this.children = children;
    }
}

