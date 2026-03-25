package com.platform.common.security;

import com.platform.common.constant.HeaderNames;
import com.platform.common.context.TraceContextHolder;
import com.platform.common.context.UserContext;
import com.platform.common.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 下游服务统一信任网关注入的头，避免每个服务重复解析 JWT 和维护不同认证逻辑。
 */
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String traceId = request.getHeader(HeaderNames.X_TRACE_ID);
            if (traceId != null && !traceId.isBlank()) {
                TraceContextHolder.set(traceId);
            }

            String userIdHeader = request.getHeader(HeaderNames.X_USER_ID);
            String username = request.getHeader(HeaderNames.X_USERNAME);
            String role = request.getHeader(HeaderNames.X_USER_ROLE);

            if (userIdHeader != null && !userIdHeader.isBlank()) {
                Long userId = Long.valueOf(userIdHeader);
                String normalizedRole = (role == null || role.isBlank()) ? "USER" : role;
                List<SimpleGrantedAuthority> authorities =
                        Collections.singletonList(new SimpleGrantedAuthority(
                                normalizedRole.startsWith("ROLE_") ? normalizedRole : "ROLE_" + normalizedRole
                        ));

                UserContextHolder.set(UserContext.builder()
                        .userId(userId)
                        .username(username)
                        .role(normalizedRole)
                        .traceId(traceId)
                        .build());

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, authorities)
                );
            }

            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            UserContextHolder.clear();
            TraceContextHolder.clear();
        }
    }
}
