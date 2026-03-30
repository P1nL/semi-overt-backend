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
 * Spring Security 用户信息加载服务。
 * 当前项目主要走 JWT 无状态认证，正常请求链路中通常不会主动调用这里。
 * 保留该 Bean 的目的，是满足 Spring Security 自动装配要求，并为后续可能接入的标准认证流程提供兼容点。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    /**
     * 按用户名加载用户详情，供 Spring Security 框架调用。
     * 返回值中的角色会转换成 `ROLE_*` 形式，以匹配 `hasRole()` 等授权表达式。
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new UsernameNotFoundException("鐢ㄦ埛涓嶅瓨鍦? " + username);
        }

        // 将项目内的用户角色转换为 Spring Security 可识别的 GrantedAuthority。
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
