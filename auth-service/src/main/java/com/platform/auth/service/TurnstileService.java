package com.platform.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.platform.kernel.exception.BusinessException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * 调用 Cloudflare Turnstile 服务端验证接口，校验前端提交的验证 token。
 * <p>
 * 文档：https://developers.cloudflare.com/turnstile/get-started/server-side-validation/
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnstileService {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestTemplate restTemplate;

    @Value("${platform.turnstile.secret-key}")
    private String secretKey;

    /**
     * 验证 Turnstile token，失败时直接抛出 BusinessException。
     *
     * @param token 前端 widget 生成的 cf-turnstile-response token
     */
    public void verify(String token) {
        if (secretKey.equals("TURNSTILE_SECRET_KEY_PLACEHOLDER")) {
            log.warn("Turnstile secret key is placeholder, skipping verification in dev mode");
            return;
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", secretKey);
        body.add("response", token);

        TurnstileResponse response;
        try {
            response = restTemplate.postForObject(VERIFY_URL, body, TurnstileResponse.class);
        } catch (Exception e) {
            log.error("Turnstile verification request failed", e);
            throw BusinessException.serverError("Captcha verification service unavailable");
        }

        if (response == null || !response.isSuccess()) {
            String codes = response != null ? String.join(",", response.getErrorCodes()) : "null-response";
            log.warn("Turnstile verification failed: error_codes={}", codes);
            throw new BusinessException(400, "Captcha verification failed, please try again");
        }
    }

    /**
     * Cloudflare Turnstile 验证响应体。
     */
    @Data
    static class TurnstileResponse {

        private boolean success;

        @JsonProperty("error-codes")
        private String[] errorCodes = new String[0];

        @JsonProperty("challenge_ts")
        private String challengeTs;

        private String hostname;
    }
}
