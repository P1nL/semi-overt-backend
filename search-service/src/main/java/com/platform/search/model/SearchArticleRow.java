package com.platform.search.model;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A bounded article card projection returned by the shared read query. */
@Data
public class SearchArticleRow {

    private Long articleId;
    private Long authorId;
    private String title;
    private String summary;
    private String content;
    private Integer wordCount;
    private String coverUrl;
    private String coverColor;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private ArticleStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}