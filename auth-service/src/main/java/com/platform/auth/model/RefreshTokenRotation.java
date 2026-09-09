package com.platform.auth.model;

import java.time.LocalDateTime;

public record RefreshTokenRotation(
        Status status,
        String sessionId,
        Long userId,
        String familyId,
        LocalDateTime absoluteExpiresAt,
        boolean persistent
) {

    public enum Status {
        ROTATED,
        UNKNOWN,
        REPLAYED,
        EXPIRED
    }

    public static RefreshTokenRotation unknown() {
        return new RefreshTokenRotation(Status.UNKNOWN, null, null, null, null, false);
    }
}
