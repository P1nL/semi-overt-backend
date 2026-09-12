package com.platform.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.contract.review.client.ReviewReasonClient;
import com.platform.contract.review.dto.BatchLatestReviewReasonReq;
import com.platform.contract.review.dto.LatestReviewReasonDto;
import com.platform.content.api.req.SaveDraftReq;
import com.platform.content.api.resp.DraftItemResp;
import com.platform.content.api.resp.SaveDraftResp;
import com.platform.content.entity.Article;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.DraftService;
import com.platform.content.util.ArticleUtils;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.DurationCategory;
import com.platform.kernel.enums.ReviewAction;
import com.platform.kernel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DraftServiceImpl implements DraftService {

    static final int MAX_WORD_COUNT = 15_000;
    private static final int MAX_REASONABLE_CLIENT_WORD_COUNT = 100_000;
    private static final Set<ArticleStatus> EDITABLE_STATUSES =
            Set.of(ArticleStatus.DRAFT, ArticleStatus.RETURNED);
    private static final Set<ArticleStatus> DRAFT_BOX_STATUSES =
            Set.of(ArticleStatus.DRAFT, ArticleStatus.PENDING, ArticleStatus.RETURNED);

    private final ArticleMapper articleMapper;
    private final ReviewReasonClient reviewInternalClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaveDraftResp saveDraft(Long articleId, Long userId, SaveDraftReq req) {
        if (req == null) {
            throw BusinessException.badRequest("Draft patch is required");
        }
        validateClientWordCount(req.getClientWordCount());
        validatePatch(req);

        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("Article not found");
        }
        if (!userId.equals(article.getAuthorId())) {
            throw BusinessException.forbidden("Cannot edit another user's article");
        }
        if (!EDITABLE_STATUSES.contains(article.getStatus())) {
            throw BusinessException.conflict("Current status does not allow saving drafts: " + article.getStatus());
        }
        if (req.getVersion() != null && !req.getVersion().equals(article.getVersion())) {
            throw staleWrite();
        }

        String content = patch(req.getContent(), article.getContent());
        int wordCount = ArticleUtils.countWords(content);
        if (wordCount > MAX_WORD_COUNT) {
            throw BusinessException.badRequest("Content word count cannot exceed " + MAX_WORD_COUNT);
        }
        BigDecimal readMinutes = ArticleUtils.calcReadMinutes(wordCount);
        DurationCategory durationCategory = ArticleUtils.calcDurationCategory(readMinutes);
        if (article.getVersion() == null || article.getVersion() < 0) {
            throw BusinessException.serverError("Article version is unavailable");
        }
        Long expectedVersion = article.getVersion();

        int updated = articleMapper.updateDraftFields(
                articleId,
                userId,
                expectedVersion,
                patch(req.getTitle(), article.getTitle()),
                content,
                patch(req.getSummary(), article.getSummary()),
                patch(req.getCoverUrl(), article.getCoverUrl()),
                patch(req.getCoverColor(), article.getCoverColor()),
                wordCount,
                readMinutes,
                durationCategory
        );
        if (updated != 1) {
            throw staleWrite();
        }

        Article saved = articleMapper.selectById(articleId);
        if (saved == null) {
            throw BusinessException.serverError("Draft was saved but could not be reloaded");
        }
        return SaveDraftResp.builder()
                .savedAt(saved.getUpdatedAt())
                .updatedAt(saved.getUpdatedAt())
                .version(saved.getVersion())
                .wordCount(saved.getWordCount())
                .readMinutes(saved.getReadMinutes())
                .durationCategory(saved.getDurationCategory())
                .status(saved.getStatus())
                .draftVisible(Boolean.TRUE.equals(saved.getDraftVisible()))
                .build();
    }

    @Override
    public List<DraftItemResp> getDraftList(Long userId) {
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getAuthorId, userId)
                        .in(Article::getStatus, DRAFT_BOX_STATUSES)
                        .orderByDesc(Article::getUpdatedAt)
        );
        articles = articles.stream()
                .filter(article -> DRAFT_BOX_STATUSES.contains(article.getStatus()))
                .toList();
        if (articles.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> returnedIds = articles.stream()
                .filter(article -> article.getStatus() == ArticleStatus.RETURNED)
                .map(Article::getId)
                .collect(Collectors.toList());
        Map<Long, String> reasonMap = new HashMap<>();
        if (!returnedIds.isEmpty()) {
            List<LatestReviewReasonDto> reasons = ResultUtils.requireOk(
                    reviewInternalClient.batchLatestReasons(new BatchLatestReviewReasonReq(returnedIds)));
            if (reasons != null) {
                reasons.stream()
                        .filter(reason -> reason.getAction() == ReviewAction.RETURN)
                        .forEach(reason -> reasonMap.put(reason.getArticleId(), reason.getReason()));
            }
        }

        return articles.stream()
                .map(article -> DraftItemResp.builder()
                        .id(article.getId())
                        .title(article.getTitle())
                        .status(article.getStatus())
                        .wordCount(article.getWordCount())
                        .version(article.getVersion())
                        .updatedAt(article.getUpdatedAt())
                        .latestReason(reasonMap.get(article.getId()))
                        .build())
                .toList();
    }

    private String patch(String requested, String current) {
        return requested == null ? current : requested;
    }

    private void validatePatch(SaveDraftReq req) {
        if (req.getVersion() != null && req.getVersion() < 0) {
            throw BusinessException.badRequest("version must be non-negative");
        }
        validateLength(req.getTitle(), 120, "title");
        validateLength(req.getSummary(), 255, "summary");
        validateLength(req.getCoverUrl(), 512, "coverUrl");
        validateLength(req.getCoverColor(), 32, "coverColor");
    }

    private void validateLength(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw BusinessException.badRequest(field + " length cannot exceed " + maximum);
        }
    }

    private void validateClientWordCount(Integer clientWordCount) {
        if (clientWordCount != null
                && (clientWordCount < 0 || clientWordCount > MAX_REASONABLE_CLIENT_WORD_COUNT)) {
            throw BusinessException.badRequest("Client word count is outside the accepted range");
        }
    }

    private BusinessException staleWrite() {
        return BusinessException.conflict("Article was changed by another operation; refresh and retry");
    }
}
