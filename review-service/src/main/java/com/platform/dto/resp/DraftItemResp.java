package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DraftItemResp 响应模型，封装对应场景返回的数据结构。
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