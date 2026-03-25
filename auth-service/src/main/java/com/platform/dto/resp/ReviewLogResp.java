package com.platform.dto.resp;

import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核日志项响应
 * 对应接口：GET /api/v1/reviews/{articleId}/logs
 *
 * 用于编辑器顶部原因提示、拒绝原因展示与后台追踪。
 */
@Data
@Builder
public class ReviewLogResp {

    private ReviewAction action;
    private ArticleStatus fromStatus;
    private ArticleStatus toStatus;

    /** 退回/拒绝原因，APPROVE 和 CANCEL 时为 null */
    private String reason;

    /** 操作人（管理员或作者本人-取消审核场景） */
    private OperatorInfo operator;

    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class OperatorInfo {
        private Long id;
        private String username;
    }
}