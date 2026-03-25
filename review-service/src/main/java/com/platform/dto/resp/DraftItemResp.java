package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 草稿箱列表项响应
 * 仅包含 DRAFT 与 RETURNED 状态文章
 */
@Data
@Builder
public class DraftItemResp {

    private Long id;

    /**
     * 文章标题
     * 草稿标题为空时前端显示"未命名草稿"，后端返回 null 即可
     */
    private String title;

    /** DRAFT 或 RETURNED */
    private ArticleStatus status;

    private Integer wordCount;
    private LocalDateTime updatedAt;

    /**
     * 最近一次审核退回的原因
     * 仅 RETURNED 状态时有值，其余为 null
     */
    private String latestReason;
}