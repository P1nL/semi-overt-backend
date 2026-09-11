package com.platform.notification.api.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Public notification DTO kept compatible with the monolith response.
 * Delivery bookkeeping and S3 decision metadata are intentionally omitted.
 */
@Data
public class NotificationResp {

    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String content;
    private LocalDateTime createdAt;
}