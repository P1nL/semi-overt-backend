package com.platform.auth.service;

import com.platform.auth.api.req.UpdateProfileReq;
import com.platform.auth.api.resp.UserInfoResp;
import com.platform.auth.api.resp.UserProfileResp;

public interface UserService {

    UserInfoResp getCurrentUserInfo(Long userId);

    UserInfoResp updateProfile(Long userId, UpdateProfileReq req);

    UserProfileResp getUserProfile(String username, Long currentUserId, String tab, int page, int pageSize);
}
