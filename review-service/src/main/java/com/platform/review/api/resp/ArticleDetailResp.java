package com.platform.review.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文章详情响应。
 */
@Data
@Builder
public class ArticleDetailResp {

    private Long id;
    private String title;
    private String content;
    private String summary;
    private String coverUrl;
    private String coverColor;
    private Integer wordCount;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private ArticleStatus status;
    private AuthorInfo author;
    private String latestReviewReason;
    private Integer submitCount;
    private LocalDateTime lastSubmittedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    @Data
    @Builder
    public static class AuthorInfo {
        private Long id;
        private String username;
        private String avatarUrl;
    }
}
