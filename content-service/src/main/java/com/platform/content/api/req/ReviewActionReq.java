package com.platform.content.api.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审核动作请求。
 */
@Data
public class ReviewActionReq {

    /**
     * 审核动作，支持 `APPROVE`、`RETURN`、`REJECT`。
     */
    @NotBlank(message = "审核动作不能为空")
    private String action;

    /**
     * 退回或拒绝时填写原因，通过时可为空。
     */
    private String reason;
}
