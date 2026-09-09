package com.platform.auth.repository;

import com.platform.auth.model.EmailVerificationCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class JdbcEmailVerificationCodeRepository implements EmailVerificationCodeRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<EmailVerificationCode> mapper = (rs, rowNum) -> {
        EmailVerificationCode code = new EmailVerificationCode();
        code.setId(rs.getLong("id"));
        code.setEmail(rs.getString("email"));
        code.setPurpose(rs.getString("purpose"));
        code.setCodeSalt(rs.getString("code_salt"));
        code.setCodeHash(rs.getString("code_hash"));
        code.setAttempts(rs.getInt("attempts"));
        code.setExpiresAt(rs.getObject("expires_at", LocalDateTime.class));
        code.setUsedAt(rs.getObject("used_at", LocalDateTime.class));
        code.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return code;
    };

    public JdbcEmailVerificationCodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EmailVerificationCode save(EmailVerificationCode code) {
        if (code.getCreatedAt() == null) {
            code.setCreatedAt(LocalDateTime.now());
        }
        KeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO email_verification_codes
                        (email, purpose, code_salt, code_hash, attempts, expires_at, used_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setString(1, code.getEmail());
            statement.setString(2, code.getPurpose());
            statement.setString(3, code.getCodeSalt());
            statement.setString(4, code.getCodeHash());
            statement.setInt(5, code.getAttempts());
            statement.setObject(6, code.getExpiresAt());
            statement.setObject(7, code.getUsedAt());
            statement.setObject(8, code.getCreatedAt());
            return statement;
        }, holder);
        if (holder.getKey() != null) {
            code.setId(holder.getKey().longValue());
        }
        return code;
    }

    @Override
    public Optional<EmailVerificationCode> findLatestUnused(String email, String purpose) {
        return jdbc.query("""
                        SELECT id, email, purpose, code_salt, code_hash, attempts,
                               expires_at, used_at, created_at
                          FROM email_verification_codes
                         WHERE email = ? AND purpose = ? AND used_at IS NULL
                         ORDER BY created_at DESC, id DESC
                         LIMIT 1
                        """, mapper, email, purpose).stream().findFirst();
    }

    @Override
    public void markUnusedAsUsed(String email, String purpose) {
        jdbc.update("""
                UPDATE email_verification_codes SET used_at = ?
                 WHERE email = ? AND purpose = ? AND used_at IS NULL
                """, LocalDateTime.now(), email, purpose);
    }

    @Override
    public void deleteById(Long id) {
        jdbc.update("DELETE FROM email_verification_codes WHERE id = ?", id);
    }

    @Override
    public boolean consumeIfValid(Long id, String codeHash, int maxAttempts, LocalDateTime now) {
        return jdbc.update("""
                UPDATE email_verification_codes SET used_at = ?
                 WHERE id = ? AND code_hash = ? AND used_at IS NULL
                   AND expires_at > ? AND attempts < ?
                """, now, id, codeHash, now, maxAttempts) == 1;
    }

    @Override
    public boolean incrementAttemptsIfAllowed(Long id, int maxAttempts, LocalDateTime now) {
        return jdbc.update("""
                UPDATE email_verification_codes SET attempts = attempts + 1
                 WHERE id = ? AND used_at IS NULL AND expires_at > ? AND attempts < ?
                """, id, now, maxAttempts) == 1;
    }
}
