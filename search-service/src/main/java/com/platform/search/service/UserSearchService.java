package com.platform.search.service;

import com.platform.search.api.resp.UserSearchResp;

public interface UserSearchService {

    /**
     * 按用户名或昵称模糊搜索用户。
     *
     * @param keyword  搜索关键词
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     */
    UserSearchResp searchUsers(String keyword, int page, int pageSize);
}
