package com.platform.content.controller.internal;

import com.platform.contract.content.dto.ApplyReviewResultReq;
import com.platform.contract.content.dto.ArticleReviewSnapshotDto;
import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.content.service.ArticleService;
import com.platform.kernel.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/internal/articles")
@RequiredArgsConstructor
public class InternalArticleController {

    private final ArticleService articleService;

    /**
     * 婢跺嫮鎮?GET /{id}/review-snapshot 鐠囬攱鐪伴妴?     */
    @GetMapping("/{id}/review-snapshot")
    public Result<ArticleReviewSnapshotDto> reviewSnapshot(@PathVariable Long id) {
        return Result.ok(articleService.getReviewSnapshot(id));
    }

    /**
     * 婢跺嫮鎮?POST /{id}/apply-review-result 鐠囬攱鐪伴妴?     */
    @PostMapping("/{id}/apply-review-result")
    public Result<Void> applyReviewResult(@PathVariable Long id,
                                          @RequestBody ApplyReviewResultReq req) {
        articleService.applyReviewResult(id, req);
        return Result.ok();
    }

    /**
     * 婢跺嫮鎮?POST /profile-page 鐠囬攱鐪伴妴?     */
    @PostMapping("/profile-page")
    public Result<UserProfileArticlesResp> profilePage(@RequestBody UserProfileArticlesQueryReq req) {
        return Result.ok(articleService.getUserProfileArticles(req));
    }
}



