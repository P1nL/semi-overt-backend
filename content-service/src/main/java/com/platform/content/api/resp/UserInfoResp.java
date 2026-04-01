package com.platform.content.api.resp;

import com.platform.kernel.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息响应。
 */
@Data
@Builder
public class UserInfoResp {

    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private UserRole role;
    private String avatarUrl;
    private String coverUrl;
    private String signature;
    private LocalDateTime createdAt;
}
