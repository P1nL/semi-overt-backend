package com.platform.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * ForgotPasswordReq 请求模型，承载对应场景的入参字段。
 */

@Data
public class ForgotPasswordReq {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}