package com.platform.client;

import com.platform.common.dto.internal.BatchUserQueryReq;
import com.platform.common.dto.internal.UserSummaryDto;
import com.platform.common.feign.FeignCommonConfig;
import com.platform.util.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 认证内部远程客户端，负责发起跨服务调用。
 */

@FeignClient(name = "auth-service", configuration = FeignCommonConfig.class)
public interface AuthInternalClient {

    @PostMapping("/internal/users/batch")
    Result<List<UserSummaryDto>> batchUsers(@RequestBody BatchUserQueryReq req);
}
