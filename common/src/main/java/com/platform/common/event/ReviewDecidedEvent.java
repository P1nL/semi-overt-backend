package com.platform.common.event;

import com.platform.common.dto.internal.ReviewDecisionPayload;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDecidedEvent implements BaseDomainEvent {

    private String eventId;
    private String traceId;
    private Long articleId;
    private Long adminId;
    private com.platform.enums.ReviewAction action;
    private String reason;
    private java.time.LocalDateTime reviewedAt;
    private com.platform.enums.ArticleStatus fromStatus;
    private com.platform.enums.ArticleStatus toStatus;

    public static ReviewDecidedEvent fromPayload(String eventId, ReviewDecisionPayload payload) {
        return ReviewDecidedEvent.builder()
                .eventId(eventId)
                .traceId(payload.getTraceId())
                .articleId(payload.getArticleId())
                .adminId(payload.getAdminId())
                .action(payload.getAction())
                .reason(payload.getReason())
                .reviewedAt(payload.getReviewedAt())
                .fromStatus(payload.getFromStatus())
                .toStatus(payload.getToStatus())
                .build();
    }
}
