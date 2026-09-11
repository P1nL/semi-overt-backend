package com.platform.notification.controller;

import com.platform.kernel.util.Result;
import com.platform.kernel.util.SecurityUtils;
import com.platform.notification.api.resp.NotificationResp;
import com.platform.notification.service.NotificationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The notification endpoint intentionally preserves the source contract:
 * GET /api[/v1]/notifications?limit=20 returning Result<List<Notification>>.
 * No new unread-count, mark-read, or page/offset contract is introduced here.
 */
@RestController
@RequestMapping({"/api/notifications", "/api/v1/notifications"})
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<NotificationResp>> list(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(notificationQueryService.listForUser(
                SecurityUtils.getCurrentUserId(), limit));
    }
}