package com.platform.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EmailVerificationCode {
    private Long id;
    private String email;
    private String purpose;
    private String codeSalt;
    private String codeHash;
    private int attempts;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
