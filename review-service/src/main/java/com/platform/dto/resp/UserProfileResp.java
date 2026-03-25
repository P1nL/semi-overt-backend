package com.platform.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 个人主页响应
 * 结构分三段：profile（用户信息）/ stats（各状态文章统计）/ list（文章列表分页）
 *
 * 权限说明：
 *   - 本人访问：stats 返回全部状态统计，list 支持 tab 过滤
 *   - 他人访问：stats 只统计 APPROVED，list 只返回 APPROVED 文章
 */
@Data
@Builder
public class UserProfileResp {

    /** 用户基本信息 */
    private ProfileInfo profile;

    /**
     * 各状态文章数量统计
     * 他人访问时仅 approved 有值，其余为 0
     */
    private ArticleStats stats;

    /** 当前分页的文章列表 */
    private List<ArticleCardResp> list;

    /** 总条数（用于前端分页） */
    private long total;

    private int page;
    private int pageSize;

    // ==================== 嵌套类 ====================

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
        /** 所有 APPROVED 文章的总字数 */
        private Integer totalWordCount;
    }
}