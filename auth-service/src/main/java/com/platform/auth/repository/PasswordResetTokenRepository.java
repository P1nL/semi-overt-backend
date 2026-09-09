package com.platform.auth.repository;

import com.platform.auth.model.PasswordResetToken;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findLatestUnusedByUserId(Long userId);
    void markUnusedAsUsedByUserId(Long userId);
    void deleteByToken(String token);
    boolean consumeIfValid(String token, String codeHash, int maxAttempts, LocalDateTime now);
    boolean incrementAttemptsIfAllowed(String token, int maxAttempts, LocalDateTime now);
}
