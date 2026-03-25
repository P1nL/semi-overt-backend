package com.platform.common.feign;

import com.platform.common.constant.HeaderNames;
import com.platform.common.context.TraceContextHolder;
import com.platform.common.context.UserContext;
import com.platform.common.context.UserContextHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 统一透传 traceId 和用户上下文，防止每个 Feign Client 重复拼装内部协议头。
 */
public class FeignHeaderRelayInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = TraceContextHolder.get();
        if (traceId != null && !traceId.isBlank()) {
            template.header(HeaderNames.X_TRACE_ID, traceId);
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
