package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import com.platform.enums.DurationCategory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ArticleDetailResp 响应模型，封装对应场景返回的数据结构。
 */

@Data
@Builder
public class ArticleDetailResp {

    private Long id;
    private String title;

    /** Markdown 正文 */
    private String content;

    private String summary;
    private String coverUrl;

    /** 封面主色，用于搜索/分类页氛围色 */
    private String coverColor;

    private Integer wordCount;
    private BigDecimal readMinutes;
    private DurationCategory durationCategory;
    private ArticleStatus status;

    /** 文章作者基本信息 */
    private AuthorInfo author;

    /**
     * 最近一次退回/拒绝的原因
     * 状态为 RETURNED / REJECTED 时不为 null，其余为 null
     */
    private String latestReviewReason;
    private Integer submitCount;
    private LocalDateTime lastSubmittedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    /** 嵌套作者信息 */
    @Data
    @Builder
    public static class AuthorInfo {
        private Long id;
        private String username;
        private String avatarUrl;
    }
}
