package com.platform.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人资料请求体。
 * 所有字段都为可选，前端只传需要更新的字段即可。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateProfileReq {

    /**
     * 显示昵称。
     */
    @Size(min = 1, max = 30, message = "昵称长度需在 1~30 个字符之间")
    private String nickname;

    /**
     * 头像 URL。
     */
    @Size(max = 512, message = "头像 URL 过长")
    private String avatarUrl;

    /**
     * 个人主页封面 URL。
     */
    @Size(max = 512, message = "封面 URL 过长")
    private String coverUrl;

    /**
     * 个性签名。
     */
    @Size(max = 50, message = "个性签名不能超过 50 个字符")
    private String signature;
}
