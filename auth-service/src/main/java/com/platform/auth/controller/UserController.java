package com.platform.auth.controller;

import com.platform.auth.api.req.UpdateProfileReq;
import com.platform.auth.api.resp.UserInfoResp;
import com.platform.auth.api.resp.UserProfileResp;
import com.platform.auth.service.UserService;
import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    @PutMapping("/me/profile")
    @PreAuthorize("isAuthenticated()")
    public Result<UserInfoResp> updateProfile(@Valid @RequestBody UpdateProfileReq req) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.ok(userService.updateProfile(userId, req));
    }

    @GetMapping("/{username}/profile")
    public Result<UserProfileResp> getUserProfile(
            @PathVariable String username,
            @RequestParam(required = false, defaultValue = "all") String tab,
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "Page must be at least 1") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 50, message = "Page size must be at most 50") int pageSize
    ) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return Result.ok(userService.getUserProfile(username, currentUserId, tab, page, pageSize));
    }
}
