package com.platform.search.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Article card contract compatible with the monolith and current frontend. */
@Data
@Builder
public class ArticleCardResp {

    /** Canonical card id consumed by the frontend. */
    private Long id;
    /** Legacy/current search alias retained for callers that use articleId. */
    private Long articleId;
    private Long authorId;
    private ArticleAuthorResp author;
    private String authorName;
    private String authorAvatar;
    private String title;
    private String content;
    private String summary;
    private String previewText;
    private String coverUrl;
    private String coverColor;
    private ArticleStatus status;
    private Integer wordCount;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private Boolean draftVisible;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private String rejectReason;

    @Data
    @Builder
    public static class ArticleAuthorResp {
        private Long id;
        private String username;
        private String nickname;
        private String avatarUrl;
    }
}