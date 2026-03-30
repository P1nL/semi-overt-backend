package com.platform.task;

import com.platform.service.DraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 草稿Flush定时任务，负责周期性触发对应业务处理。
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftFlushTask {

    private final DraftService draftService;

    /**
     * 每 5 分钟执行一次刷盘
     * fixedDelay：上次执行结束后等待 5 分钟再执行（避免任务堆叠）
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void flushDrafts() {
        log.debug("草稿刷盘任务开始...");
        try {
            draftService.flushAllDrafts();
        } catch (Exception e) {
            // 顶层捕获，防止异常导致定时任务停止调度
            log.error("草稿刷盘任务异常", e);
        }
    }
}