package com.platform.content.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.contract.auth.client.AuthUserQueryClient;
import com.platform.contract.auth.dto.BatchUserQueryReq;
import com.platform.contract.auth.dto.UserSummaryDto;
import com.platform.content.api.resp.ArticleCardResp;
import com.platform.content.api.resp.CategoryResp;
import com.platform.content.entity.Article;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.enums.DurationCategory;
import com.platform.kernel.exception.BusinessException;
import com.platform.content.mapper.ArticleMapper;
import com.platform.content.service.CategoryService;
import com.platform.content.util.ArticleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final int CARD_PREVIEW_MAX_LENGTH = 120;

    private final ArticleMapper articleMapper;
    private final AuthUserQueryClient authInternalClient;

    @Override
    public CategoryResp listByCategory(String category, int page, int pageSize) {
        DurationCategory cat;
        try {
            cat = DurationCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest(
                    "Invalid category: " + category + ", supported values: QUICK / SHORT / DEEP");
        }

        Page<Article> pageObj = new Page<>(page, pageSize);
        IPage<Article> result = articleMapper.selectPageByCategory(pageObj, cat);
        List<Article> articles = result.getRecords();

        Set<Long> authorIds = articles.stream()
                .map(Article::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, UserSummaryDto> userMap = batchFetchUsers(authorIds);

        List<ArticleCardResp> list = articles.stream()
                .map(a -> toCard(a, userMap))
                .toList();

        return CategoryResp.builder()
                .category(cat)
                .list(list)
                .total(result.getTotal())
                .page(page)
                .pageSize(pageSize)
                .pages(result.getPages())
                .build();
    }

    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        BatchUserQueryReq req = new BatchUserQueryReq();
        req.setUserIds(authorIds.stream().toList());
        List<UserSummaryDto> users = ResultUtils.requireOk(authInternalClient.batchUsers(req));
        return users.stream().collect(Collectors.toMap(UserSummaryDto::getId, u -> u));
    }

    private ArticleCardResp toCard(Article article, Map<Long, UserSummaryDto> userMap) {
        UserSummaryDto author = userMap.get(article.getAuthorId());
        return ArticleCardResp.builder()
                .articleId(article.getId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .previewText(ArticleUtils.extractPreviewText(article.getContent(), CARD_PREVIEW_MAX_LENGTH))
                .coverUrl(article.getCoverUrl())
                .coverColor(article.getCoverColor())
                .readMinutes(article.getReadMinutes())
                .durationCategory(article.getDurationCategory())
                .status(article.getStatus())
                .authorId(author != null ? author.getId() : null)
                .authorName(author != null ? author.getNickname() : null)
                .authorAvatar(author != null ? author.getAvatarUrl() : null)
                .publishedAt(article.getPublishedAt())
                .updatedAt(article.getUpdatedAt())
                .build();
    }
}
