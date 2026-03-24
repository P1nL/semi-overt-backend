package com.platform.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Security 上下文工具类
 * JwtAuthFilter 将 userId 放入 Authentication.principal
 * Controller 层通过此工具类获取当前登录用户 ID 和角色
 */
public class SecurityUtils {

    /**
     * 获取当前登录用户 ID
     * 未登录时返回 null
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof Long) return (Long) principal;
        return null;
    }

    /**
     * 判断当前用户是否是管理员
     */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    /**
     * 判断当前用户是否已登录
     */
    public static boolean isAuthenticated() {
        return getCurrentUserId() != null;
    }
}