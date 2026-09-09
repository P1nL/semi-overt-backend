package com.platform.auth.repository;

import com.platform.auth.model.EmailVerificationCode;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailVerificationCodeRepository {
    EmailVerificationCode save(EmailVerificationCode code);
    Optional<EmailVerificationCode> findLatestUnused(String email, String purpose);
    void markUnusedAsUsed(String email, String purpose);
    void deleteById(Long id);
    boolean consumeIfValid(Long id, String codeHash, int maxAttempts, LocalDateTime now);
    boolean incrementAttemptsIfAllowed(Long id, int maxAttempts, LocalDateTime now);
}
