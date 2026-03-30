package com.platform.client;

import com.platform.common.dto.internal.UserProfileArticlesQueryReq;
import com.platform.common.dto.internal.UserProfileArticlesResp;
import com.platform.common.feign.FeignCommonConfig;
import com.platform.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 内容内部远程客户端，负责发起跨服务调用。
 */

@FeignClient(name = "content-service", configuration = FeignCommonConfig.class)
public interface ContentInternalClient {

    @PostMapping("/internal/articles/profile-page")
    Result<UserProfileArticlesResp> profilePage(@RequestBody UserProfileArticlesQueryReq req);
}
