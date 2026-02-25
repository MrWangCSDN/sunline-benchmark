package com.sunline.dict.controller;

import com.sunline.dict.common.Result;
import com.sunline.dict.entity.SysMenu;
import com.sunline.dict.entity.SysUser;
import com.sunline.dict.entity.SysUserMenuPermission;
import com.sunline.dict.service.SysMenuService;
import com.sunline.dict.service.SysUserMenuPermissionService;
import com.sunline.dict.service.SysUserService;
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
 * 认证Controller
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin
public class AuthController {
    
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private SysUserService sysUserService;
    
    @Autowired
    private SysUserMenuPermissionService permissionService;
    
    @Autowired
    private SysMenuService menuService;
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> request, HttpSession session) {
        try {
            String username = request.get("username");
            String password = request.get("password");
            
            if (username == null || username.trim().isEmpty()) {
                return Result.error("用户名不能为空");
            }
            if (password == null || password.trim().isEmpty()) {
                return Result.error("密码不能为空");
            }
            
            // 查找用户
            SysUser user = sysUserService.findByUsername(username.trim());
            if (user == null) {
                return Result.error("用户名或密码错误");
            }
            
            // 验证密码
            // 如果是admin用户且密码是默认密码，需要特殊处理（首次登录时加密）
            if ("admin".equals(username) && "Liang@201314".equals(password)) {
                // 检查密码是否已加密
                if (!user.getPassword().startsWith("$2a$")) {
                    // 密码未加密，进行加密
                    user.setPassword(sysUserService.encodePassword(password));
                    sysUserService.updateById(user);
                } else {
                    // 密码已加密，验证密码
                    if (!sysUserService.verifyPassword(password, user.getPassword())) {
                        return Result.error("用户名或密码错误");
                    }
                }
            } else {
                // 其他用户验证密码
                if (!sysUserService.verifyPassword(password, user.getPassword())) {
                    return Result.error("用户名或密码错误");
                }
            }
            
            // 登录成功，保存用户信息到Session
            session.setAttribute("user", user);
            session.setAttribute("username", user.getUsername());
            session.setAttribute("isAdmin", user.isAdmin());
            
            Map<String, Object> data = new HashMap<>();
            data.put("username", user.getUsername());
            data.put("chineseName", user.getChineseName());
            data.put("isAdmin", user.isAdmin());
            
            log.info("用户 {} 登录成功", username);
            return Result.success(data);
        } catch (Exception e) {
            log.error("登录失败", e);
            return Result.error("登录失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/current")
    public Result<Map<String, Object>> getCurrentUser(HttpSession session) {
        SysUser user = (SysUser) session.getAttribute("user");
        if (user == null) {
            return Result.error("未登录");
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("chineseName", user.getChineseName());
        data.put("isAdmin", user.isAdmin());
        
        return Result.success(data);
    }
    
    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpSession session) {
        String username = (String) session.getAttribute("username");
        session.invalidate();
        log.info("用户 {} 登出", username);
        return Result.success("登出成功");
    }
    
    /**
     * 修改密码
     */
    @PostMapping("/change-password")
    public Result<String> changePassword(@RequestBody Map<String, String> request, HttpSession session) {
        try {
            SysUser user = (SysUser) session.getAttribute("user");
            if (user == null) {
                return Result.error("未登录");
            }
            
            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");
            
            if (oldPassword == null || oldPassword.trim().isEmpty()) {
                return Result.error("原密码不能为空");
            }
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return Result.error("新密码不能为空");
            }
            if (newPassword.length() < 6) {
                return Result.error("新密码长度不能少于6位");
            }
            
            // 验证原密码
            SysUser currentUser = sysUserService.getById(user.getId());
            if (currentUser == null) {
                return Result.error("用户不存在");
            }
            
            // 特殊处理admin用户的默认密码
            if ("admin".equals(user.getUsername()) && "Liang@201314".equals(oldPassword)) {
                // 检查密码是否已加密
                if (!currentUser.getPassword().startsWith("$2a$")) {
                    // 密码未加密，直接验证
                    if (!oldPassword.equals(currentUser.getPassword())) {
                        return Result.error("原密码错误");
                    }
                } else {
                    // 密码已加密，使用BCrypt验证
                    if (!sysUserService.verifyPassword(oldPassword, currentUser.getPassword())) {
                        return Result.error("原密码错误");
                    }
                }
            } else {
                // 其他用户验证密码
                if (!sysUserService.verifyPassword(oldPassword, currentUser.getPassword())) {
                    return Result.error("原密码错误");
                }
            }
            
            // 更新密码
            boolean success = sysUserService.resetPassword(user.getId(), newPassword);
            if (success) {
                log.info("用户 {} 修改密码成功", user.getUsername());
                return Result.success("密码修改成功");
            } else {
                return Result.error("密码修改失败");
            }
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return Result.error("修改密码失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取当前登录用户的菜单权限（以menuCode为key）
     */
    @GetMapping("/menu-permissions")
    public Result<Map<String, String>> getMenuPermissions(HttpSession session) {
        SysUser user = (SysUser) session.getAttribute("user");
        if (user == null) {
            return Result.error("未登录");
        }
        
        try {
            Map<String, String> permissions = new HashMap<>();
            
            // 如果是超级管理员，所有菜单都有读写权限
            if (user.isAdmin()) {
                // 获取所有菜单
                List<SysMenu> allMenus = menuService.list();
                for (SysMenu menu : allMenus) {
                    if (menu.getMenuCode() != null) {
                        permissions.put(menu.getMenuCode(), "READ_WRITE");
                    }
                }
            } else {
                // 获取用户的菜单权限
                List<SysUserMenuPermission> permissionList = permissionService.getPermissionsByUserId(user.getId());
                
                // 根据menuId查询menuCode并构建映射
                for (SysUserMenuPermission permission : permissionList) {
                    SysMenu menu = menuService.getById(permission.getMenuId());
                    if (menu != null && menu.getMenuCode() != null) {
                        permissions.put(menu.getMenuCode(), permission.getPermissionType());
                    }
                }
            }
            
            return Result.success(permissions);
        } catch (Exception e) {
            log.error("获取菜单权限失败", e);
            return Result.error("获取菜单权限失败: " + e.getMessage());
        }
    }
}

