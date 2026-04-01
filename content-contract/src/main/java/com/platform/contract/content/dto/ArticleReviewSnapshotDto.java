package com.platform.contract.content.dto;

import com.platform.kernel.enums.ArticleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文章审核SnapshotDto相关类型，承载当前模块中的辅助职责。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleReviewSnapshotDto {
    private Long articleId;
    private Long authorId;
    private String title;
    private String summary;
    private String content;
    private Integer wordCount;
    private Integer submitCount;
    private ArticleStatus status;
    private LocalDateTime lastSubmittedAt;
}
