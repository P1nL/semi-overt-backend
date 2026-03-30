package com.platform.service;

import com.platform.dto.req.SaveDraftReq;
import com.platform.dto.resp.DraftItemResp;
import com.platform.dto.resp.SaveDraftResp;

import java.util.List;

/**
 * 草稿业务接口，定义对外暴露的服务能力。
 */

public interface DraftService {

    /**
     * 自动保存草稿
     * - 正文写入 Redis，Key: draft:{userId}:{articleId}，TTL 7 天
     * - 元数据（标题/摘要/封面/字数/阅读时长）同步写入 MySQL
     * 前置检查：
     *   1. 文章存在且属于当前用户
     *   2. 状态必须为 DRAFT 或 RETURNED（PENDING 及以后状态不允许保存）
     *
     * @param articleId 文章 ID
     * @param userId    当前用户 ID
     * @param req       保存内容
     */
    SaveDraftResp saveDraft(Long articleId, Long userId, SaveDraftReq req);

    /**
     * 获取草稿箱列表
     * 仅返回 DRAFT 和 RETURNED 状态的文章，按 updatedAt 倒序
     * RETURNED 文章附带最近一次退回原因
     *
     * @param userId 当前用户 ID
     */
    List<DraftItemResp> getDraftList(Long userId);

    /**
     * 将 Redis 中所有草稿正文刷盘到 MySQL
     * 由 DraftFlushTask 定时调用（每 5 分钟）
     * 逐条处理，单条失败不影响其他条目
     */
    void flushAllDrafts();
}