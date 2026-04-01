package com.platform.contract.auth.client;

import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.kernel.util.Result;
import com.platform.web.support.feign.FeignCommonConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "auth-service", configuration = FeignCommonConfig.class)
public interface AuthUserQueryClient {

    @PostMapping("/internal/users/batch")
    Result<List<UserSummaryDto>> batchUsers(@RequestBody BatchUserQueryReq req);
}
