package com.platform.search.service;

import com.platform.search.api.resp.UserSearchResp;

public interface UserSearchService {

    /**
     * 按用户名或昵称模糊搜索用户。
     *
     * @param keyword 搜索关键词
     * @param limit   最多返回条数（不超过 20）
     */
    UserSearchResp searchUsers(String keyword, int limit);
}
