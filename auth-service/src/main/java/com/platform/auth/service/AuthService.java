package com.platform.auth.service;

import com.platform.auth.api.req.ForgotPasswordReq;
import com.platform.auth.api.req.LoginReq;
import com.platform.auth.api.req.RegisterReq;
import com.platform.auth.api.req.ResetPasswordReq;
import com.platform.auth.api.resp.AuthResp;

public interface AuthService {

        AuthResp register(RegisterReq req);

        AuthResp login(LoginReq req);

        void forgotPassword(ForgotPasswordReq req);

        void resetPassword(ResetPasswordReq req);
}


