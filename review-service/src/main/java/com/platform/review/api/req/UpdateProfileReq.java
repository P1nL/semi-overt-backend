package com.platform.review.api.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateProfileReq {

    @Size(min = 1, max = 30, message = "Nickname length must be 1-30 characters")
    private String nickname;

    @Size(max = 512, message = "Avatar URL is too long")
    private String avatarUrl;

    @Size(max = 512, message = "Cover URL is too long")
    private String coverUrl;

    @Size(max = 50, message = "Signature length must not exceed 50 characters")
    private String signature;
}
