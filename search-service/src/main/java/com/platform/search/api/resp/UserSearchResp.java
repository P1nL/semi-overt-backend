package com.platform.search.api.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 用户搜索响应：按用户名/昵称模糊匹配的用户列表。
 */
@Data
@Builder
public class UserSearchResp {

    private String keyword;
    private List<UserCardResp> list;
    private Long total;
    private Long page;
    private Long pageSize;
    private Long pages;

    @Data
    @Builder
    public static class UserCardResp {
        private Long id;
        private String username;
        private String nickname;
        private String avatarUrl;
        /** 用户主页路径，供前端直接跳转 */
        private String profilePath;
    }
}
