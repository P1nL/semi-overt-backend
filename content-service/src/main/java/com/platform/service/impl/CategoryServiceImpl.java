package com.platform.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.client.AuthInternalClient;
import com.platform.common.api.ResultUtils;
import com.platform.common.dto.internal.BatchUserQueryReq;
import com.platform.common.dto.internal.UserSummaryDto;
import com.platform.dto.resp.ArticleCardResp;
import com.platform.dto.resp.CategoryResp;
import com.platform.entity.Article;
import com.platform.enums.DurationCategory;
import com.platform.exception.BusinessException;
import com.platform.mapper.ArticleMapper;
import com.platform.service.CategoryService;
import com.platform.util.ArticleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 分类页服务实现。
 * 负责按阅读时长分类分页查询公开文章，并补齐作者摘要信息后组装成卡片列表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final int CARD_PREVIEW_MAX_LENGTH = 120;

    private final ArticleMapper articleMapper;
    private final AuthInternalClient authInternalClient;

    /**
     * 按分类分页查询文章。
     * 分类参数会先转换为 `DurationCategory`，非法值直接返回 400。
     */
    @Override
    public CategoryResp listByCategory(String category, int page, int pageSize) {
        DurationCategory cat;
        try {
            cat = DurationCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("鏃犳晥鐨勫垎绫诲弬鏁帮細" + category + "锛屾敮鎸侊細QUICK / SHORT / DEEP");
        }

        Page<Article> pageObj = new Page<>(page, pageSize);
        IPage<Article> result = articleMapper.selectPageByCategory(pageObj, cat);

        List<Article> articles = result.getRecords();

        // 作者信息通过内部接口批量补齐，避免列表页出现 N+1 远程调用。
        Set<Long> authorIds = articles.stream()
                .map(Article::getAuthorId)
                .collect(Collectors.toSet());

        Map<Long, UserSummaryDto> userMap = batchFetchUsers(authorIds);

        List<ArticleCardResp> list = articles.stream()
                .map(a -> toCard(a, userMap))
                .collect(Collectors.toList());

        return CategoryResp.builder()
                .category(cat)
                .list(list)
                .total(result.getTotal())
                .page(page)
                .pageSize(pageSize)
                .pages(result.getPages())
                .build();
    }

    /**
     * 批量查询作者摘要信息。
     */
    private Map<Long, UserSummaryDto> batchFetchUsers(Set<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        BatchUserQueryReq req = new BatchUserQueryReq();
        req.setUserIds(authorIds.stream().toList());
        List<UserSummaryDto> users = ResultUtils.requireOk(authInternalClient.batchUsers(req));
        return users.stream().collect(Collectors.toMap(UserSummaryDto::getId, u -> u));
    }

    /**
     * 文章实体到分类页卡片响应的字段映射。
     */
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
