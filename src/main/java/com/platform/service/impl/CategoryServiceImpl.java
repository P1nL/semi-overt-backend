package com.platform.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.dto.resp.ArticleCardResp;
import com.platform.dto.resp.CategoryResp;
import com.platform.entity.Article;
import com.platform.entity.User;
import com.platform.enums.DurationCategory;
import com.platform.exception.BusinessException;
import com.platform.mapper.ArticleMapper;
import com.platform.mapper.UserMapper;
import com.platform.service.CategoryService;
import com.platform.util.ArticleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分类服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final int CARD_PREVIEW_MAX_LENGTH = 120;

    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    @Override
    public CategoryResp listByCategory(String category, int page, int pageSize) {
        // ---- 1. 解析分类枚举（大小写不敏感，非法值抛 400） ----
        DurationCategory cat;
        try {
            cat = DurationCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("无效的分类参数：" + category + "，支持：QUICK / SHORT / DEEP");
        }

        // ---- 2. 分页查询（MyBatis Plus 分页插件自动补 COUNT） ----
        Page<Article> pageObj = new Page<>(page, pageSize);
        IPage<Article> result = articleMapper.selectPageByCategory(pageObj, cat);

        List<Article> articles = result.getRecords();

        // ---- 3. 批量查询作者 ----
        Set<Long> authorIds = articles.stream()
                .map(Article::getAuthorId)
                .collect(Collectors.toSet());

        Map<Long, User> userMap = batchFetchUsers(authorIds);

        // ---- 4. 组装响应 ----
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

    // ==================== 私有辅助方法 ====================

    private Map<Long, User> batchFetchUsers(Set<Long> authorIds) {
        if (authorIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(authorIds);
        return users.stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    private ArticleCardResp toCard(Article article, Map<Long, User> userMap) {
        User author = userMap.get(article.getAuthorId());
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
