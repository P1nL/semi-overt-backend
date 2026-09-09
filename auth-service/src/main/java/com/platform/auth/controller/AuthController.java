package com.platform.auth.controller;

import com.platform.auth.api.req.ForgotPasswordReq;
import com.platform.auth.api.req.LoginReq;
import com.platform.auth.api.req.RegisterCodeReq;
import com.platform.auth.api.req.RegisterReq;
import com.platform.auth.api.req.ResetPasswordReq;
import com.platform.auth.api.resp.AuthResp;
import com.platform.auth.api.resp.AuthSessionResult;
import com.platform.auth.security.AuthClientIpResolver;
import com.platform.auth.security.AuthOriginGuard;
import com.platform.auth.service.AuthService;
import com.platform.kernel.exception.BusinessException;
import com.platform.kernel.util.Result;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final AuthOriginGuard originGuard;
    private final AuthClientIpResolver clientIpResolver;
    private final String refreshCookieName;
    private final boolean refreshCookieSecure;

    public AuthController(
            AuthService authService,
            AuthOriginGuard originGuard,
            AuthClientIpResolver clientIpResolver,
            @Value("${platform.auth.refresh-cookie-name:semi_overt_refresh}") String refreshCookieName,
            @Value("${platform.auth.refresh-cookie-secure:true}") boolean refreshCookieSecure) {
        this.authService = authService;
        this.originGuard = originGuard;
        this.clientIpResolver = clientIpResolver;
        this.refreshCookieName = refreshCookieName;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @PostMapping("/register-code")
    public Result<Void> registerCode(@Valid @RequestBody RegisterCodeReq req, HttpServletRequest request) {
        originGuard.validate(request);
        authService.requestRegistrationCode(req, clientIpResolver.resolve(request));
        return Result.ok();
    }

    @PostMapping("/register")
    public Result<AuthResp> register(@Valid @RequestBody RegisterReq req,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        originGuard.validate(request);
        AuthSessionResult result = authService.registerWithSession(req, readRefreshCookie(request));
        writeRefreshCookie(response, result);
        return Result.ok(result.response());
    }

    @PostMapping("/login")
    public Result<AuthResp> login(@Valid @RequestBody LoginReq req,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        originGuard.validate(request);
        AuthSessionResult result = authService.loginWithSession(req, readRefreshCookie(request));
        writeRefreshCookie(response, result);
        return Result.ok(result.response());
    }

    @PostMapping("/refresh")
    public Result<AuthResp> refresh(HttpServletRequest request, HttpServletResponse response) {
        originGuard.validate(request);
        try {
            AuthSessionResult result = authService.refresh(readRefreshCookie(request));
            writeRefreshCookie(response, result);
            return Result.ok(result.response());
        } catch (BusinessException ex) {
            if (ex.getCode() == 401) {
                clearRefreshCookie(response);
            }
            throw ex;
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        originGuard.validate(request);
        authService.logout(readRefreshCookie(request));
        clearRefreshCookie(response);
        return Result.ok();
    }

    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordReq req, HttpServletRequest request) {
        originGuard.validate(request);
        authService.forgotPassword(req, clientIpResolver.resolve(request));
        return Result.ok();
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordReq req, HttpServletRequest request) {
        originGuard.validate(request);
        authService.resetPassword(req);
        return Result.ok();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (refreshCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeRefreshCookie(HttpServletResponse response, AuthSessionResult result) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(refreshCookieName, result.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .path("/api")
                .sameSite("Lax");
        if (result.persistent()) {
            builder.maxAge(Duration.ofSeconds(result.refreshCookieMaxAgeSeconds()));
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(refreshCookieName, "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .path("/api")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build().toString());
    }
}
