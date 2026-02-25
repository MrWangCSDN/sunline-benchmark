package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.SysMenu;

import java.util.List;

/**
 * 系统菜单服务接口
 */
public interface SysMenuService extends IService<SysMenu> {
    
    /**
     * 查询所有菜单（树形结构）
     */
    List<SysMenu> getAllMenusTree();
    
    /**
     * 查询所有启用的菜单（树形结构）
     */
    List<SysMenu> getAllEnabledMenusTree();
    
    /**
     * 根据用户ID查询用户有权限的菜单（树形结构）
     */
    List<SysMenu> getMenusTreeByUserId(Long userId);
}

