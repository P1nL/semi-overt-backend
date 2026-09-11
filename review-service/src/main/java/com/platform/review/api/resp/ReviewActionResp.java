package com.platform.review.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ReviewActionResp {
    private String decisionId;
    private String state;
    private ArticleStatus status;
    private LocalDateTime reviewedAt;

    public LocalDateTime getUpdatedAt() {
        return reviewedAt;
    }
}
