package com.platform.content.task;

import com.platform.content.service.DraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class DraftFlushTask {

    private final DraftService draftService;

        @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void flushDrafts() {
        log.debug("閼藉顭堥崚椋庢磸娴犺濮熷鈧慨?..");
        try {
            draftService.flushAllDrafts();
        } catch (Exception e) {
            // Guard the scheduler from unexpected flush failures.
            log.error("閼藉顭堥崚椋庢磸娴犺濮熷鍌氱埗", e);
        }
    }
}


