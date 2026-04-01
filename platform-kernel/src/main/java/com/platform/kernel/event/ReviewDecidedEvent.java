package com.platform.kernel.event;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.ReviewAction;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 领域事件模型，描述审核动作完成后传播给内容服务的结果。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDecidedEvent implements BaseDomainEvent {

    private String eventId;
    private String traceId;
    private Long articleId;
    private Long adminId;
    private ReviewAction action;
    private String reason;
    private java.time.LocalDateTime reviewedAt;
    private ArticleStatus fromStatus;
    private ArticleStatus toStatus;

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
