package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.entity.User;
import com.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Security 用户信息加载服务
 *
 * 职责说明：
 *   本项目使用 JWT 无状态认证，正常请求流程中 Spring Security 不会主动调用此方法。
 *   此类的存在是为了：
 *   1. 满足 Spring Security 自动装配的要求（必须存在 UserDetailsService Bean）
 *   2. 在需要基于 AuthenticationManager 认证时（如后续可能引入的 OAuth2 场景）提供支持
 *
 * 注意：登录逻辑在 AuthServiceImpl 中手动实现（查库 + BCrypt 校验），不经过此类。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    /**
     * 按用户名加载用户（Spring Security 框架调用）
     *
     * @param username 用户名（此处也可传邮箱，视调用方而定）
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 将自定义 UserRole 转换为 Spring Security 的 GrantedAuthority
        // 格式：ROLE_USER / ROLE_ADMIN（与 SecurityConfig 中 hasRole() 匹配）
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                authorities
        );
    }
}