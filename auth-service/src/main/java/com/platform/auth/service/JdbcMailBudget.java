package com.platform.auth.service;

import com.platform.auth.session.JdbcRequestBudget;
import com.platform.kernel.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Shared, atomic email-send budgets for registration and password reset. */
@Component
public class JdbcMailBudget {
    private static final String REGISTER = "REGISTER";
    private static final String RESET_PASSWORD = "RESET_PASSWORD";

    private final JdbcRequestBudget budget;
    private final int emailHourly;
    private final int emailDaily;
    private final int ipMinute;
    private final int ipHourly;
    private final int globalHourly;
    private final int globalDaily;

    public JdbcMailBudget(
            JdbcRequestBudget budget,
            @Value("${platform.mail.rate-limit.email-hourly:5}") int emailHourly,
            @Value("${platform.mail.rate-limit.email-daily:10}") int emailDaily,
            @Value("${platform.mail.rate-limit.ip-minute:5}") int ipMinute,
            @Value("${platform.mail.rate-limit.ip-hourly:30}") int ipHourly,
            @Value("${platform.mail.rate-limit.global-hourly:500}") int globalHourly,
            @Value("${platform.mail.rate-limit.global-daily:2000}") int globalDaily) {
        this.budget = budget;
        this.emailHourly = emailHourly;
        this.emailDaily = emailDaily;
        this.ipMinute = ipMinute;
        this.ipHourly = ipHourly;
        this.globalHourly = globalHourly;
        this.globalDaily = globalDaily;
        if (emailHourly < 1 || emailDaily < 1 || ipMinute < 1 || ipHourly < 1
                || globalHourly < 1 || globalDaily < 1) {
            throw new IllegalArgumentException("Mail budget limits must be positive");
        }
    }

    public void acquire(String email, String purpose, String clientIp) {
        if (email == null || email.isBlank()
                || !(REGISTER.equals(purpose) || RESET_PASSWORD.equals(purpose))) {
            throw new IllegalArgumentException("Invalid mail budget identity");
        }
        String emailKey = digest(email.trim().toLowerCase(Locale.ROOT));
        String ipKey = digest(clientIp == null || clientIp.isBlank() ? "unknown" : clientIp.trim());
        List<JdbcRequestBudget.Limit> limits = new ArrayList<>(7);
        limits.add(new JdbcRequestBudget.Limit("mail:global:hour", globalHourly, 3600));
        limits.add(new JdbcRequestBudget.Limit("mail:global:day", globalDaily, 86400));
        limits.add(new JdbcRequestBudget.Limit("mail:email:cooldown:" + emailKey, 1, 60));
        limits.add(new JdbcRequestBudget.Limit("mail:email:hour:" + emailKey, emailHourly, 3600));
        limits.add(new JdbcRequestBudget.Limit("mail:email:day:" + emailKey, emailDaily, 86400));
        limits.add(new JdbcRequestBudget.Limit("mail:ip:minute:" + ipKey, ipMinute, 60));
        limits.add(new JdbcRequestBudget.Limit("mail:ip:hour:" + ipKey, ipHourly, 3600));

        JdbcRequestBudget.Decision decision = budget.tryAcquire(limits);
        if (!decision.allowed()) {
            throw BusinessException.tooManyRequests(
                    "Too many verification emails, please try again later",
                    java.util.Map.of("retryAfterSeconds", decision.retryAfterSeconds()));
        }
    }

    static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
