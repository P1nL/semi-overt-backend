package com.platform.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PasswordResetToken {
    private String token;
    private Long userId;
    private String codeSalt;
    private String codeHash;
    private int attempts;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
