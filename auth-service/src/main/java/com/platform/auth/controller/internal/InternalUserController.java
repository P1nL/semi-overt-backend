package com.platform.auth.controller.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.auth.entity.User;
import com.platform.auth.mapper.UserMapper;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;


@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserMapper userMapper;

    /**
     * 婢跺嫮鎮?POST /batch 鐠囬攱鐪伴妴?     */
    @PostMapping("/batch")
    public Result<List<UserSummaryDto>> batchQuery(@RequestBody BatchUserQueryReq req) {
        if (req == null || req.getUserIds() == null || req.getUserIds().isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .in(User::getId, req.getUserIds()));

        return Result.ok(users.stream()
                .map(user -> UserSummaryDto.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .toList());
    }
}


