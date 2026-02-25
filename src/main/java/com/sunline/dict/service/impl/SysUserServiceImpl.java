package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.SysUser;
import com.sunline.dict.mapper.SysUserMapper;
import com.sunline.dict.service.SysUserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 系统用户服务实现
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Override
    public SysUser findByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        wrapper.eq(SysUser::getStatus, 1); // 只查询启用状态的用户
        return this.getOne(wrapper);
    }
    
    @Override
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    @Override
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
    
    @Override
    public Page<SysUser> getUserPage(int current, int size, String keyword) {
        Page<SysUser> page = new Page<>(current, size);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w
                .like(SysUser::getUsername, keyword)
                .or()
                .like(SysUser::getChineseName, keyword)
                .or()
                .like(SysUser::getGitUsername, keyword)
            );
        }
        
        wrapper.orderByDesc(SysUser::getCreateTime);
        return this.page(page, wrapper);
    }
    
    @Override
    public boolean resetPassword(Long userId, String newPassword) {
        SysUser user = this.getById(userId);
        if (user == null) {
            return false;
        }
        user.setPassword(encodePassword(newPassword));
        return this.updateById(user);
    }
    
    @Override
    public boolean existsByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        return this.count(wrapper) > 0;
    }
}

