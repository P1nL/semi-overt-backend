package com.platform.controller;

import com.platform.dto.req.ForgotPasswordReq;
import com.platform.dto.req.LoginReq;
import com.platform.dto.req.RegisterReq;
import com.platform.dto.req.ResetPasswordReq;
import com.platform.dto.resp.AuthResp;
import com.platform.service.AuthService;
import com.platform.util.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 * 基础路径：/api/v1/auth（已在 SecurityConfig 全部放行，无需 Token）
 *
 * POST /api/v1/auth/register          注册
 * POST /api/v1/auth/login             登录
 * POST /api/v1/auth/logout            登出（需携带 Token）
 * POST /api/v1/auth/forgot-password   找回密码-发送邮件
 * POST /api/v1/auth/reset-password    找回密码-重置密码
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 注册
     * 成功后直接返回 Token，前端无需再次登录
     */
    @PostMapping("/register")
    public Result<AuthResp> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    /**
     * 登录
     * account 支持用户名或邮箱
     */
    @PostMapping("/login")
    public Result<AuthResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    /**
     * 登出
     * 将当前 Token 加入黑名单，即使 Token 未过期也会失效
     * 前端登出后应同时清除本地存储的 Token
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        authService.logout(token);
        return Result.ok();
    }

    /**
     * 找回密码第一步：发送重置邮件
     * 无论邮箱是否存在，接口均返回成功（防止邮箱枚举攻击）
     * 同一邮箱 15 分钟内重复请求返回 429
     */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordReq req) {
        authService.forgotPassword(req);
        return Result.ok();
    }

    /**
     * 找回密码第二步：重置密码
     * token 来自邮件链接参数，一次性有效
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        authService.resetPassword(req);
        return Result.ok();
    }

    /**
     * 从请求头提取 Token（不含 "Bearer " 前缀）
     * 与 JwtAuthFilter 保持一致的提取逻辑
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}