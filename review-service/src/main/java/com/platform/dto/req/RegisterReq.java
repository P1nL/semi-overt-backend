package com.platform.dto.req;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * RegisterReq 请求模型，承载对应场景的入参字段。
 */

@Data
public class RegisterReq {

    /**
     * 账号：4~20位，字母/数字/下划线，不能纯数字
     * 正则说明：(?!\d+$) 排除纯数字，[a-zA-Z0-9_]{4,20} 限定字符集和长度
     */
    @NotBlank(message = "账号不能为空")
    @Pattern(
            regexp = "^(?!\\d+$)[a-zA-Z0-9_]{4,20}$",
            message = "账号为 4~20 位，只能包含字母、数字、下划线，且不能纯数字"
    )
    private String username;

    /** 邮箱：用于登录和找回密码 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 密码：8~20位，前端可做强度提示，后端只验长度 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度为 8~20 位")
    private String password;
}