package com.platform.service;

import com.platform.dto.req.ForgotPasswordReq;
import com.platform.dto.req.LoginReq;
import com.platform.dto.req.RegisterReq;
import com.platform.dto.req.ResetPasswordReq;
import com.platform.dto.resp.AuthResp;

/**
 * 认证业务接口，定义注册、登录和密码找回相关服务能力。
 */
public interface AuthService {

    /**
     * 注册新用户。
     */
    AuthResp register(RegisterReq req);

    /**
     * 登录。
     */
    AuthResp login(LoginReq req);

    /**
     * 发送找回密码邮件。
     */
    void forgotPassword(ForgotPasswordReq req);

    /**
     * 使用重置令牌更新密码。
     */
    void resetPassword(ResetPasswordReq req);
}
