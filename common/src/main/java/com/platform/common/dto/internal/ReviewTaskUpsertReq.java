package com.platform.common.dto.internal;

import com.platform.enums.ArticleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审核任务Upsert相关类型，承载当前模块中的辅助职责。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewTaskUpsertReq {
    private Long articleId;
    private Long authorId;
    private String title;
    private Integer wordCount;
    private ArticleStatus status;
    private Integer submitCount;
    private LocalDateTime submittedAt;
    private String lastEventId;
}
