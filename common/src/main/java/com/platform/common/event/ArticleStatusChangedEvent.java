package com.platform.common.event;

import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 领域事件模型，描述文章状态变更后跨服务传播的数据快照。
 */

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
