package com.platform.auth.model;

import java.time.LocalDateTime;

public record DeviceSession(
        String sessionId,
        Long userId,
        String familyId,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastUsedAt,
        LocalDateTime idleExpiresAt,
        LocalDateTime absoluteExpiresAt,
        boolean persistent,
        LocalDateTime revokedAt
) {
}
