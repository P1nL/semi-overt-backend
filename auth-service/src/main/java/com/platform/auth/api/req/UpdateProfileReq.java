package com.platform.auth.api.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateProfileReq {

    @Size(max = 60, message = "Nickname length must not exceed 60 characters")
    private String nickname;

    @Size(max = 255, message = "Avatar URL is too long")
    private String avatarUrl;

    @Size(max = 512, message = "Cover URL is too long")
    private String coverUrl;

    @Size(max = 50, message = "Signature length must not exceed 50 characters")
    private String signature;
}
