package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核列表项响应
 * 对应接口：GET /api/v1/reviews/pending
 *
 * 只包含审核员决策所需的基本信息，完整正文通过文章详情接口获取。
 */
@Data
@Builder
public class ReviewListItemResp {

    private Long id;
    private String title;

    /** 累计提交审核次数（多次提交说明历史有退回，审核员可重点关注） */
    private Integer submitCount;

    /** 最近一次提交审核时间 */
    private LocalDateTime submittedAt;

    /** 文章字数（辅助判断工作量） */
    private Integer wordCount;

    /** 作者信息 */
    private AuthorInfo author;

    @Data
    @Builder
    public static class AuthorInfo {
        private Long id;
        private String username;
    }
}