package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.SysUserMenuPermission;

import java.util.List;
import java.util.Map;

/**
 * 用户菜单权限服务接口
 */
public interface SysUserMenuPermissionService extends IService<SysUserMenuPermission> {
    
    /**
     * 根据用户ID查询所有菜单权限
     */
    List<SysUserMenuPermission> getPermissionsByUserId(Long userId);
    
    /**
     * 保存用户菜单权限（先删除旧权限，再保存新权限）
     * @param userId 用户ID
     * @param permissions 权限列表，格式：{menuId: permissionType}
     */
    boolean saveUserPermissions(Long userId, Map<Long, String> permissions);
    
    /**
     * 根据用户ID和菜单ID查询权限类型
     * @return READ_ONLY, READ_WRITE, 或 null（无权限）
     */
    String getPermissionType(Long userId, Long menuId);
    
    /**
     * 检查用户是否有菜单权限
     */
    boolean hasMenuPermission(Long userId, String menuCode);
    
    /**
     * 检查用户是否有菜单的写权限
     */
    boolean hasWritePermission(Long userId, String menuCode);
}

