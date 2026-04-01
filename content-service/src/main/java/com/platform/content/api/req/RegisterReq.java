package com.platform.content.api.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterReq {

    @NotBlank(message = "Username is required")
    @Pattern(
            regexp = "^(?!\\d+$)[a-zA-Z0-9_]{4,20}$",
            message = "Username must be 4-20 characters and use letters, digits, or underscores"
    )
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 20, message = "Password length must be 8-20 characters")
    private String password;
}
