package com.sunline.dict.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sunline.dict.entity.SysUser;

/**
 * 系统用户服务接口
 */
public interface SysUserService extends IService<SysUser> {
    
    /**
     * 根据用户名查找用户
     */
    SysUser findByUsername(String username);
    
    /**
     * 验证用户密码
     */
    boolean verifyPassword(String rawPassword, String encodedPassword);
    
    /**
     * 加密密码
     */
    String encodePassword(String rawPassword);
    
    /**
     * 分页查询用户列表
     */
    Page<SysUser> getUserPage(int current, int size, String keyword);
    
    /**
     * 重置用户密码
     */
    boolean resetPassword(Long userId, String newPassword);
    
    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);
}

