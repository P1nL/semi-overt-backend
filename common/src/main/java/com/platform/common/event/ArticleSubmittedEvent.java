package com.platform.common.event;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 领域事件模型，描述文章提交审核时投递给下游服务的数据。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSubmittedEvent implements BaseDomainEvent {

    private String eventId;
    private String traceId;
    private Long articleId;
    private Long authorId;
    private Integer submitCount;
    private LocalDateTime submittedAt;
}
