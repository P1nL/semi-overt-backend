package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ArticleCardResp 响应模型，封装对应场景返回的数据结构。
 */

@Data
@Builder
public class ArticleCardResp {

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
