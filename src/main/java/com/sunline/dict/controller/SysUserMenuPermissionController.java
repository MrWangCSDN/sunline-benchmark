package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.entity.SysMenu;
import com.sunline.dict.entity.SysUserMenuPermission;
import com.sunline.dict.service.SysMenuService;
import com.sunline.dict.service.SysUserMenuPermissionService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户菜单权限管理Controller
 */
@RestController
@RequestMapping("/api/user-menu-permission")
@CrossOrigin
public class SysUserMenuPermissionController {
    
    private static final Logger log = LoggerFactory.getLogger(SysUserMenuPermissionController.class);
    
    @Autowired
    private SysUserMenuPermissionService permissionService;
    
    @Autowired
    private SysMenuService menuService;
    
    /**
     * 检查是否为管理员
     */
    private boolean isAdmin(HttpSession session) {
        Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");
        return isAdmin != null && isAdmin;
    }
    
    /**
     * 获取用户的菜单权限树（包含权限信息）
     */
    @GetMapping("/tree/{userId}")
    public Result<Map<String, Object>> getUserMenuPermissionTree(@PathVariable Long userId, HttpSession session) {
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        try {
            // 获取所有菜单树
            List<SysMenu> menuTree = menuService.getAllEnabledMenusTree();
            
            // 获取用户的权限
            List<SysUserMenuPermission> permissions = permissionService.getPermissionsByUserId(userId);
            Map<Long, String> permissionMap = permissions.stream()
                .collect(Collectors.toMap(
                    SysUserMenuPermission::getMenuId,
                    SysUserMenuPermission::getPermissionType
                ));
            
            // 为菜单树添加权限信息
            addPermissionToMenuTree(menuTree, permissionMap);
            
            Map<String, Object> result = new HashMap<>();
            result.put("menuTree", menuTree);
            result.put("permissions", permissionMap);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("查询用户菜单权限失败", e);
            return Result.error("查询用户菜单权限失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存用户的菜单权限
     */
    @PostMapping("/save")
    public Result<String> saveUserPermissions(@RequestBody Map<String, Object> request, HttpSession session) {
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            @SuppressWarnings("unchecked")
            Map<String, String> permissions = (Map<String, String>) request.get("permissions");
            
            // 转换权限数据格式：从 {menuId: permissionType} 转换为 {Long menuId: String permissionType}
            Map<Long, String> permissionMap = new HashMap<>();
            if (permissions != null) {
                for (Map.Entry<String, String> entry : permissions.entrySet()) {
                    try {
                        Long menuId = Long.valueOf(entry.getKey());
                        String permissionType = entry.getValue();
                        permissionMap.put(menuId, permissionType);
                    } catch (NumberFormatException e) {
                        log.warn("无效的菜单ID: {}", entry.getKey());
                    }
                }
            }
            
            boolean success = permissionService.saveUserPermissions(userId, permissionMap);
            if (success) {
                log.info("管理员 {} 保存用户 {} 的菜单权限", session.getAttribute("username"), userId);
                return Result.success("保存权限成功");
            } else {
                return Result.error("保存权限失败");
            }
        } catch (Exception e) {
            log.error("保存用户菜单权限失败", e);
            return Result.error("保存用户菜单权限失败: " + e.getMessage());
        }
    }
    
    /**
     * 递归为菜单树添加权限信息
     */
    private void addPermissionToMenuTree(List<SysMenu> menus, Map<Long, String> permissionMap) {
        for (SysMenu menu : menus) {
            // 添加权限信息
            String permissionType = permissionMap.get(menu.getId());
            if (permissionType != null) {
                // 可以将权限信息存储在菜单对象的某个字段中，但需要修改实体类
                // 这里暂时不做处理，前端可以根据权限Map来显示
            }
            
            // 递归处理子菜单
            if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
                addPermissionToMenuTree(menu.getChildren(), permissionMap);
            }
        }
    }
}

