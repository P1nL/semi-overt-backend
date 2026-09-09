package com.platform.auth.service;

import com.platform.auth.api.req.ForgotPasswordReq;
import com.platform.auth.api.req.LoginReq;
import com.platform.auth.api.req.RegisterCodeReq;
import com.platform.auth.api.req.RegisterReq;
import com.platform.auth.api.req.ResetPasswordReq;
import com.platform.auth.api.resp.AuthResp;
import com.platform.auth.api.resp.AuthSessionResult;

public interface AuthService {
    AuthSessionResult registerWithSession(RegisterReq req, String previousRefreshToken);

    void requestRegistrationCode(RegisterCodeReq req, String clientIp);

    AuthSessionResult loginWithSession(LoginReq req, String previousRefreshToken);

    AuthSessionResult refresh(String refreshToken);

    void logout(String refreshToken);

    void forgotPassword(ForgotPasswordReq req, String clientIp);

    void resetPassword(ResetPasswordReq req);

    default AuthResp register(RegisterReq req) {
        return registerWithSession(req, null).response();
    }

    default AuthResp login(LoginReq req) {
        return loginWithSession(req, null).response();
    }

    default void forgotPassword(ForgotPasswordReq req) {
        forgotPassword(req, "unknown");
    }
}
