package com.platform.controller;

import com.platform.dto.req.ForgotPasswordReq;
import com.platform.dto.req.LoginReq;
import com.platform.dto.req.RegisterReq;
import com.platform.dto.req.ResetPasswordReq;
import com.platform.dto.resp.AuthResp;
import com.platform.service.AuthService;
import com.platform.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器，对外提供注册、登录和密码找回相关接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 处理 POST /register 请求。
     */
    @PostMapping("/register")
    public Result<AuthResp> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    /**
     * 处理 POST /login 请求。
     */
    @PostMapping("/login")
    public Result<AuthResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    /**
     * 处理 POST /forgot-password 请求。
     */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordReq req) {
        authService.forgotPassword(req);
        return Result.ok();
    }

    /**
     * 处理 POST /reset-password 请求。
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        authService.resetPassword(req);
        return Result.ok();
    }
}
