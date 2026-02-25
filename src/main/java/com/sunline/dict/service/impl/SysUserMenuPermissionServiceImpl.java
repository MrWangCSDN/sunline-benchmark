package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.SysUserMenuPermission;
import com.sunline.dict.mapper.SysUserMenuPermissionMapper;
import com.sunline.dict.service.SysUserMenuPermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户菜单权限服务实现
 */
@Service
public class SysUserMenuPermissionServiceImpl extends ServiceImpl<SysUserMenuPermissionMapper, SysUserMenuPermission> 
        implements SysUserMenuPermissionService {
    
    @Override
    public List<SysUserMenuPermission> getPermissionsByUserId(Long userId) {
        LambdaQueryWrapper<SysUserMenuPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserMenuPermission::getUserId, userId);
        return this.list(wrapper);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveUserPermissions(Long userId, Map<Long, String> permissions) {
        // 删除用户的所有旧权限
        LambdaQueryWrapper<SysUserMenuPermission> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(SysUserMenuPermission::getUserId, userId);
        this.remove(deleteWrapper);
        
        // 如果没有新权限，直接返回成功
        if (permissions == null || permissions.isEmpty()) {
            return true;
        }
        
        // 批量插入新权限
        List<SysUserMenuPermission> permissionList = new ArrayList<>();
        for (Map.Entry<Long, String> entry : permissions.entrySet()) {
            Long menuId = entry.getKey();
            String permissionType = entry.getValue();
            
            // 只保存有效的权限类型
            if (menuId != null && ("READ_ONLY".equals(permissionType) || "READ_WRITE".equals(permissionType))) {
                SysUserMenuPermission permission = new SysUserMenuPermission();
                permission.setUserId(userId);
                permission.setMenuId(menuId);
                permission.setPermissionType(permissionType);
                permission.setCreateTime(LocalDateTime.now());
                permission.setUpdateTime(LocalDateTime.now());
                permissionList.add(permission);
            }
        }
        
        if (!permissionList.isEmpty()) {
            return this.saveBatch(permissionList);
        }
        
        return true;
    }
    
    @Override
    public String getPermissionType(Long userId, Long menuId) {
        if (userId == null || menuId == null) {
            return null;
        }
        
        LambdaQueryWrapper<SysUserMenuPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserMenuPermission::getUserId, userId)
               .eq(SysUserMenuPermission::getMenuId, menuId);
        SysUserMenuPermission permission = this.getOne(wrapper);
        return permission != null ? permission.getPermissionType() : null;
    }
    
    @Override
    public boolean hasMenuPermission(Long userId, String menuCode) {
        // 首先需要根据menuCode查找menuId，这里需要注入SysMenuService
        // 简化处理：直接查询是否存在权限记录
        // 实际应该先根据menuCode查menuId，再查权限
        return false; // 暂时返回false，需要在Controller层处理
    }
    
    @Override
    public boolean hasWritePermission(Long userId, String menuCode) {
        // 类似hasMenuPermission，需要根据menuCode查找权限类型
        return false; // 暂时返回false，需要在Controller层处理
    }
}

