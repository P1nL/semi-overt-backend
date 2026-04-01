package com.platform.review.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提审响应。
 */
@Data
@Builder
public class SubmitResp {

    private ArticleStatus status;
    private Integer submitCount;
    private LocalDateTime lastSubmittedAt;
}
