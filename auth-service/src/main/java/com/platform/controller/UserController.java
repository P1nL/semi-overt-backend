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
 * 用户模块接口
 * 基础路径：/api/v1/users
 *
 * 修复记录（相对于初始版本）：
 *   1. 新增 tab 查询参数，对齐接口文档 10.3
 *   2. getUserProfile 透传 currentUserId，Service 层判断是否本人访问
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
     * 查看指定用户的公开主页
     * GET /api/v1/users/{username}/profile?tab=approved&page=1&pageSize=10
     *
     * tab 可选值：all / approved / pending / returned / rejected / draft
     *   - 他人访问时 tab 参数无效，始终只返回 APPROVED 内容
     *   - 本人 / 管理员访问时 tab 生效，可按状态过滤
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