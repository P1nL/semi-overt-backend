package com.platform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.auth.api.req.ForgotPasswordReq;
import com.platform.auth.api.req.LoginReq;
import com.platform.auth.api.req.RegisterReq;
import com.platform.auth.api.req.ResetPasswordReq;
import com.platform.auth.api.resp.AuthResp;
import com.platform.auth.entity.User;
import com.platform.kernel.exception.BusinessException;
import com.platform.kernel.enums.UserRole;
import com.platform.auth.mapper.UserMapper;
import com.platform.auth.service.AuthService;
import com.platform.auth.service.TurnstileService;
import com.platform.auth.util.JwtHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现，负责注册、登录和密码找回主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String KEY_PWD_RESET = "pwd:reset:";
    private static final String KEY_PWD_RESET_LOCK = "pwd:reset:lock:";
    private static final Set<String> RESERVED_NAMES = Set.of("me", "admin", "system");

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtHelper jwtHelper;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final TurnstileService turnstileService;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${platform.reset-pwd-token-ttl-minutes}")
    private long resetPwdTtlMinutes;

    @Value("${platform.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    /**
     * 注册新用户，并立即返回登录态。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResp register(RegisterReq req) {
        turnstileService.verify(req.getCfTurnstileToken());

        if (RESERVED_NAMES.contains(req.getUsername().toLowerCase())) {
            throw new BusinessException(400, "Username is reserved");
        }
        if (existsByUsername(req.getUsername())) {
            throw BusinessException.conflict("Username already exists");
        }
        if (existsByEmail(req.getEmail())) {
            throw BusinessException.conflict("Email already registered");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setNickname(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(UserRole.USER);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw BusinessException.conflict("用户名或邮箱已被注册");
        }

        String token = jwtHelper.createToken(user.getId(), user.getUsername(), UserRole.USER.name(), false);
        log.info("User registered: userId={}, username={}", user.getId(), user.getUsername());
        return buildAuthResp(token, user);
    }

    /**
     * 使用用户名或邮箱登录，校验成功后签发 token。
     */
    @Override
    public AuthResp login(LoginReq req) {
        User user = req.getAccount().contains("@")
                ? findByEmail(req.getAccount())
                : findByUsername(req.getAccount());

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw BusinessException.badRequest("Invalid account or password");
        }

        String token = jwtHelper.createToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                req.isRememberMe()
        );
        log.info("User logged in: userId={}, username={}, rememberMe={}",
                user.getId(), user.getUsername(), req.isRememberMe());
        return buildAuthResp(token, user);
    }

    /**
     * 发起找回密码流程。
     * 已发送过邮件的邮箱会进入冷却期，未知邮箱则静默忽略以避免泄露用户是否存在。
     */
    @Override
    public void forgotPassword(ForgotPasswordReq req) {
        String email = req.getEmail().toLowerCase().trim();
        String lockKey = KEY_PWD_RESET_LOCK + email;

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", resetPwdTtlMinutes, TimeUnit.MINUTES);
        if (Boolean.FALSE.equals(locked)) {
            throw BusinessException.tooManyRequests(
                    "Reset email already sent, try again in " + resetPwdTtlMinutes + " minutes");
        }

        User user = findByEmail(email);
        if (user == null) {
            log.info("Forgot password skipped for unknown email: {}", email);
            return;
        }

        String resetToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                KEY_PWD_RESET + resetToken,
                String.valueOf(user.getId()),
                resetPwdTtlMinutes,
                TimeUnit.MINUTES
        );

        try {
            sendResetEmail(user.getUsername(), email, resetToken);
        } catch (MailException e) {
            redisTemplate.delete(KEY_PWD_RESET + resetToken);
            redisTemplate.delete(lockKey);
            log.error("Failed to send reset email: userId={}, email={}", user.getId(), email, e);
            throw BusinessException.serverError("Mail service is temporarily unavailable");
        }

        log.info("Reset email sent: userId={}, email={}", user.getId(), email);
    }

    /**
     * 根据重置 token 更新用户密码，并销毁已使用的重置凭证。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordReq req) {
        String tokenKey = KEY_PWD_RESET + req.getToken();
        String userIdStr = redisTemplate.opsForValue().get(tokenKey);
        if (userIdStr == null) {
            throw BusinessException.badRequest("Reset link is invalid or expired");
        }

        Long userId = Long.valueOf(userIdStr);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("User not found");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
        redisTemplate.delete(tokenKey);
        log.info("Password reset: userId={}", userId);
    }

    /**
     * 判断用户名是否已被占用。
     */
    private boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }

    /**
     * 判断邮箱是否已被注册。
     */
    private boolean existsByEmail(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
    }

    /**
     * 按用户名查询用户。
     */
    private User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    /**
     * 按邮箱查询用户。
     */
    private User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    /**
     * 统一构造认证成功后的返回体。
     */
    private AuthResp buildAuthResp(String token, User user) {
        return AuthResp.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    /**
     * 发送重置密码邮件，把一次性 token 拼接到前端重置页面链接中。
     */
    private void sendResetEmail(String username, String toEmail, String resetToken) {
        String resetLink = UriComponentsBuilder
                .fromUriString(frontendBaseUrl)
                .path("/reset-password")
                .queryParam("token", resetToken)
                .build()
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(toEmail);
        message.setSubject("Password reset");
        message.setText(
                "Hi " + username + ",\n\n"
                        + "Use the link below to reset your password within "
                        + resetPwdTtlMinutes
                        + " minutes:\n\n"
                        + resetLink
                        + "\n\nIf you did not request this, you can ignore this email.\n\nNow Demo"
        );
        mailSender.send(message);
    }
}

