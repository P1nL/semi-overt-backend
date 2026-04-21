package com.platform.contract.auth.client;

import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.api.PageResponse;
import com.platform.kernel.util.Result;
import com.platform.web.support.feign.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "auth-service", configuration = FeignCommonConfig.class)
public interface AuthUserQueryClient {

    @PostMapping("/internal/users/batch")
    Result<List<UserSummaryDto>> batchUsers(@RequestBody BatchUserQueryReq req);

    /**
     * 按用户名或昵称模糊搜索用户，返回分页结果。
     */
    @GetMapping("/internal/users/search")
    Result<PageResponse<UserSummaryDto>> searchUsers(@RequestParam("keyword") String keyword,
                                                     @RequestParam(value = "page", defaultValue = "1") int page,
                                                     @RequestParam(value = "pageSize", defaultValue = "10") int pageSize);
}
