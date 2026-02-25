package com.sunline.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sunline.dict.entity.SysMenu;
import com.sunline.dict.mapper.SysMenuMapper;
import com.sunline.dict.service.SysMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统菜单服务实现
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {
    
    
    @Override
    public List<SysMenu> getAllMenusTree() {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysMenu::getSortOrder);
        List<SysMenu> allMenus = this.list(wrapper);
        return buildMenuTree(allMenus);
    }
    
    @Override
    public List<SysMenu> getAllEnabledMenusTree() {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenu::getStatus, 1);
        wrapper.orderByAsc(SysMenu::getSortOrder);
        List<SysMenu> allMenus = this.list(wrapper);
        return buildMenuTree(allMenus);
    }
    
    @Override
    public List<SysMenu> getMenusTreeByUserId(Long userId) {
        // 如果用户是超级管理员，返回所有菜单
        // 这里需要注入SysUserService来检查，暂时返回所有启用的菜单
        // 实际权限过滤应该在Controller层根据用户权限表进行过滤
        return getAllEnabledMenusTree();
    }
    
    /**
     * 构建菜单树
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> allMenus) {
        // 按parentId分组
        Map<Long, List<SysMenu>> menuMap = allMenus.stream()
            .filter(menu -> menu.getParentId() != null && menu.getParentId() != 0)
            .collect(Collectors.groupingBy(SysMenu::getParentId));
        
        // 获取所有父菜单
        List<SysMenu> parentMenus = allMenus.stream()
            .filter(menu -> menu.getParentId() == null || menu.getParentId() == 0)
            .sorted((m1, m2) -> {
                int order1 = m1.getSortOrder() != null ? m1.getSortOrder() : 0;
                int order2 = m2.getSortOrder() != null ? m2.getSortOrder() : 0;
                return order1 - order2;
            })
            .collect(Collectors.toList());
        
        // 为每个父菜单设置子菜单
        for (SysMenu parentMenu : parentMenus) {
            List<SysMenu> children = menuMap.getOrDefault(parentMenu.getId(), new ArrayList<>());
            children.sort((m1, m2) -> {
                int order1 = m1.getSortOrder() != null ? m1.getSortOrder() : 0;
                int order2 = m2.getSortOrder() != null ? m2.getSortOrder() : 0;
                return order1 - order2;
            });
            parentMenu.setChildren(children);
        }
        
        return parentMenus;
    }
}

