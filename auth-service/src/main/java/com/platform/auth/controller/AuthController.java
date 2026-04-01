package com.platform.auth.controller;

import com.platform.auth.api.req.ForgotPasswordReq;
import com.platform.auth.api.req.LoginReq;
import com.platform.auth.api.req.RegisterReq;
import com.platform.auth.api.req.ResetPasswordReq;
import com.platform.auth.api.resp.AuthResp;
import com.platform.auth.service.AuthService;
import com.platform.kernel.util.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 婢跺嫮鎮?POST /register 鐠囬攱鐪伴妴?     */
    @PostMapping("/register")
    public Result<AuthResp> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    /**
     * 婢跺嫮鎮?POST /login 鐠囬攱鐪伴妴?     */
    @PostMapping("/login")
    public Result<AuthResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    /**
     * 婢跺嫮鎮?POST /forgot-password 鐠囬攱鐪伴妴?     */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordReq req) {
        authService.forgotPassword(req);
        return Result.ok();
    }

    /**
     * 婢跺嫮鎮?POST /reset-password 鐠囬攱鐪伴妴?     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        authService.resetPassword(req);
        return Result.ok();
    }
}


