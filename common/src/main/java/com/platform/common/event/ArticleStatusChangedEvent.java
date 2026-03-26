package com.platform.common.event;

import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleStatusChangedEvent implements BaseDomainEvent {

    private String eventId;
    private String traceId;
    private Long articleId;
    private Long authorId;
    private ArticleStatus fromStatus;
    private ArticleStatus toStatus;
    private String title;
    private String summary;
    private String coverUrl;
    private String coverColor;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
}
