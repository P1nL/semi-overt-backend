package com.platform.kernel.util;

import com.platform.kernel.context.UserContext;
import com.platform.kernel.context.UserContextHolder;

/**
 * 安全上下文工具类，提供当前登录用户信息的读取能力。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户 ID；未登录时返回 {@code null}。
     */
    public static Long getCurrentUserId() {
        UserContext context = UserContextHolder.get();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 获取当前登录用户名；未登录时返回 {@code null}。
     */
    public static String getCurrentUsername() {
        UserContext context = UserContextHolder.get();
        return context != null ? context.getUsername() : null;
    }

    /**
     * 获取当前登录角色；未登录时返回 {@code null}。
     */
    public static String getCurrentRole() {
        UserContext context = UserContextHolder.get();
        return context != null ? context.getRole() : null;
    }

    /**
     * 判断当前用户是否具备管理员角色。
     */
    public static boolean isAdmin() {
        String role = getCurrentRole();
        return "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    /**
     * 判断当前请求是否已经完成身份认证。
     */
    public static boolean isAuthenticated() {
        return getCurrentUserId() != null;
    }
}
