package com.platform.common.dto.internal;

import com.platform.enums.ArticleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
