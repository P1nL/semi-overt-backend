package com.platform.auth.controller;

import com.platform.auth.api.req.UpdateProfileReq;
import com.platform.auth.api.resp.UserInfoResp;
import com.platform.auth.api.resp.UserProfileResp;
import com.platform.auth.service.UserService;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public Result<UserInfoResp> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(userService.getCurrentUserInfo(userId));
    }

    @PutMapping({"/me", "/me/profile"})
    @PreAuthorize("isAuthenticated()")
    public Result<UserInfoResp> updateProfile(@Valid @RequestBody UpdateProfileReq req) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(userService.updateProfile(userId, req));
    }

    @GetMapping("/{identifier}/profile")
    public Result<UserProfileResp> getUserProfile(
            @PathVariable String identifier,
            @RequestParam(required = false, defaultValue = "") String tab,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return Result.ok(userService.getUserProfile(identifier, currentUserId, tab, limit, page, pageSize));
    }
}
