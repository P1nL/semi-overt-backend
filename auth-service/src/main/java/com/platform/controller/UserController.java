package com.platform.controller;

import com.platform.dto.req.UpdateProfileReq;
import com.platform.dto.resp.UserInfoResp;
import com.platform.dto.resp.UserProfileResp;
import com.platform.service.UserService;
import com.platform.util.Result;
import com.platform.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器，对外提供相关 HTTP 接口。
 */

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息
     * GET /api/v1/users/me
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<UserInfoResp> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(userService.getCurrentUserInfo(userId));
    }

    /**
     * 修改当前登录用户的个人资料
     * PUT /api/v1/users/me/profile
     */
    @PutMapping("/me/profile")
    @PreAuthorize("isAuthenticated()")
    public Result<UserInfoResp> updateProfile(@Valid @RequestBody UpdateProfileReq req) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(userService.updateProfile(userId, req));
    }

    /**
     * 处理 GET /{username}/profile 请求。
     */
    @GetMapping("/{username}/profile")
    public Result<UserProfileResp> getUserProfile(
            @PathVariable String username,
            @RequestParam(required = false, defaultValue = "all") String tab,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "页码最小为1") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "每页条数最小为1")
            @Max(value = 50, message = "每页条数最大为50") int pageSize
    ) {
        // 未登录时为 null，Service 层按访客处理
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return Result.ok(userService.getUserProfile(username, currentUserId, tab, page, pageSize));
    }
}