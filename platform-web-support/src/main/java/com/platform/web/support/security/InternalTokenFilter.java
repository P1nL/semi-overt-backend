package com.platform.web.support.security;

import com.platform.kernel.constant.HeaderNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 内部接口鉴权过滤器。
 * 校验 /internal/** 请求是否携带了合法的 X-Internal-Token。
 * 该 token 由网关在转发时注入，或由 Feign 拦截器在服务间调用时附加。
 *
 * <p>配置方式：
 * <ol>
 *   <li>在环境变量或 Nacos 中配置 platform.internal.token</li>
 *   <li>在各服务的 SecurityConfig 中实例化此 Filter 并注册到 /internal/** 路径</li>
 * </ol>
 */
@Slf4j
public class InternalTokenFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public InternalTokenFilter(String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalArgumentException("[安全] platform.internal.token 未配置，内部接口将无法访问");
        }
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 仅拦截 /internal/ 路径
        String path = request.getRequestURI();
        if (!path.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(HeaderNames.X_INTERNAL_TOKEN);
        if (token == null || !expectedToken.equals(token)) {
            log.warn("内部接口鉴权失败: path={}, remoteAddr={}", path, request.getRemoteAddr());
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(403);
            response.getWriter().write("{\"code\":403,\"message\":\"Internal access denied\",\"data\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
