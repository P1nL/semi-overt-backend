package com.platform.auth.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保存草稿响应。
 */
@Data
@Builder
public class SaveDraftResp {

    private LocalDateTime savedAt;
    private Integer wordCount;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private ArticleStatus status;
}
