package com.platform.review.api.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 用户公开资料页响应。
 */
@Data
@Builder
public class UserProfileResp {

    private ProfileInfo profile;
    private ArticleStats stats;
    private List<ArticleCardResp> list;
    private long total;
    private int page;
    private int pageSize;

    @Data
    @Builder
    public static class ProfileInfo {
        private Long id;
        private String username;
        private String nickname;
        private String avatarUrl;
        private String coverUrl;
        private String signature;
    }

    @Data
    @Builder
    public static class ArticleStats {
        private long approved;
        private long pending;
        private long returned;
        private long rejected;
        private long draft;
        private Integer totalWordCount;
    }
}
