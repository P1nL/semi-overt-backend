package com.platform.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the mail-budget client identity. A forwarded client address is
 * accepted only from a request carrying the configured internal token.
 */
@Component
public class AuthClientIpResolver {
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String VERIFIED_CLIENT_IP_HEADER = "X-Verified-Client-IP";
    private static final int MAX_VALUE_LENGTH = 128;

    private final String internalToken;

    public AuthClientIpResolver(@Value("${platform.internal.token:}") String internalToken) {
        this.internalToken = internalToken == null ? "" : internalToken;
    }

    public String resolve() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return "unknown";
        }
        return resolve(attributes.getRequest());
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = normalize(request.getRemoteAddr());
        if (!internalToken.isBlank() && request.getHeader(INTERNAL_TOKEN_HEADER)!=null && java.security.MessageDigest.isEqual(internalToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),request.getHeader(INTERNAL_TOKEN_HEADER).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            String verified = normalize(request.getHeader(VERIFIED_CLIENT_IP_HEADER));
            if (verified != null) {
                return verified;
            }
        }
        return remoteAddr == null ? "unknown" : remoteAddr;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() || normalized.length() > MAX_VALUE_LENGTH ? null : normalized;
    }
}
