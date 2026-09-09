package com.platform.auth.repository;

import com.platform.auth.model.RefreshTokenRotation;

import java.time.LocalDateTime;

public interface DeviceSessionRepository {

    void createSession(String sessionId,
                       Long userId,
                       String familyId,
                       String refreshTokenHash,
                       LocalDateTime now,
                       LocalDateTime idleExpiresAt,
                       LocalDateTime absoluteExpiresAt,
                       boolean persistent);

    RefreshTokenRotation rotateRefreshToken(String refreshTokenHash,
                                             String replacementTokenHash,
                                             LocalDateTime now,
                                             LocalDateTime replacementIdleExpiresAt);

    boolean touchForAccess(String sessionId,
                           Long userId,
                           LocalDateTime now,
                           LocalDateTime nextIdleExpiresAt);

    void revokeByRefreshToken(String refreshTokenHash, LocalDateTime now);

    void revokeSession(String sessionId, LocalDateTime now);

    void revokeAllForUser(Long userId, LocalDateTime now);

    int pruneExpiredSessions(LocalDateTime now, int batchSize);
}
