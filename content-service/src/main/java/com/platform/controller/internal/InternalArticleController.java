package com.platform.controller.internal;

import com.platform.common.dto.internal.ApplyReviewResultReq;
import com.platform.common.dto.internal.ArticleReviewSnapshotDto;
import com.platform.common.dto.internal.UserProfileArticlesQueryReq;
import com.platform.common.dto.internal.UserProfileArticlesResp;
import com.platform.service.ArticleService;
import com.platform.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 内部文章内部控制器，向其他服务暴露服务间调用接口。
 */

@RestController
@RequestMapping("/internal/articles")
@RequiredArgsConstructor
public class InternalArticleController {

    private final ArticleService articleService;

    /**
     * 处理 GET /{id}/review-snapshot 请求。
     */
    @GetMapping("/{id}/review-snapshot")
    public Result<ArticleReviewSnapshotDto> reviewSnapshot(@PathVariable Long id) {
        return Result.ok(articleService.getReviewSnapshot(id));
    }

    /**
     * 处理 POST /{id}/apply-review-result 请求。
     */
    @PostMapping("/{id}/apply-review-result")
    public Result<Void> applyReviewResult(@PathVariable Long id,
                                          @RequestBody ApplyReviewResultReq req) {
        articleService.applyReviewResult(id, req);
        return Result.ok();
    }

    /**
     * 处理 POST /profile-page 请求。
     */
    @PostMapping("/profile-page")
    public Result<UserProfileArticlesResp> profilePage(@RequestBody UserProfileArticlesQueryReq req) {
        return Result.ok(articleService.getUserProfileArticles(req));
    }
}
