package com.platform.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.web.support.config.CommonJacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class CommonJacksonConfigTest {
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new CommonJacksonConfig().objectMapper();
    }

    @Test
    void serializesLocalDateTimeWithTheSystemOffset() throws Exception {
        LocalDateTime value = LocalDateTime.of(2026, 9, 9, 10, 15, 30, 123_000_000);
        String expected = value.atZone(ZoneId.systemDefault())
                .toOffsetDateTime()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        assertThat(objectMapper.writeValueAsString(value)).isEqualTo("\"" + expected + "\"");
        assertThat(expected).matches(".*(?:Z|[+-]\\d{2}:\\d{2})$");
    }

    @Test
    void keepsJavaTimeDeserializationEnabled() throws Exception {
        assertThat(objectMapper.readValue("\"2026-09-09T10:15:30\"", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 9, 9, 10, 15, 30));
    }
}
