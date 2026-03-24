package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核动作响应
 * 对应接口：POST /api/v1/reviews/{articleId}/action
 */
@Data
@Builder
public class ReviewActionResp {

    /** 审核后的文章状态 */
    private ArticleStatus status;

    /** 审核完成时间 */
    private LocalDateTime reviewedAt;
}