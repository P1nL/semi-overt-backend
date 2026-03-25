package com.platform.common.constant;

/**
 * 内部头协议必须集中定义，避免网关和下游各自手写字符串导致链路不兼容。
 */
public final class HeaderNames {

    public static final String X_USER_ID = "X-User-Id";
    public static final String X_USERNAME = "X-Username";
    public static final String X_USER_ROLE = "X-User-Role";
    public static final String X_TRACE_ID = "X-Trace-Id";

    private HeaderNames() {
    }
}
