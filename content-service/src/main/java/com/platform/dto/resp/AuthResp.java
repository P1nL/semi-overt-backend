package com.platform.dto.resp;

import com.platform.enums.UserRole;
import lombok.Builder;
import lombok.Data;

/**
 * AuthResp 响应模型，封装对应场景返回的数据结构。
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