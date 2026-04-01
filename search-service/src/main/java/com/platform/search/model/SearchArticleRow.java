package com.platform.search.model;

import com.platform.kernel.enums.DurationCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MySQL 鎼滅储缁撴灉琛屾ā鍨嬨€?
 */
@Data
public class SearchArticleRow {

    private Long articleId;
    private Long authorId;
    private String title;
    private String summary;
    private String coverUrl;
    private String coverColor;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}

