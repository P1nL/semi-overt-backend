package com.platform.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 * account 字段同时支持用户名和邮箱（后端按是否含 @ 自动区分）
 */
@Data
public class LoginReq {

    /** 账号：用户名 或 邮箱，两者均可 */
    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 记住我：true → 有效期 7 天（jwt.token.remember-me-expiration）
     *         false → 有效期 2 小时（jwt.token.expiration）
     */
    private boolean rememberMe = false;
}