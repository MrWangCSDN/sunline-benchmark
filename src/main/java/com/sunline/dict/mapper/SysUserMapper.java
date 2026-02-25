package com.sunline.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunline.dict.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}

