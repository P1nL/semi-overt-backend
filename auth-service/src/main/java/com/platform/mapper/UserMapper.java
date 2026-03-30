package com.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.platform.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问接口，负责相关表的持久化读写。
 */

@Mapper
public interface UserMapper extends BaseMapper<User> {
}