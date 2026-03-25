package com.platform.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 找回密码第二步：携带邮件中的 token + 新密码提交
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