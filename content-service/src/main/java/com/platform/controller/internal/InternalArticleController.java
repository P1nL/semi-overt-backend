package com.platform.controller.internal;

import com.platform.common.dto.internal.ApplyReviewResultReq;
import com.platform.common.dto.internal.ArticleReviewSnapshotDto;
import com.platform.common.dto.internal.UserProfileArticlesQueryReq;
import com.platform.common.dto.internal.UserProfileArticlesResp;
import com.platform.service.ArticleService;
import com.platform.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/articles")
@RequiredArgsConstructor
public class InternalArticleController {

    private final ArticleService articleService;

    @GetMapping("/{id}/review-snapshot")
    public Result<ArticleReviewSnapshotDto> reviewSnapshot(@PathVariable Long id) {
        return Result.ok(articleService.getReviewSnapshot(id));
    }

    @PostMapping("/{id}/apply-review-result")
    public Result<Void> applyReviewResult(@PathVariable Long id,
                                          @RequestBody ApplyReviewResultReq req) {
        articleService.applyReviewResult(id, req);
        return Result.ok();
    }

    @PostMapping("/profile-page")
    public Result<UserProfileArticlesResp> profilePage(@RequestBody UserProfileArticlesQueryReq req) {
        return Result.ok(articleService.getUserProfileArticles(req));
    }
}
