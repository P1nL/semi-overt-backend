package com.platform.auth.api.resp;

import com.platform.kernel.enums.UserRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResp {

    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String email;
    private UserRole role;
    private String avatarUrl;
}
