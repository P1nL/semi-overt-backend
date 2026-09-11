package com.platform.web.support.config;

import com.platform.kernel.event.ArticleSubmittedEvent;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;

class CommonJacksonConfigTest {
    @Test void domainEventRoundTripsItsOwnOffsetTimestamp() throws Exception {
        var mapper=new CommonJacksonConfig().objectMapper();
        var at=LocalDateTime.of(2026,9,10,17,27,4,123456000);
        var event=ArticleSubmittedEvent.builder().eventId("round-trip").submittedAt(at).build();
        String json=mapper.writeValueAsString(event);
        assertEquals(at,mapper.readValue(json,ArticleSubmittedEvent.class).getSubmittedAt());
    }
    @Test void acceptsLegacyLocalAndConvertsForeignOffsetWithoutLosingInstant() throws Exception {
        var mapper=new CommonJacksonConfig().objectMapper();
        assertEquals(LocalDateTime.of(2026,9,10,17,0),mapper.readValue("\"2026-09-10T17:00:00\"",LocalDateTime.class));
        assertEquals(OffsetDateTime.parse("2026-09-10T09:00:00Z").atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
                mapper.readValue("\"2026-09-10T09:00:00Z\"",LocalDateTime.class));
        assertThrows(Exception.class,()->mapper.readValue("\"not-a-date\"",LocalDateTime.class));
    }
}
