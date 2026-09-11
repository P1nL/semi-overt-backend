package com.platform.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnstileServiceTest {
    @Test
    void disabledLocalModeSkipsVerification() {
        TurnstileService service = new TurnstileService(new RestTemplate(), "", false);
        assertDoesNotThrow(() -> service.verify(null));
    }

    @Test
    void enabledModeStillRejectsMissingCredentials() {
        TurnstileService service = new TurnstileService(new RestTemplate(), "", true);
        assertThrows(RuntimeException.class, () -> service.verify(null));
    }
}