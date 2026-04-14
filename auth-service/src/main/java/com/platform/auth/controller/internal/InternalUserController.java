package com.platform.auth.controller.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.auth.entity.User;
import com.platform.auth.mapper.UserMapper;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;


@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserMapper userMapper;

    /**
     * POST /batch — 按 ID 批量查询用户摘要
     */
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

    /**
     * GET /search?keyword=x&limit=10 — 按用户名或昵称模糊搜索用户
     */
    @GetMapping("/search")
    public Result<List<UserSummaryDto>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit) {
        if (keyword == null || keyword.isBlank()) {
            return Result.ok(Collections.emptyList());
        }

        int safeLimit = Math.max(1, Math.min(limit, 50));

        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .and(w -> w.like(User::getUsername, keyword.trim())
                                   .or()
                                   .like(User::getNickname, keyword.trim()))
                        .last("LIMIT " + safeLimit));

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


