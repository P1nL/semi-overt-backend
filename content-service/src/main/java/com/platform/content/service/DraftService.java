package com.platform.content.service;

import com.platform.content.api.req.SaveDraftReq;
import com.platform.content.api.resp.DraftItemResp;
import com.platform.content.api.resp.SaveDraftResp;

import java.util.List;

public interface DraftService {

    // Historical Redis draft:* entries are intentionally not read, flushed, or deleted.
    // Any recovery requires a separately approved, baseline-bound read-only export strategy.
    SaveDraftResp saveDraft(Long articleId, Long userId, SaveDraftReq req);
    List<DraftItemResp> getDraftList(Long userId);
}
