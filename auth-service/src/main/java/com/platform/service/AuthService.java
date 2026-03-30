package com.platform.service;

import com.platform.dto.req.ForgotPasswordReq;
import com.platform.dto.req.LoginReq;
import com.platform.dto.req.RegisterReq;
import com.platform.dto.req.ResetPasswordReq;
import com.platform.dto.resp.AuthResp;

/**
 * 认证业务接口，定义对外暴露的服务能力。
 */

public interface AuthService {

    /**
     * 注册新用户
     * 注册成功后直接颁发 Token，免去再次登录
     */
    AuthResp register(RegisterReq req);

    /**
     * 登录
     * account 支持用户名或邮箱
     */
    AuthResp login(LoginReq req);

    /**
     * 登出
     * 将当前 Token 加入 Redis 黑名单（剩余 TTL 内有效）
     *
     * @param token 从 Authorization 头提取的原始 Token（不含 "Bearer " 前缀）
     */
    void logout(String token);

    /**
     * 找回密码第一步：发送重置邮件
     * 同一邮箱在 15 分钟内重复请求将返回 429
     */
    void forgotPassword(ForgotPasswordReq req);

    /**
     * 找回密码第二步：用 token 重置密码
     * token 一次性使用，重置成功后立即失效
     */
    void resetPassword(ResetPasswordReq req);
}