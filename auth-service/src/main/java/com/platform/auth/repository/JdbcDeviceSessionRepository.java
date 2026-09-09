package com.platform.auth.repository;

import com.platform.auth.model.DeviceSession;
import com.platform.auth.model.RefreshTokenRotation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JdbcDeviceSessionRepository implements DeviceSessionRepository {

    private static final String ACTIVE = "ACTIVE";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<DeviceSession> sessionRowMapper = (rs, rowNum) -> new DeviceSession(
            rs.getString("session_id"),
            rs.getLong("user_id"),
            rs.getString("family_id"),
            rs.getString("status"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("last_used_at", LocalDateTime.class),
            rs.getObject("idle_expires_at", LocalDateTime.class),
            rs.getObject("absolute_expires_at", LocalDateTime.class),
            rs.getBoolean("persistent"),
            rs.getObject("revoked_at", LocalDateTime.class)
    );

    private final RowMapper<RefreshTokenRow> refreshTokenRowMapper = (rs, rowNum) -> new RefreshTokenRow(
            rs.getString("token_hash"),
            rs.getString("session_id"),
            rs.getString("family_id"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("consumed_at", LocalDateTime.class),
            rs.getString("replaced_by_hash")
    );

    public JdbcDeviceSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void createSession(String sessionId,
                              Long userId,
                              String familyId,
                              String refreshTokenHash,
                              LocalDateTime now,
                              LocalDateTime idleExpiresAt,
                              LocalDateTime absoluteExpiresAt,
                              boolean persistent) {
        jdbcTemplate.update("""
                INSERT INTO auth_device_sessions (
                    session_id, user_id, family_id, status, created_at, last_used_at,
                    idle_expires_at, absolute_expires_at, persistent, revoked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                sessionId, userId, familyId, ACTIVE, now, now, idleExpiresAt, absoluteExpiresAt, persistent);
        jdbcTemplate.update("""
                INSERT INTO auth_refresh_tokens (
                    token_hash, session_id, family_id, created_at, consumed_at, replaced_by_hash
                ) VALUES (?, ?, ?, ?, NULL, NULL)
                """,
                refreshTokenHash, sessionId, familyId, now);
    }

    @Override
    public RefreshTokenRotation rotateRefreshToken(String refreshTokenHash,
                                                    String replacementTokenHash,
                                                    LocalDateTime now,
                                                    LocalDateTime replacementIdleExpiresAt) {
        Optional<RefreshTokenRow> tokenResult = jdbcTemplate.query("""
                        SELECT token_hash, session_id, family_id, created_at, consumed_at, replaced_by_hash
                          FROM auth_refresh_tokens
                         WHERE token_hash = ?
                         FOR UPDATE
                        """,
                refreshTokenRowMapper,
                refreshTokenHash).stream().findFirst();
        if (tokenResult.isEmpty()) {
            return RefreshTokenRotation.unknown();
        }

        RefreshTokenRow token = tokenResult.get();
        Optional<DeviceSession> sessionResult = findSessionForUpdate(token.sessionId());
        if (sessionResult.isEmpty()) {
            return new RefreshTokenRotation(
                    RefreshTokenRotation.Status.UNKNOWN,
                    token.sessionId(), null, token.familyId(), null, false);
        }

        DeviceSession session = sessionResult.get();
        if (token.consumedAt() != null) {
            revokeSession(session.sessionId(), now);
            return rotation(RefreshTokenRotation.Status.REPLAYED, session);
        }
        if (!ACTIVE.equals(session.status())
                || session.absoluteExpiresAt() == null
                || !session.absoluteExpiresAt().isAfter(now)
                || session.idleExpiresAt() == null
                || !session.idleExpiresAt().isAfter(now)) {
            revokeSession(session.sessionId(), now);
            return rotation(RefreshTokenRotation.Status.EXPIRED, session);
        }

        int consumed = jdbcTemplate.update("""
                UPDATE auth_refresh_tokens
                   SET consumed_at = ?, replaced_by_hash = ?
                 WHERE token_hash = ? AND consumed_at IS NULL
                """, now, replacementTokenHash, refreshTokenHash);
        if (consumed != 1) {
            revokeSession(session.sessionId(), now);
            return rotation(RefreshTokenRotation.Status.REPLAYED, session);
        }

        jdbcTemplate.update("""
                INSERT INTO auth_refresh_tokens (
                    token_hash, session_id, family_id, created_at, consumed_at, replaced_by_hash
                ) VALUES (?, ?, ?, ?, NULL, NULL)
                """,
                replacementTokenHash, session.sessionId(), session.familyId(), now);

        LocalDateTime nextIdleExpiresAt = replacementIdleExpiresAt.isBefore(session.absoluteExpiresAt())
                ? replacementIdleExpiresAt
                : session.absoluteExpiresAt();
        jdbcTemplate.update("""
                UPDATE auth_device_sessions
                   SET last_used_at = ?, idle_expires_at = ?
                 WHERE session_id = ? AND status = ?
                """, now, nextIdleExpiresAt, session.sessionId(), ACTIVE);
        return rotation(RefreshTokenRotation.Status.ROTATED, session);
    }

    @Override
    public boolean touchForAccess(String sessionId,
                                  Long userId,
                                  LocalDateTime now,
                                  LocalDateTime nextIdleExpiresAt) {
        int updated = jdbcTemplate.update("""
                UPDATE auth_device_sessions
                   SET last_used_at = ?,
                       idle_expires_at = CASE
                           WHEN absolute_expires_at < ? THEN absolute_expires_at
                           ELSE ?
                       END
                 WHERE session_id = ?
                   AND user_id = ?
                   AND status = ?
                   AND absolute_expires_at > ?
                   AND idle_expires_at > ?
                """,
                now, nextIdleExpiresAt, nextIdleExpiresAt, sessionId, userId, ACTIVE, now, now);
        return updated == 1;
    }

    @Override
    public void revokeByRefreshToken(String refreshTokenHash, LocalDateTime now) {
        jdbcTemplate.query("""
                        SELECT token_hash, session_id, family_id, created_at, consumed_at, replaced_by_hash
                          FROM auth_refresh_tokens
                         WHERE token_hash = ?
                         FOR UPDATE
                        """,
                refreshTokenRowMapper,
                refreshTokenHash).stream().findFirst().ifPresent(token -> revokeSession(token.sessionId(), now));
    }

    @Override
    public void revokeSession(String sessionId, LocalDateTime now) {
        jdbcTemplate.update("""
                UPDATE auth_device_sessions
                   SET status = ?, revoked_at = COALESCE(revoked_at, ?)
                 WHERE session_id = ?
                """, "REVOKED", now, sessionId);
    }

    @Override
    public void revokeAllForUser(Long userId, LocalDateTime now) {
        jdbcTemplate.update("""
                UPDATE auth_device_sessions
                   SET status = ?, revoked_at = COALESCE(revoked_at, ?)
                 WHERE user_id = ? AND status = ?
                """, "REVOKED", now, userId, ACTIVE);
    }

    @Override
    public int pruneExpiredSessions(LocalDateTime now, int batchSize) {
        int limit = Math.max(1, Math.min(batchSize, 500));
        List<String> sessionIds = jdbcTemplate.queryForList("""
                        SELECT session_id
                          FROM auth_device_sessions
                         WHERE absolute_expires_at <= ?
                         ORDER BY absolute_expires_at, session_id
                         LIMIT ?
                        """,
                String.class,
                now,
                limit);
        if (sessionIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(sessionIds.size(), "?"));
        jdbcTemplate.update("DELETE FROM auth_refresh_tokens WHERE session_id IN (" + placeholders + ")",
                sessionIds.toArray());
        jdbcTemplate.update("DELETE FROM auth_device_sessions WHERE session_id IN (" + placeholders + ")",
                sessionIds.toArray());
        return sessionIds.size();
    }

    private Optional<DeviceSession> findSessionForUpdate(String sessionId) {
        return jdbcTemplate.query("""
                        SELECT session_id, user_id, family_id, status, created_at, last_used_at,
                               idle_expires_at, absolute_expires_at, persistent, revoked_at
                          FROM auth_device_sessions
                         WHERE session_id = ?
                         FOR UPDATE
                        """,
                sessionRowMapper,
                sessionId).stream().findFirst();
    }

    private RefreshTokenRotation rotation(RefreshTokenRotation.Status status, DeviceSession session) {
        return new RefreshTokenRotation(
                status,
                session.sessionId(),
                session.userId(),
                session.familyId(),
                session.absoluteExpiresAt(),
                session.persistent());
    }

    private record RefreshTokenRow(
            String tokenHash,
            String sessionId,
            String familyId,
            LocalDateTime createdAt,
            LocalDateTime consumedAt,
            String replacedByHash
    ) {
    }
}
