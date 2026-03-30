package com.platform.util;

import com.platform.common.context.UserContext;
import com.platform.common.context.UserContextHolder;

/**
 * 安全上下文工具类，提供当前登录用户信息的读取能力。
 */

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前用户id。
     */
    public static Long getCurrentUserId() {
        UserContext context = UserContextHolder.get();
        return context != null ? context.getUserId() : null;
    }

    /**
     * 获取当前username。
     */
    public static String getCurrentUsername() {
        UserContext context = UserContextHolder.get();
        return context != null ? context.getUsername() : null;
    }

    /**
     * 获取当前角色。
     */
    public static String getCurrentRole() {
        UserContext context = UserContextHolder.get();
        return context != null ? context.getRole() : null;
    }

    /**
     * 判断admin。
     */
    public static boolean isAdmin() {
        String role = getCurrentRole();
        return "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    /**
     * 判断authenticated。
     */
    public static boolean isAuthenticated() {
        return getCurrentUserId() != null;
    }
}
