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
 * 认证服务实现。
 * 负责注册、登录、找回密码邮件发送与密码重置。
 * 同时维护认证域中的 Redis key 约定，主要包括重置密码令牌与发送频率锁。
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

    /** 发件邮箱地址，与 application.yml 中的 spring.mail.username 保持一致。 */
    @Value("${spring.mail.username}")
    private String mailFrom;

    /** 重置密码令牌有效期，单位分钟。 */
    @Value("${platform.reset-pwd-token-ttl-minutes}")
    private long resetPwdTtlMinutes;

    /** 前端基础地址，用于拼接重置密码页面链接。 */
    @Value("${platform.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    /** 重置密码令牌 key 前缀。 */
    private static final String KEY_PWD_RESET      = "pwd:reset:";
    /** 重置密码邮件发送频率锁 key 前缀。 */
    private static final String KEY_PWD_RESET_LOCK = "pwd:reset:lock:";

    /** 系统保留用户名，不允许注册。 */
    private static final Set<String> RESERVED_NAMES = Set.of("me", "admin", "system");


    /**
     * 注册新用户。
     * 顺序为：校验保留词与唯一性 -> 写入用户 -> 直接签发登录 token。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthResp register(RegisterReq req) {
        if (RESERVED_NAMES.contains(req.getUsername().toLowerCase())) {
            throw new BusinessException(400, "该用户名为系统保留词，请换一个");
        }

        if (existsByUsername(req.getUsername())) {
            throw BusinessException.conflict("用户名已被占用");
        }
        if (existsByEmail(req.getEmail())) {
            throw BusinessException.conflict("该邮箱已注册");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setNickname(req.getUsername());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole(UserRole.USER);
        userMapper.insert(user);

        String token = jwtHelper.createToken(user.getId(), user.getUsername(),
                UserRole.USER.name(), false);

        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return buildAuthResp(token, user);
    }


    /**
     * 登录。
     * 支持“用户名或邮箱 + 密码”的统一入口，并按 rememberMe 控制 token 生命周期。
     */
    @Override
    public AuthResp login(LoginReq req) {
        User user = req.getAccount().contains("@")
                ? findByEmail(req.getAccount())
                : findByUsername(req.getAccount());

        if (user == null) {
            throw BusinessException.badRequest("账号或密码错误");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw BusinessException.badRequest("账号或密码错误");
        }

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

    @Override
    /**
     * 发送找回密码邮件。
     * 若邮箱不存在则静默返回，避免通过接口枚举已注册邮箱。
     */
    public void forgotPassword(ForgotPasswordReq req) {
        String email = req.getEmail().toLowerCase().trim();

        String lockKey = KEY_PWD_RESET_LOCK + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw BusinessException.tooManyRequests("重置邮件已发送，请 " + resetPwdTtlMinutes + " 分钟后再试");
        }

        User user = findByEmail(email);
        if (user == null) {
            log.info("找回密码：邮箱不存在，静默处理: email={}", email);
            return;
        }

        String resetToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                KEY_PWD_RESET + resetToken,
                String.valueOf(user.getId()),
                resetPwdTtlMinutes,
                TimeUnit.MINUTES
        );

        redisTemplate.opsForValue().set(lockKey, "1", resetPwdTtlMinutes, TimeUnit.MINUTES);

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


    @Override
    @Transactional(rollbackFor = Exception.class)
    /**
     * 使用重置令牌重设密码。
     * 令牌为一次性使用，修改成功后立即删除。
     */
    public void resetPassword(ResetPasswordReq req) {
        String tokenKey = KEY_PWD_RESET + req.getToken();

        String userIdStr = redisTemplate.opsForValue().get(tokenKey);
        if (userIdStr == null) {
            throw BusinessException.badRequest("重置链接已失效或不存在，请重新申请");
        }

        Long userId = Long.valueOf(userIdStr);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);

        redisTemplate.delete(tokenKey);

        log.info("密码重置成功: userId={}", userId);
    }


    /**
     * 判断用户名是否已存在。
     */
    private boolean existsByUsername(String username) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        ) > 0;
    }

    /**
     * 判断邮箱是否已存在。
     */
    private boolean existsByEmail(String email) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        ) > 0;
    }

    /**
     * 按用户名查询用户。
     */
    private User findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
    }

    /**
     * 按邮箱查询用户。
     */
    private User findByEmail(String email) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
    }

    /**
     * 将用户实体和 token 组装为统一认证响应。
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
     * 发送重置密码邮件。
     * 邮件正文中携带前端重置页面地址和一次性 token。
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
