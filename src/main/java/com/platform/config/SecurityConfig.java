package com.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.filter.JwtAuthFilter;
import com.platform.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 核心配置
 * - 无状态 JWT，关闭 Session
 * - 路由权限：公开 / 登录用户 / 仅管理员
 * - 未登录访问受保护接口返回 JSON（非重定向到登录页）
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // 开启 @PreAuthorize 注解支持
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离，关闭 CSRF
                .csrf(csrf -> csrf.disable())

                // 无状态，不使用 Session
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 路由权限规则
                .authorizeHttpRequests(auth -> auth
                        // 静态资源（上传文件对外访问）
                        .requestMatchers("/static/**").permitAll()

                        // 认证接口（注册/登录/找回密码）全部放行
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // 首页、分类、搜索（公开）
                        .requestMatchers(HttpMethod.GET, "/api/v1/home").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/search/**").permitAll()

                        // 文章详情：公开接口（内部按状态做权限细分）
                        .requestMatchers(HttpMethod.GET, "/api/v1/articles/{articleId}").permitAll()

                        // 用户主页（公开）
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/profile").permitAll()

                        // 文章作者访问
                        .requestMatchers(HttpMethod.GET, "/api/v1/reviews/*/logs").authenticated()

                        // 审核接口：仅管理员（细粒度控制在 @PreAuthorize）
                        .requestMatchers("/api/v1/reviews/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // 其余接口需登录
                        .anyRequest().authenticated()
                )

                // 未登录访问受保护接口：返回 JSON 而非跳转登录页
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(200);
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Result.unauthorized("未登录或 Token 已失效"))
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(200);
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Result.forbidden("权限不足"))
                            );
                        })
                )

                // JWT 过滤器在 UsernamePassword 过滤器之前
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
