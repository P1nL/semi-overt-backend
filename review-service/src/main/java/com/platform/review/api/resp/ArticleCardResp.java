package com.platform.review.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文章卡片响应。
 */
@Data
@Builder
public class ArticleCardResp {

    private Long articleId;
    private String title;
    private String summary;
    private String previewText;
    private String coverUrl;
    private String coverColor;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private ArticleStatus status;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private String rejectReason;
}
