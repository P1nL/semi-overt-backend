package com.platform.auth.api.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterReq {

    @NotBlank(message = "Username is required")
    @Pattern(
            regexp = "^(?!\\d+$)[a-zA-Z0-9_]{3,32}$",
            message = "Username must be 3-32 characters and use letters, digits, or underscores"
    )
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 120, message = "Email length must not exceed 120 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(max = 200, message = "Password is too long")
    private String password;

    @Pattern(regexp = "\\d{6}", message = "Registration code must be 6 digits")
    private String emailCode;

    /** Registration-code request verifies Turnstile; registration then consumes its email code. */
    private String cfTurnstileToken;
}
