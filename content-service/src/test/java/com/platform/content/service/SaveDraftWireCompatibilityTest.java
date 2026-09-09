package com.platform.content.service;
import com.platform.content.api.resp.SaveDraftResp;
import com.platform.web.support.config.CommonJacksonConfig;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
class SaveDraftWireCompatibilityTest {
    @Test void suppliesServerUpdatedAtWithoutBrowserFallback() throws Exception {
        var value=SaveDraftResp.builder().savedAt(LocalDateTime.of(2026,9,9,12,0)).build();
        var mapper=new CommonJacksonConfig().objectMapper();var json=mapper.readTree(mapper.writeValueAsString(value));
        assertEquals(json.get("savedAt"),json.get("updatedAt"));
        assertFalse(json.get("draftVisible").asBoolean());
    }
}
