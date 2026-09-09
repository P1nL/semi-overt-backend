package com.platform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.auth.api.req.ForgotPasswordReq;
import com.platform.auth.api.req.LoginReq;
import com.platform.auth.api.req.RegisterCodeReq;
import com.platform.auth.api.req.RegisterReq;
import com.platform.auth.api.req.ResetPasswordReq;
import com.platform.auth.api.resp.AuthResp;
import com.platform.auth.api.resp.AuthSessionResult;
import com.platform.auth.entity.User;
import com.platform.auth.mapper.UserMapper;
import com.platform.auth.model.EmailVerificationCode;
import com.platform.auth.model.PasswordResetToken;
import com.platform.auth.repository.DeviceSessionRepository;
import com.platform.auth.repository.EmailVerificationCodeRepository;
import com.platform.auth.repository.PasswordResetTokenRepository;
import com.platform.auth.service.AuthService;
import com.platform.auth.service.JdbcMailBudget;
import com.platform.auth.service.TurnstileService;
import com.platform.auth.session.DeviceSessionService;
import com.platform.kernel.enums.UserRole;
import com.platform.kernel.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String REGISTER_PURPOSE = "REGISTER";
    private static final String RESET_PURPOSE = "RESET_PASSWORD";
    private static final int CODE_TTL_MINUTES = 10;
    private static final int CODE_MAX_ATTEMPTS = 5;
    private static final int CODE_RESEND_SECONDS = 60;
    private static final Set<String> RESERVED_NAMES = Set.of("me", "admin", "system");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TurnstileService turnstileService;
    private final JavaMailSender mailSender;
    private final EmailVerificationCodeRepository emailCodes;
    private final PasswordResetTokenRepository resetTokens;
    private final DeviceSessionRepository deviceSessions;
    private final DeviceSessionService sessions;
    private final JdbcMailBudget mailBudget;
    private final TransactionTemplate transactions;
    private final TransactionTemplate attempts;
    private final SecureRandom random = new SecureRandom();
    private final String mailFrom;
    private final String resetCodePepper;
    private final boolean registrationCodeRequired;

    public AuthServiceImpl(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            TurnstileService turnstileService,
            JavaMailSender mailSender,
            EmailVerificationCodeRepository emailCodes,
            PasswordResetTokenRepository resetTokens,
            DeviceSessionRepository deviceSessions,
            DeviceSessionService sessions,
            JdbcMailBudget mailBudget,
            PlatformTransactionManager transactionManager,
            @Value("${spring.mail.username:noreply@example.com}") String mailFrom,
            @Value("${platform.auth.reset-code-pepper:}") String resetCodePepper,
            @Value("${platform.auth.registration-code-required:true}") boolean registrationCodeRequired,
            @Value("${platform.auth.refresh-absolute-days:90}") long refreshAbsoluteDays) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.turnstileService = turnstileService;
        this.mailSender = mailSender;
        this.emailCodes = emailCodes;
        this.resetTokens = resetTokens;
        this.deviceSessions = deviceSessions;
        this.sessions = sessions;
        this.mailBudget = mailBudget;
        this.mailFrom = mailFrom;
        this.resetCodePepper = resetCodePepper == null ? "" : resetCodePepper;
        this.registrationCodeRequired = registrationCodeRequired;
        if (this.resetCodePepper.isBlank()) {
            throw new IllegalStateException("platform.auth.reset-code-pepper must be configured");
        }
        if (refreshAbsoluteDays < 1 || refreshAbsoluteDays > 90) {
            throw new IllegalStateException("platform.auth.refresh-absolute-days must be between 1 and 90");
        }
        this.attempts = new TransactionTemplate(transactionManager);
        this.attempts.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setTimeout(5);
        this.attempts.setTimeout(5);
    }

    @Override
    public AuthSessionResult registerWithSession(RegisterReq req, String previousRefreshToken) {
        String username = normalizeUsername(req.getUsername());
        String email = normalizeEmail(req.getEmail());
        requirePassword(req.getPassword(), true);
        if (!registrationCodeRequired) {
            turnstileService.verify(req.getCfTurnstileToken());
        }
        User user = transactions.execute(status -> {
            if (RESERVED_NAMES.contains(username.toLowerCase(Locale.ROOT))) {
                throw BusinessException.badRequest("Username is reserved");
            }
            if (findByUsername(username) != null) {
                throw BusinessException.conflict("Username already exists");
            }
            if (findByEmail(email) != null) {
                throw BusinessException.conflict("Email already registered");
            }
            if (registrationCodeRequired) {
                verifyAndConsumeRegistrationCode(email, req.getEmailCode());
            }
            User created = new User();
            created.setUsername(username);
            created.setNickname(username);
            created.setEmail(email);
            created.setPassword(passwordEncoder.encode(req.getPassword()));
            created.setSessionVersion(0L);
            created.setRole(UserRole.USER);
            try {
                userMapper.insert(created);
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("Username or email already exists");
            }
            return created;
        });
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("Registration transaction returned no user");
        }
        return openSession(user, 0L, true, previousRefreshToken);
    }

    @Override
    public void requestRegistrationCode(RegisterCodeReq req, String clientIp) {
        String email = normalizeEmail(req.getEmail());
        turnstileService.verify(req.getCfTurnstileToken());
        mailBudget.acquire(email, REGISTER_PURPOSE, clientIp);
        if (findByEmail(email) != null) {
            throw BusinessException.conflict("Email already registered");
        }
        LocalDateTime now = LocalDateTime.now();
        if (emailCodes.findLatestUnused(email, REGISTER_PURPOSE)
                .filter(code -> code.getCreatedAt() != null
                        && code.getCreatedAt().isAfter(now.minusSeconds(CODE_RESEND_SECONDS)))
                .isPresent()) {
            return;
        }
        String code = generateCode();
        EmailVerificationCode record = new EmailVerificationCode();
        record.setEmail(email);
        record.setPurpose(REGISTER_PURPOSE);
        record.setCodeSalt(createSalt());
        record.setCodeHash(hashCode(record.getCodeSalt(), code));
        record.setAttempts(0);
        record.setExpiresAt(now.plusMinutes(CODE_TTL_MINUTES));
        record.setCreatedAt(now);
        emailCodes.markUnusedAsUsed(email, REGISTER_PURPOSE);
        EmailVerificationCode saved = emailCodes.save(record);
        try {
            sendCode(email, code, "Registration verification code", "You are registering a semi-overt account.");
        } catch (RuntimeException ex) {
            if (saved != null && saved.getId() != null) {
                emailCodes.deleteById(saved.getId());
            }
            throw BusinessException.serverError("Mail service is temporarily unavailable");
        }
    }

    @Override
    public AuthSessionResult loginWithSession(LoginReq req, String previousRefreshToken) {
        String account = req.getAccount().trim();
        requirePassword(req.getPassword(), false);
        User candidate = account.contains("@")
                ? findByEmail(normalizeEmail(account))
                : findByUsername(account);
        if (candidate == null || candidate.getPassword() == null
                || !passwordEncoder.matches(req.getPassword(), candidate.getPassword())) {
            throw BusinessException.badRequest("Invalid account or password");
        }
        Long expectedVersion = candidate.getSessionVersion() == null ? 0L : candidate.getSessionVersion();
        return openSession(candidate, expectedVersion, req.isRememberMe(), previousRefreshToken);
    }

    @Override
    public AuthSessionResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw BusinessException.unauthorized("Authentication required");
        }
        DeviceSessionService.Outcome outcome = sessions.refresh(refreshToken);
        if (outcome.status() != DeviceSessionService.Status.ISSUED || outcome.tokens() == null) {
            throw BusinessException.unauthorized("Authentication required");
        }
        DeviceSessionService.Tokens tokens = outcome.tokens();
        User user = userMapper.selectById(tokens.userId());
        if (user == null) {
            throw BusinessException.unauthorized("Authentication required");
        }
        return sessionResult(tokens, user);
    }

    @Override
    public void logout(String refreshToken) {
        sessions.logout(refreshToken);
    }

    @Override
    public void forgotPassword(ForgotPasswordReq req, String clientIp) {
        String email = normalizeEmail(req.getEmail());
        mailBudget.acquire(email, RESET_PURPOSE, clientIp);
        User user = findByEmail(email);
        if (user == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (resetTokens.findLatestUnusedByUserId(user.getId())
                .filter(token -> token.getCreatedAt() != null
                        && token.getCreatedAt().isAfter(now.minusSeconds(CODE_RESEND_SECONDS)))
                .isPresent()) {
            return;
        }
        String code = generateCode();
        PasswordResetToken record = new PasswordResetToken();
        record.setToken(UUID.randomUUID().toString().replace("-", ""));
        record.setUserId(user.getId());
        record.setCodeSalt(createSalt());
        record.setCodeHash(hashCode(record.getCodeSalt(), code));
        record.setAttempts(0);
        record.setExpiresAt(now.plusMinutes(CODE_TTL_MINUTES));
        record.setCreatedAt(now);
        PasswordResetToken saved = resetTokens.save(record);
        try {
            sendCode(email, code, "Password reset verification code", "You requested a password reset.");
        } catch (RuntimeException ex) {
            if (saved != null) {
                resetTokens.deleteByToken(saved.getToken());
            }
            throw BusinessException.serverError("Mail service is temporarily unavailable");
        }
    }

    @Override
    public void resetPassword(ResetPasswordReq req) {
        requirePassword(req.getNewPassword(), true);
        String email = normalizeEmail(req.getEmail());
        User user = findByEmail(email);
        if (user == null) {
            throw BusinessException.badRequest("Verification code is invalid or expired");
        }
        PasswordResetToken token = resetTokens.findLatestUnusedByUserId(user.getId())
                .orElseThrow(() -> BusinessException.badRequest("Verification code is invalid or expired"));
        LocalDateTime now = LocalDateTime.now();
        if (token.getUsedAt() != null || token.getCodeSalt() == null || token.getCodeHash() == null
                || token.getExpiresAt() == null || !token.getExpiresAt().isAfter(now)
                || token.getAttempts() >= CODE_MAX_ATTEMPTS) {
            throw BusinessException.badRequest("Verification code is invalid or expired");
        }
        String submittedHash = hashCode(token.getCodeSalt(), req.getCode());
        if (!constantTimeEquals(token.getCodeHash(), submittedHash)) {
            attempts.executeWithoutResult(s -> resetTokens.incrementAttemptsIfAllowed(token.getToken(), CODE_MAX_ATTEMPTS, now));
            throw BusinessException.badRequest("Verification code is invalid or expired");
        }
        String encodedPassword = passwordEncoder.encode(req.getNewPassword());
        transactions.executeWithoutResult(status -> {
            User lockedUser = userMapper.selectByIdForUpdate(token.getUserId());
            if (lockedUser == null || !email.equals(normalizeEmail(lockedUser.getEmail()))) {
                throw BusinessException.badRequest("Verification code is invalid or expired");
            }
            if (!resetTokens.consumeIfValid(token.getToken(), submittedHash, CODE_MAX_ATTEMPTS, LocalDateTime.now())) {
                throw BusinessException.badRequest("Verification code is invalid or expired");
            }
            // Keep the lock order user -> device sessions, matching refresh/session code.
            deviceSessions.revokeAllForUser(lockedUser.getId(), LocalDateTime.now());
            userMapper.updatePasswordAndRotateSession(lockedUser.getId(), encodedPassword);
        });
    }

    private AuthSessionResult openSession(User user,
                                          Long expectedVersion,
                                          boolean persistent,
                                          String previousRefreshToken) {
        DeviceSessionService.Outcome outcome = sessions.openForAuthenticatedUser(
                user.getId(), expectedVersion, persistent, previousRefreshToken);
        if (outcome.status() == DeviceSessionService.Status.VERSION_CHANGED) {
            throw BusinessException.badRequest("Invalid account or password");
        }
        if (outcome.status() != DeviceSessionService.Status.ISSUED || outcome.tokens() == null) {
            throw BusinessException.serverError("Unable to create authentication session");
        }
        return sessionResult(outcome.tokens(), user);
    }

    private AuthSessionResult sessionResult(DeviceSessionService.Tokens tokens, User user) {
        long maxAge = Math.max(0,
                Duration.between(LocalDateTime.now(), tokens.absoluteExpiresAt()).toSeconds());
        AuthResp response = AuthResp.builder()
                .token(tokens.accessToken())
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname() == null ? user.getUsername() : user.getNickname())
                .email(user.getEmail())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .build();
        return new AuthSessionResult(response, tokens.refreshToken(), maxAge, tokens.persistent());
    }

    private void verifyAndConsumeRegistrationCode(String email, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw BusinessException.badRequest("Registration code is invalid or expired");
        }
        EmailVerificationCode record = emailCodes.findLatestUnused(email, REGISTER_PURPOSE)
                .orElseThrow(() -> BusinessException.badRequest("Registration code is invalid or expired"));
        LocalDateTime now = LocalDateTime.now();
        if (record.getCodeSalt() == null || record.getCodeHash() == null || record.getExpiresAt() == null
                || !record.getExpiresAt().isAfter(now) || record.getAttempts() >= CODE_MAX_ATTEMPTS) {
            throw BusinessException.badRequest("Registration code is invalid or expired");
        }
        String submittedHash = hashCode(record.getCodeSalt(), code);
        if (!constantTimeEquals(record.getCodeHash(), submittedHash)) {
            attempts.executeWithoutResult(s -> emailCodes.incrementAttemptsIfAllowed(record.getId(), CODE_MAX_ATTEMPTS, now));
            throw BusinessException.badRequest("Registration code is invalid or expired");
        }
        if (!emailCodes.consumeIfValid(record.getId(), submittedHash, CODE_MAX_ATTEMPTS, now)) {
            throw BusinessException.badRequest("Registration code is invalid or expired");
        }
    }

    private void sendCode(String email, String code, String subject, String intro) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(intro + "\n\nVerification code: " + code
                + "\n\nThis code expires in " + CODE_TTL_MINUTES + " minutes.");
        mailSender.send(message);
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void requirePassword(String password, boolean requireMinimum) {
        if (password == null) {
            throw BusinessException.badRequest("Password is required");
        }
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if ((requireMinimum && bytes < 8) || bytes > 72) {
            throw BusinessException.badRequest(requireMinimum
                    ? "Password must be 8 to 72 UTF-8 bytes"
                    : "Password must not exceed 72 UTF-8 bytes");
        }
    }

    private String generateCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    private String createSalt() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashCode(String salt, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(resetCodePepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal((salt + ":" + code)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
