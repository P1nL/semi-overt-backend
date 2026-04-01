package com.platform.kernel.context;

import lombok.Builder;
import lombok.Value;

/**
 * 用户上下文模型，封装当前请求中的登录用户身份信息。
 */

@Value
@Builder
public class UserContext {
    Long userId;
    String username;
    String role;
    String traceId;
}
