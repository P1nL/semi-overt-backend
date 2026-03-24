package com.platform.dto.resp;

import com.platform.enums.UserRole;
import lombok.Builder;
import lombok.Data;

/**
 * 认证响应：注册和登录均返回此结构
 * 前端拿到 token 后存入本地，后续请求放入 Authorization: Bearer <token>
 */
@Data
@Builder
public class AuthResp {

    /** JWT Token */
    private String token;

    private Long userId;
    private String username;

    /** 显示昵称，可修改 */
    private String nickname;

    private String email;
    private UserRole role;

    /** 头像 URL（可为 null，前端显示默认头像） */
    private String avatarUrl;
}