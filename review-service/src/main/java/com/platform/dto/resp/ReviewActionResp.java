package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ReviewActionResp 响应模型，封装对应场景返回的数据结构。
 */

@Data
@Builder
public class ReviewActionResp {

    /** 审核后的文章状态 */
    private ArticleStatus status;

    /** 审核完成时间 */
    private LocalDateTime reviewedAt;
}