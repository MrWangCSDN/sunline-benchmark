package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.entity.SysMenu;
import com.sunline.dict.service.SysMenuService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统菜单管理Controller
 */
@RestController
@RequestMapping("/api/menu")
@CrossOrigin
public class SysMenuController {
    
    private static final Logger log = LoggerFactory.getLogger(SysMenuController.class);
    
    @Autowired
    private SysMenuService sysMenuService;
    
    /**
     * 检查是否为管理员
     */
    private boolean isAdmin(HttpSession session) {
        Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");
        return isAdmin != null && isAdmin;
    }
    
    /**
     * 获取所有菜单（树形结构）
     */
    @GetMapping("/tree")
    public Result<List<SysMenu>> getMenuTree(HttpSession session) {
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        try {
            List<SysMenu> menus = sysMenuService.getAllEnabledMenusTree();
            return Result.success(menus);
        } catch (Exception e) {
            log.error("查询菜单树失败", e);
            return Result.error("查询菜单树失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户ID获取用户的菜单权限（树形结构）
     */
    @GetMapping("/tree/{userId}")
    public Result<List<SysMenu>> getMenuTreeByUserId(@PathVariable Long userId, HttpSession session) {
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        try {
            List<SysMenu> menus = sysMenuService.getMenusTreeByUserId(userId);
            return Result.success(menus);
        } catch (Exception e) {
            log.error("查询用户菜单权限失败", e);
            return Result.error("查询用户菜单权限失败: " + e.getMessage());
        }
    }
}

