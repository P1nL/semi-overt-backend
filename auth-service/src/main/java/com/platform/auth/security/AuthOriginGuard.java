package com.platform.auth.security;

import com.platform.kernel.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;

/** Origin and fetch-site guard for cookie-authenticated state-changing endpoints. */
@Component
public class AuthOriginGuard {
    private final CorsConfiguration corsConfiguration;

    public AuthOriginGuard(
            @Value("${platform.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,https://semi-overt.com,https://www.semi-overt.com}")
            String allowedOrigins) {
        if(allowedOrigins.contains("*"))throw new IllegalArgumentException("Auth origins must be explicit");
        corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList());
    }

    public void validate(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return;
        }
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(fetchSite == null ? null : fetchSite.trim())) {
            throw BusinessException.forbidden("Access denied");
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && corsConfiguration.checkOrigin(origin.trim()) == null) {
            throw BusinessException.forbidden("Access denied");
        }
    }
}
