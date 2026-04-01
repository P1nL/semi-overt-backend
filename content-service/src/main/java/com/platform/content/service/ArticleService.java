package com.platform.content.service;

import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.kernel.event.ReviewDecidedEvent;
import com.platform.content.api.resp.ArticleDetailResp;
import com.platform.content.api.resp.SubmitResp;

import java.util.Map;


public interface ArticleService {

        Map<String, Object> createArticle(Long userId);

        ArticleDetailResp getArticleDetail(Long articleId, Long currentUserId);

        SubmitResp submitForReview(Long articleId, Long userId);

        Map<String, Object> cancelReview(Long articleId, Long userId);

        void deleteArticle(Long articleId, Long userId);

        Map<String, Object> adminDeleteArticle(Long articleId, Long adminId);

        ArticleReviewSnapshotDto getReviewSnapshot(Long articleId);

        void applyReviewResult(Long articleId, ApplyReviewResultReq req);

    void applyReviewDecisionEvent(ReviewDecidedEvent event);

    UserProfileArticlesResp getUserProfileArticles(UserProfileArticlesQueryReq req);
}



