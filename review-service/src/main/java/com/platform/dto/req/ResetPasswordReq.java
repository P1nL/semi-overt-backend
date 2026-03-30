package com.platform.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * ResetPasswordReq 请求模型，承载对应场景的入参字段。
 */

@Data
public class ResetPasswordReq {

    /** 邮件中的重置令牌（UUID，存在 Redis 中，15分钟有效） */
    @NotBlank(message = "重置令牌不能为空")
    private String token;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度为 8~20 位")
    private String newPassword;
}