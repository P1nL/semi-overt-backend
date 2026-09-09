package com.platform.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.platform.kernel.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class TurnstileService {
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private final RestTemplate restTemplate;
    private final String secretKey;

    public TurnstileService(RestTemplate restTemplate,
                            @Value("${platform.turnstile.secret-key:}") String secretKey) {
        this.restTemplate = restTemplate;
        this.secretKey = secretKey == null ? "" : secretKey;
    }

    public void verify(String token) {
        if (secretKey.isBlank() || token == null || token.isBlank()) {
            throw BusinessException.badRequest("Captcha verification failed, please try again");
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", secretKey);
        body.add("response", token);
        try {
            TurnstileResponse response = restTemplate.postForObject(VERIFY_URL, body, TurnstileResponse.class);
            if (response == null || !response.success) {
                throw BusinessException.badRequest("Captcha verification failed, please try again");
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw BusinessException.serverError("Captcha verification service unavailable");
        }
    }

    static class TurnstileResponse {
        @JsonProperty("success")
        private boolean success;
        @JsonProperty("error-codes")
        private String[] errorCodes = new String[0];
    }
}
