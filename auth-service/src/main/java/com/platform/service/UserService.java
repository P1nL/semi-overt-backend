package com.platform.service;

import com.platform.dto.req.UpdateProfileReq;
import com.platform.dto.resp.UserInfoResp;
import com.platform.dto.resp.UserProfileResp;

/**
 * 用户业务接口，定义对外暴露的服务能力。
 */

public interface UserService {

    /**
     * 获取当前登录用户信息
     */
    UserInfoResp getCurrentUserInfo(Long userId);

    /**
     * 修改个人资料
     */
    UserInfoResp updateProfile(Long userId, UpdateProfileReq req);

    /**
     * 获取个人主页
     *
     * @param username      目标用户名
     * @param currentUserId 当前登录用户 ID，未登录为 null
     * @param tab           状态过滤：all / approved / pending / returned / rejected / draft
     * @param page          页码，默认 1
     * @param pageSize      每页条数，默认 10
     */
    UserProfileResp getUserProfile(String username, Long currentUserId,
                                   String tab, int page, int pageSize);
}