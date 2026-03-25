package com.platform.util;

import com.platform.common.context.UserContext;
import com.platform.common.context.UserContextHolder;

/**
 * 业务层继续走静态工具方法，但底层数据改为网关注入的 Header 上下文。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        UserContext context = UserContextHolder.get();
        return context != null ? context.getUserId() : null;
    }

    public static String getCurrentUsername() {
        UserContext context = UserContextHolder.get();
        return context != null ? context.getUsername() : null;
    }

    public static String getCurrentRole() {
        UserContext context = UserContextHolder.get();
        return context != null ? context.getRole() : null;
    }

    public static boolean isAdmin() {
        String role = getCurrentRole();
        return "ADMIN".equalsIgnoreCase(role) || "ROLE_ADMIN".equalsIgnoreCase(role);
    }

    public static boolean isAuthenticated() {
        return getCurrentUserId() != null;
    }
}
