package com.platform.auth.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核动作结果响应。
 */
@Data
@Builder
public class ReviewActionResp {

    private ArticleStatus status;
    private LocalDateTime reviewedAt;
}