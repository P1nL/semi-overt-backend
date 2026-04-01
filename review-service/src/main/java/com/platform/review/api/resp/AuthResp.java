package com.platform.review.api.resp;

import com.platform.kernel.enums.UserRole;
import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class AuthResp {

        private String avatarUrl;
}


