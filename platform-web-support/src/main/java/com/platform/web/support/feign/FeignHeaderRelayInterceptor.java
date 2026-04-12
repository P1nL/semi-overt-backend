package com.platform.web.support.feign;

import com.platform.kernel.constant.HeaderNames;
import com.platform.kernel.context.TraceContextHolder;
import com.platform.kernel.context.UserContext;
import com.platform.kernel.context.UserContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Feign 请求头透传拦截器，负责把链路追踪和用户上下文转发到下游服务。
 */

public class FeignHeaderRelayInterceptor implements RequestInterceptor {

    private final String internalToken;

    public FeignHeaderRelayInterceptor(String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    public void apply(RequestTemplate template) {
        String traceId = TraceContextHolder.get();
        if (traceId != null && !traceId.isBlank()) {
            template.header(HeaderNames.X_TRACE_ID, traceId);
        }

        // 服务间调用自动附加内部鉴权令牌
        if (internalToken != null && !internalToken.isBlank()) {
            template.header(HeaderNames.X_INTERNAL_TOKEN, internalToken);
        }

        UserContext userContext = UserContextHolder.get();
        if (userContext != null && userContext.getUserId() != null) {
            template.header(HeaderNames.X_USER_ID, String.valueOf(userContext.getUserId()));
            if (userContext.getUsername() != null) {
                template.header(HeaderNames.X_USERNAME, userContext.getUsername());
            }
            if (userContext.getRole() != null) {
                template.header(HeaderNames.X_USER_ROLE, userContext.getRole());
            }
        }
    }
}
