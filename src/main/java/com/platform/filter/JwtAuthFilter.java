package com.platform.filter;

import com.platform.util.JwtHelper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtHelper jwtHelper;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            Boolean isBlacklisted = redisTemplate.hasKey("jwt:blacklist:" + token);

            if (Boolean.TRUE.equals(isBlacklisted)) {
                SecurityContextHolder.clearContext();
                log.debug("Token 已在黑名单中，拒绝认证");
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication = jwtHelper.resolveJwt(token);
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);

                if (jwtHelper.shouldRefresh(token)) {
                    Long userId = jwtHelper.getUserId(token);
                    String username = jwtHelper.getUsername(token);
                    String role = jwtHelper.getRole(token);

                    if (userId != null && username != null) {
                        if (role == null || role.isBlank()) {
                            role = "USER";
                        }
                        String newToken = jwtHelper.createToken(userId, username, role, false);
                        response.setHeader("New-Token", newToken);
                    }
                }
            } else {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}