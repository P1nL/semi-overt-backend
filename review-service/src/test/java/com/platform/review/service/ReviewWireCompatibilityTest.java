package com.platform.review.service;
import com.platform.review.api.resp.ReviewActionResp;
import com.platform.web.support.config.CommonJacksonConfig;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class ReviewWireCompatibilityTest {
    @Test void preservesLegacyAndFrontendTimestampNames() throws Exception {
        var value=ReviewActionResp.builder().reviewedAt(LocalDateTime.of(2026,9,9,12,0)).build();
        var mapper=new CommonJacksonConfig().objectMapper();var json=mapper.readTree(mapper.writeValueAsString(value));
        assertEquals(json.get("reviewedAt"),json.get("updatedAt"));
    }
}
