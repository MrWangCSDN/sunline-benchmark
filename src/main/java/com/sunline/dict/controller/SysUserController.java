package com.sunline.dict.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunline.dict.common.Result;
import com.sunline.dict.entity.SysUser;
import com.sunline.dict.service.SysUserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统用户管理Controller
 */
@RestController
@RequestMapping("/api/user")
@CrossOrigin
public class SysUserController {
    
    private static final Logger log = LoggerFactory.getLogger(SysUserController.class);
    
    @Autowired
    private SysUserService sysUserService;
    
    /**
     * 检查是否为管理员
     */
    private boolean isAdmin(HttpSession session) {
        Boolean isAdmin = (Boolean) session.getAttribute("isAdmin");
        return isAdmin != null && isAdmin;
    }
    
    /**
     * 分页查询用户列表
     */
    @GetMapping("/page")
    public Result<Page<SysUser>> getUserPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            HttpSession session) {
        
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        try {
            Page<SysUser> page = sysUserService.getUserPage(current, size, keyword);
            return Result.success(page);
        } catch (Exception e) {
            log.error("查询用户列表失败", e);
            return Result.error("查询用户列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加用户
     */
    @PostMapping("/add")
    public Result<String> addUser(@RequestBody SysUser user, HttpSession session) {
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        try {
            if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
                return Result.error("用户名不能为空");
            }
            
            // 检查用户名是否已存在
            if (sysUserService.existsByUsername(user.getUsername().trim())) {
                return Result.error("用户名已存在");
            }
            
            // 设置默认密码（如果未提供）
            if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
                user.setPassword("Spdb@123"); // 默认密码
            }
            
            // 加密密码
            user.setPassword(sysUserService.encodePassword(user.getPassword()));
            
            // 设置默认值
            if (user.getStatus() == null) {
                user.setStatus(1); // 默认启用
            }
            if (user.getIsAdmin() == null) {
                user.setIsAdmin(0); // 默认非管理员
            }
            
            sysUserService.save(user);
            log.info("管理员 {} 添加用户: {}", session.getAttribute("username"), user.getUsername());
            return Result.success("添加用户成功");
        } catch (Exception e) {
            log.error("添加用户失败", e);
            return Result.error("添加用户失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新用户
     */
    @PostMapping("/update")
    public Result<String> updateUser(@RequestBody SysUser user, HttpSession session) {
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        try {
            if (user.getId() == null) {
                return Result.error("用户ID不能为空");
            }
            
            SysUser existingUser = sysUserService.getById(user.getId());
            if (existingUser == null) {
                return Result.error("用户不存在");
            }
            
            // 不允许修改用户名
            user.setUsername(null);
            // 不允许通过此接口修改密码（使用重置密码接口）
            user.setPassword(null);
            
            sysUserService.updateById(user);
            log.info("管理员 {} 更新用户: {}", session.getAttribute("username"), user.getId());
            return Result.success("更新用户成功");
        } catch (Exception e) {
            log.error("更新用户失败", e);
            return Result.error("更新用户失败: " + e.getMessage());
        }
    }
    
    /**
     * 删除用户
     */
    @PostMapping("/delete")
    public Result<String> deleteUser(@RequestParam Long id, HttpSession session) {
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        try {
            SysUser user = sysUserService.getById(id);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            // 不允许删除admin用户
            if ("admin".equals(user.getUsername())) {
                return Result.error("不能删除超级管理员");
            }
            
            sysUserService.removeById(id);
            log.info("管理员 {} 删除用户: {}", session.getAttribute("username"), id);
            return Result.success("删除用户成功");
        } catch (Exception e) {
            log.error("删除用户失败", e);
            return Result.error("删除用户失败: " + e.getMessage());
        }
    }
    
    /**
     * 重置密码
     */
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@RequestBody Map<String, Object> request, HttpSession session) {
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        try {
            Long userId = Long.valueOf(request.get("id").toString());
            String newPassword = (String) request.get("password");
            
            // 如果未提供密码，使用默认密码
            if (newPassword == null || newPassword.trim().isEmpty()) {
                newPassword = "Spdb@123"; // 默认密码
            }
            
            boolean success = sysUserService.resetPassword(userId, newPassword);
            if (success) {
                log.info("管理员 {} 重置用户 {} 的密码", session.getAttribute("username"), userId);
                return Result.success("重置密码成功，新密码为: Spdb@123");
            } else {
                return Result.error("重置密码失败");
            }
        } catch (Exception e) {
            log.error("重置密码失败", e);
            return Result.error("重置密码失败: " + e.getMessage());
        }
    }
    
    /**
     * 权限配置（已迁移到 SysUserMenuPermissionController）
     * 保留此接口以兼容旧的前端代码
     */
    @PostMapping("/permission")
    public Result<String> configurePermission(@RequestBody Map<String, Object> request, HttpSession session) {
        if (!isAdmin(session)) {
            return Result.error("无权限访问");
        }
        
        log.warn("使用了旧的权限配置接口，建议使用 /api/user-menu-permission/save 接口");
        return Result.success("权限配置功能已迁移，请使用新的权限配置接口");
    }
}

