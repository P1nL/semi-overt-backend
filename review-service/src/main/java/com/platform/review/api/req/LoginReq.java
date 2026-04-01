package com.platform.review.api.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReq {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;

    private boolean rememberMe = false;
}
