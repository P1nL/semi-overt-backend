package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.dto.req.ForgotPasswordReq;
import com.platform.dto.req.LoginReq;
import com.platform.dto.req.RegisterReq;
import com.platform.dto.req.ResetPasswordReq;
import com.platform.dto.resp.AuthResp;
import com.platform.entity.User;
import com.platform.enums.UserRole;
import com.platform.exception.BusinessException;
import com.platform.mapper.UserMapper;
import com.platform.service.AuthService;
import com.platform.util.JwtHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * 认证服务实现
 *
 * Redis Key 规范（认证模块）：
 *   jwt:blacklist:{token}          → 登出黑名单，TTL = token 剩余有效期
 *   pwd:reset:{uuid}               → 重置令牌 → userId，TTL = 15min
 *   pwd:reset:lock:{email}         → 发送频率锁，TTL = 15min（防重复发送）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtHelper jwtHelper;
    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;

    /** 发件人地址，与 application.yml spring.mail.username 保持一致 */
    @Value("${spring.mail.username}")
    private String mailFrom;

    /** 重置密码令牌有效期（分钟），来自 platform.reset-pwd-token-ttl-minutes */
    @Value("${platform.reset-pwd-token-ttl-minutes}")
    private long resetPwdTtlMinutes;

    /** 前端地址，用于拼接重置密码页面链接 */
    @Value("${platform.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    // ==================== Redis Key 常量 ====================
    private static final String KEY_JWT_BLACKLIST  = "jwt:blacklist:";
    private static final String KEY_PWD_RESET      = "pwd:reset:";
    private static final String KEY_PWD_RESET_LOCK = "pwd:reset:lock:";

    /** 系统保留词，不允许注册为用户名 */
    private static final Set<String> RESERVED_NAMES = Set.of("me", "admin", "system");

    // ==================== 注册 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResp register(RegisterReq req) {
        if (RESERVED_NAMES.contains(req.getUsername().toLowerCase())) {
            throw new BusinessException(400, "该用户名为系统保留词，请换一个");
        }

        // 1. 校验用户名唯一
        if (existsByUsername(req.getUsername())) {
            throw BusinessException.conflict("用户名已被占用");
        }
        // 2. 校验邮箱唯一
        if (existsByEmail(req.getEmail())) {
            throw BusinessException.conflict("该邮箱已注册");
        }

        // 3. 构建用户实体并写库
        User user = new User();
        user.setUsername(req.getUsername());
        user.setNickname(req.getUsername()); // 注册时昵称默认与用户名相同，用户可后续修改
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(UserRole.USER);
        userMapper.insert(user);

        // 4. 颁发 Token，注册后直接登录
        String token = jwtHelper.createToken(user.getId(), user.getUsername(),
                UserRole.USER.name(), false);

        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return buildAuthResp(token, user);
    }

    // ==================== 登录 ====================

    @Override
    public AuthResp login(LoginReq req) {
        // 1. 按账号类型查用户（包含 @ 视为邮箱，否则视为用户名）
        User user = req.getAccount().contains("@")
                ? findByEmail(req.getAccount())
                : findByUsername(req.getAccount());

        if (user == null) {
            throw BusinessException.badRequest("账号或密码错误");
        }

        // 2. 校验密码
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw BusinessException.badRequest("账号或密码错误");
        }

        // 3. 颁发 Token
        String token = jwtHelper.createToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                req.isRememberMe()
        );

        log.info("用户登录成功: userId={}, username={}, rememberMe={}",
                user.getId(), user.getUsername(), req.isRememberMe());
        return buildAuthResp(token, user);
    }

    // ==================== 登出 ====================

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        long remainingMillis = jwtHelper.getRemainingMillis(token);
        if (remainingMillis > 0) {
            redisTemplate.opsForValue().set(
                    KEY_JWT_BLACKLIST + token,
                    "1",
                    remainingMillis,
                    TimeUnit.MILLISECONDS
            );
        }
        log.debug("用户登出，Token 已加入黑名单，剩余有效期: {}ms", remainingMillis);
    }

    // ==================== 找回密码：发送邮件 ====================

    @Override
    public void forgotPassword(ForgotPasswordReq req) {
        String email = req.getEmail().toLowerCase().trim();

        // 1. 频率限制：同一邮箱 15 分钟内只能发一次
        String lockKey = KEY_PWD_RESET_LOCK + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw BusinessException.tooManyRequests("重置邮件已发送，请 " + resetPwdTtlMinutes + " 分钟后再试");
        }

        // 2. 查用户（邮箱不存在时不报错，防止邮箱枚举攻击）
        User user = findByEmail(email);
        if (user == null) {
            log.info("找回密码：邮箱不存在，静默处理: email={}", email);
            return;
        }

        // 3. 生成重置令牌并写入 Redis
        String resetToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                KEY_PWD_RESET + resetToken,
                String.valueOf(user.getId()),
                resetPwdTtlMinutes,
                TimeUnit.MINUTES
        );

        // 4. 设置发送频率锁
        redisTemplate.opsForValue().set(lockKey, "1", resetPwdTtlMinutes, TimeUnit.MINUTES);

        // 5. 发送邮件
        try {
            sendResetEmail(user.getUsername(), email, resetToken);
        } catch (MailException e) {
            redisTemplate.delete(KEY_PWD_RESET + resetToken);
            redisTemplate.delete(lockKey);
            log.error("找回密码邮件发送失败: userId={}, email={}", user.getId(), email, e);
            throw BusinessException.serverError("邮件服务暂时不可用，请稍后重试");
        }

        log.info("找回密码邮件已发送: userId={}, email={}", user.getId(), email);
    }

    // ==================== 找回密码：重置密码 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordReq req) {
        String tokenKey = KEY_PWD_RESET + req.getToken();

        // 1. 从 Redis 取 userId
        String userIdStr = redisTemplate.opsForValue().get(tokenKey);
        if (userIdStr == null) {
            throw BusinessException.badRequest("重置链接已失效或不存在，请重新申请");
        }

        // 2. 查用户
        Long userId = Long.valueOf(userIdStr);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        // 3. 更新密码
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);

        // 4. 令牌一次性使用，立即删除
        redisTemplate.delete(tokenKey);

        log.info("密码重置成功: userId={}", userId);
    }

    // ==================== 私有辅助方法 ====================

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        ) > 0;
    }

    private boolean existsByEmail(String email) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        ) > 0;
    }

    private User findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
    }

    /** 构建认证响应 */
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

    /** 发送密码重置邮件 */
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
        message.setSubject("【内容创作平台】密码重置");
        message.setText(
                "Hi " + username + "，\n\n" +
                        "您申请了密码重置，请点击以下链接完成操作（" + resetPwdTtlMinutes + " 分钟内有效）：\n\n" +
                        resetLink + "\n\n" +
                        "如非本人操作，请忽略此邮件。\n\n" +
                        "— 内容创作平台"
        );
        mailSender.send(message);
    }
}
