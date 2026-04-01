package com.platform.content.api.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核列表项响应。
 */
@Data
@Builder
public class ReviewListItemResp {

    private Long id;
    private String title;
    private Integer submitCount;
    private LocalDateTime submittedAt;
    private Integer wordCount;
    private AuthorInfo author;

    @Data
    @Builder
    public static class AuthorInfo {
        private Long id;
        private String username;
    }
}
