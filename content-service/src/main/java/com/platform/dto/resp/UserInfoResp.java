package com.platform.dto.resp;

import com.platform.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * UserInfoResp 响应模型，封装对应场景返回的数据结构。
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