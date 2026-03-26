package com.platform.common.event;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

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
