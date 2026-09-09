package com.platform.auth.repository;

import com.platform.auth.model.PasswordResetToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class JdbcPasswordResetTokenRepository implements PasswordResetTokenRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<PasswordResetToken> mapper = (rs, rowNum) -> {
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(rs.getString("token"));
        token.setUserId(rs.getLong("user_id"));
        token.setCodeSalt(rs.getString("code_salt"));
        token.setCodeHash(rs.getString("code_hash"));
        token.setAttempts(rs.getInt("attempts"));
        token.setExpiresAt(rs.getObject("expires_at", LocalDateTime.class));
        token.setUsedAt(rs.getObject("used_at", LocalDateTime.class));
        token.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return token;
    };

    public JdbcPasswordResetTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        if (token.getCreatedAt() == null) {
            token.setCreatedAt(LocalDateTime.now());
        }
        jdbc.update("""
                INSERT INTO password_reset_tokens
                    (token, user_id, code_salt, code_hash, attempts, expires_at, used_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, token.getToken(), token.getUserId(), token.getCodeSalt(), token.getCodeHash(),
                token.getAttempts(), token.getExpiresAt(), token.getUsedAt(), token.getCreatedAt());
        return token;
    }

    @Override
    public Optional<PasswordResetToken> findLatestUnusedByUserId(Long userId) {
        return jdbc.query("""
                        SELECT token, user_id, code_salt, code_hash, attempts,
                               expires_at, used_at, created_at
                          FROM password_reset_tokens
                         WHERE user_id = ? AND used_at IS NULL
                         ORDER BY created_at DESC, token DESC
                         LIMIT 1
                        """, mapper, userId).stream().findFirst();
    }

    @Override
    public void markUnusedAsUsedByUserId(Long userId) {
        jdbc.update("""
                UPDATE password_reset_tokens SET used_at = ?
                 WHERE user_id = ? AND used_at IS NULL
                """, LocalDateTime.now(), userId);
    }

    @Override
    public void deleteByToken(String token) {
        jdbc.update("DELETE FROM password_reset_tokens WHERE token = ?", token);
    }

    @Override
    public boolean consumeIfValid(String token, String codeHash, int maxAttempts, LocalDateTime now) {
        return jdbc.update("""
                UPDATE password_reset_tokens SET used_at = ?
                 WHERE token = ? AND code_hash = ? AND used_at IS NULL
                   AND expires_at > ? AND attempts < ?
                """, now, token, codeHash, now, maxAttempts) == 1;
    }

    @Override
    public boolean incrementAttemptsIfAllowed(String token, int maxAttempts, LocalDateTime now) {
        return jdbc.update("""
                UPDATE password_reset_tokens SET attempts = attempts + 1
                 WHERE token = ? AND used_at IS NULL AND expires_at > ? AND attempts < ?
                """, token, now, maxAttempts) == 1;
    }
}
