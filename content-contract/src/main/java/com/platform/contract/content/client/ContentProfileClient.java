package com.platform.contract.content.client;

import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.kernel.util.Result;
import com.platform.web.support.feign.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "content-service", configuration = FeignCommonConfig.class)
public interface ContentProfileClient {

    @PostMapping("/internal/articles/profile-page")
    Result<UserProfileArticlesResp> profilePage(@RequestBody UserProfileArticlesQueryReq req);
}
