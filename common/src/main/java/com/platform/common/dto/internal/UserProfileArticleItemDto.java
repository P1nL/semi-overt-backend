package com.platform.common.dto.internal;

import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户资料文章ItemDto相关类型，承载当前模块中的辅助职责。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileArticleItemDto {
    private Long articleId;
    private String title;
    private String summary;
    private String previewText;
    private String coverUrl;
    private String coverColor;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private ArticleStatus status;
    private Long authorId;
    private String authorName;
    private String authorAvatar;
    private LocalDateTime publishedAt;
    private LocalDateTime updatedAt;
    private String rejectReason;
}
