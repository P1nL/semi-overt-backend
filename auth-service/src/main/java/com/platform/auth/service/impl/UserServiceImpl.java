package com.platform.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.auth.api.req.UpdateProfileReq;
import com.platform.auth.api.resp.ArticleCardResp;
import com.platform.auth.api.resp.UserInfoResp;
import com.platform.auth.api.resp.UserProfileResp;
import com.platform.auth.entity.User;
import com.platform.auth.mapper.UserMapper;
import com.platform.auth.service.UserService;
import com.platform.contract.content.dto.UserProfileArticleItemDto;
import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.contract.content.client.ContentProfileClient;
import com.platform.kernel.api.ResultUtils;
import com.platform.kernel.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ContentProfileClient contentInternalClient;

    @Override
    public UserInfoResp getCurrentUserInfo(Long userId) {
        User user = getUserById(userId);
        return toUserInfoResp(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoResp updateProfile(Long userId, UpdateProfileReq req) {
        User user = getUserById(userId);

        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
        }
        if (req.getAvatarUrl() != null) {
            user.setAvatarUrl(req.getAvatarUrl());
        }
        if (req.getCoverUrl() != null) {
            user.setCoverUrl(req.getCoverUrl());
        }
        if (req.getSignature() != null) {
            user.setSignature(req.getSignature());
        }

        userMapper.updateById(user);
        return toUserInfoResp(user);
    }

    @Override
    public UserProfileResp getUserProfile(String username, Long currentUserId, String tab, int page, int pageSize) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw BusinessException.notFound("User not found");
        }

        UserProfileArticlesResp articlesResp = ResultUtils.requireOk(contentInternalClient.profilePage(
                UserProfileArticlesQueryReq.builder()
                        .authorId(user.getId())
                        .tab(tab)
                        .page(page)
                        .pageSize(pageSize)
                        .build()
        ));

        return UserProfileResp.builder()
                .profile(UserProfileResp.ProfileInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .role(user.getRole())
                        .avatarUrl(user.getAvatarUrl())
                        .coverUrl(user.getCoverUrl())
                        .signature(user.getSignature())
                        .build())
                .stats(toArticleStats(articlesResp))
                .list(toArticleCards(articlesResp))
                .total(articlesResp != null ? articlesResp.getTotal() : 0)
                .page(articlesResp != null ? articlesResp.getPage() : page)
                .pageSize(articlesResp != null ? articlesResp.getPageSize() : pageSize)
                .pages(resolvePages(articlesResp, pageSize))
                .build();
    }

    private long resolvePages(UserProfileArticlesResp articlesResp, int fallbackPageSize) {
        long total = articlesResp != null ? articlesResp.getTotal() : 0;
        int pageSize = articlesResp != null ? articlesResp.getPageSize() : fallbackPageSize;
        int safePageSize = Math.max(1, pageSize);
        return total == 0 ? 0 : (total + safePageSize - 1) / safePageSize;
    }

    private UserProfileResp.ArticleStats toArticleStats(UserProfileArticlesResp articlesResp) {
        if (articlesResp == null || articlesResp.getStats() == null) {
            return UserProfileResp.ArticleStats.builder()
                    .approved(0)
                    .pending(0)
                    .returned(0)
                    .rejected(0)
                    .draft(0)
                    .totalWordCount(0)
                    .build();
        }

        return UserProfileResp.ArticleStats.builder()
                .approved(articlesResp.getStats().getApproved())
                .pending(articlesResp.getStats().getPending())
                .returned(articlesResp.getStats().getReturned())
                .rejected(articlesResp.getStats().getRejected())
                .draft(articlesResp.getStats().getDraft())
                .totalWordCount(articlesResp.getStats().getTotalWordCount())
                .build();
    }

    private List<ArticleCardResp> toArticleCards(UserProfileArticlesResp articlesResp) {
        if (articlesResp == null || articlesResp.getList() == null) {
            return Collections.emptyList();
        }

        return articlesResp.getList().stream()
                .map(this::toArticleCardResp)
                .toList();
    }

    private ArticleCardResp toArticleCardResp(UserProfileArticleItemDto article) {
        return ArticleCardResp.builder()
                .articleId(article.getArticleId())
                .title(article.getTitle())
                .summary(article.getSummary())
                .previewText(article.getPreviewText())
                .coverUrl(article.getCoverUrl())
                .coverColor(article.getCoverColor())
                .readMinutes(article.getReadMinutes())
                .durationCategory(article.getDurationCategory())
                .status(article.getStatus())
                .authorId(article.getAuthorId())
                .authorName(article.getAuthorName())
                .authorAvatar(article.getAuthorAvatar())
                .publishedAt(article.getPublishedAt())
                .updatedAt(article.getUpdatedAt())
                .rejectReason(article.getRejectReason())
                .build();
    }

    private User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("User not found");
        }
        return user;
    }

    private UserInfoResp toUserInfoResp(User user) {
        return UserInfoResp.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .role(user.getRole())
                .avatarUrl(user.getAvatarUrl())
                .coverUrl(user.getCoverUrl())
                .signature(user.getSignature())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
