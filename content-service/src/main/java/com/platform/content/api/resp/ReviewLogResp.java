package com.platform.content.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核日志响应。
 */
@Data
@Builder
public class ReviewLogResp {

    private ReviewAction action;
    private ArticleStatus fromStatus;
    private ArticleStatus toStatus;
    private String reason;
    private OperatorInfo operator;
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class OperatorInfo {
        private Long id;
        private String username;
    }
}
