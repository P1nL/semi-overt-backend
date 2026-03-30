package com.platform.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ReviewActionReq 请求模型，承载对应场景的入参字段。
 */

@Data
public class ReviewActionReq {

    /**
     * 审核动作：APPROVE / RETURN / REJECT
     * 使用 String 接收，Service 层转枚举并做合法性校验
     */
    @NotBlank(message = "审核动作不能为空")
    private String action;

    /**
     * 退回/拒绝原因
     * RETURN / REJECT 时必填（Service 层校验），APPROVE 时可为 null
     */
    private String reason;
}