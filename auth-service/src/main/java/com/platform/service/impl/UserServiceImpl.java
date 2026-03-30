package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.platform.client.ContentInternalClient;
import com.platform.common.api.ResultUtils;
import com.platform.common.dto.internal.UserProfileArticleItemDto;
import com.platform.common.dto.internal.UserProfileArticlesQueryReq;
import com.platform.common.dto.internal.UserProfileArticlesResp;
import com.platform.dto.req.UpdateProfileReq;
import com.platform.dto.resp.ArticleCardResp;
import com.platform.dto.resp.UserInfoResp;
import com.platform.dto.resp.UserProfileResp;
import com.platform.entity.User;
import com.platform.exception.BusinessException;
import com.platform.mapper.UserMapper;
import com.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 用户资料服务实现。
 * 负责当前登录用户资料维护，以及用户主页信息的聚合查询。
 * 用户基础资料以 auth-service 的 MySQL 为准，文章统计与列表通过 content-service 内部接口补齐。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final ContentInternalClient contentInternalClient;

    /**
     * 查询当前登录用户的基础资料。
     * 这里只返回 auth-service 持有的用户主数据，不包含文章统计。
     */
    @Override
    public UserInfoResp getCurrentUserInfo(Long userId) {
        User user = getUserById(userId);
        return toUserInfoResp(user);
    }

    /**
     * 更新当前用户可编辑的个人资料字段。
     * 仅覆盖请求中显式传入的非 null 字段，未传字段保持原值。
     */
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

    /**
     * 查询用户主页。
     * 先根据用户名定位用户，再调用 content-service 聚合该用户的文章统计与分页列表。
     * tab 和分页参数原样透传给内容服务，由内容服务统一做可见性与状态过滤。
     */
    @Override
    public UserProfileResp getUserProfile(String username, Long currentUserId, String tab, int page, int pageSize) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(404, "鐢ㄦ埛涓嶅瓨鍦?");
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

    /**
     * 根据总数和页大小计算总页数。
     * 当内容服务无返回时，退回到调用方传入的分页参数，保证响应结构稳定。
     */
    private long resolvePages(UserProfileArticlesResp articlesResp, int fallbackPageSize) {
        long total = articlesResp != null ? articlesResp.getTotal() : 0;
        int pageSize = articlesResp != null ? articlesResp.getPageSize() : fallbackPageSize;
        int safePageSize = Math.max(1, pageSize);
        return total == 0 ? 0 : (total + safePageSize - 1) / safePageSize;
    }

    /**
     * 将内容服务返回的统计 DTO 映射为前台使用的统计结构。
     * 当下游未返回统计时，统一回填 0，避免前端判空。
     */
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

    /**
     * 将用户主页文章列表转换为卡片响应。
     */
    private List<ArticleCardResp> toArticleCards(UserProfileArticlesResp articlesResp) {
        if (articlesResp == null || articlesResp.getList() == null) {
            return Collections.emptyList();
        }

        return articlesResp.getList().stream()
                .map(this::toArticleCardResp)
                .toList();
    }

    /**
     * 单篇文章列表项到卡片结构的字段映射。
     */
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

    /**
     * 按主键查询用户，不存在时直接抛 404。
     */
    private User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "鐢ㄦ埛涓嶅瓨鍦?");
        }
        return user;
    }

    /**
     * 用户实体到当前用户信息响应的字段映射。
     */
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
