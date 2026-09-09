package com.platform.auth.api.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginReq {

    @NotBlank(message = "Account is required")
    @Size(max = 120, message = "Account length must not exceed 120 characters")
    private String account;

    @NotBlank(message = "Password is required")
    @Size(max = 200, message = "Password is too long")
    private String password;

    private boolean rememberMe = false;
}
