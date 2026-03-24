package com.platform.task;

import com.platform.service.DraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 草稿定时刷盘任务
 * 每 5 分钟将 Redis 中的草稿正文同步写回 MySQL articles.content
 *
 * 设计说明：
 *   - 自动保存接口将正文写入 Redis（高频写，低延迟），元数据同步入库
 *   - 本任务负责将 Redis 正文异步落盘，保证 MySQL 数据不丢失
 *   - Redis TTL 为 7 天，大于刷盘间隔（5 分钟），不会出现"未刷盘就过期"的问题
 *   - 单条失败不影响其他条目，失败详情记录到日志
 *
 * 使用前提：
 *   主启动类需添加 @EnableScheduling 注解
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