package com.platform.dto.resp;

import com.platform.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前登录用户信息响应
 * 仅用于 GET /user/me，包含 email 等敏感字段
 * 不用于他人主页（他人主页用 UserProfileResp）
 */
@Data
@Builder
public class UserInfoResp {

    private Long userId;

    /** 登录账号，注册后不可修改 */
    private String username;

    /** 显示昵称，可修改 */
    private String nickname;

    /** 登录邮箱，仅本人可见 */
    private String email;

    private UserRole role;
    private String avatarUrl;
    private String coverUrl;
    private String signature;

    /** 注册时间 */
    private LocalDateTime createdAt;
}