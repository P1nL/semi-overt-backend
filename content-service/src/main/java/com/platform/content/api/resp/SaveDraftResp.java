package com.platform.content.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class SaveDraftResp {
    private LocalDateTime savedAt;
    private LocalDateTime updatedAt;
    private Long version;
    private Integer wordCount;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private ArticleStatus status;
    private boolean draftVisible;

    /** Compatibility for callers that only initialize savedAt. */
    public LocalDateTime getUpdatedAt() {
        return updatedAt != null ? updatedAt : savedAt;
    }
}
