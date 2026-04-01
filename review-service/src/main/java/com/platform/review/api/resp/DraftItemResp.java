package com.platform.review.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 草稿列表项响应。
 */
@Data
@Builder
public class DraftItemResp {

    private Long id;
    private String title;
    private ArticleStatus status;
    private Integer wordCount;
    private LocalDateTime updatedAt;
    private String latestReason;
}
