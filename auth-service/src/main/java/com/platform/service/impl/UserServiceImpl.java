package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.dto.req.UpdateProfileReq;
import com.platform.dto.resp.ArticleCardResp;
import com.platform.dto.resp.UserInfoResp;
import com.platform.dto.resp.UserProfileResp;
import com.platform.entity.Article;
import com.platform.entity.ReviewLog;
import com.platform.entity.User;
import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import com.platform.exception.BusinessException;
import com.platform.mapper.ArticleMapper;
import com.platform.mapper.ReviewLogMapper;
import com.platform.mapper.UserMapper;
import com.platform.service.UserService;
import com.platform.util.ArticleUtils;
import com.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int CARD_PREVIEW_MAX_LENGTH = 120;

    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final ReviewLogMapper reviewLogMapper;

    // ----------------------------------------------------------------
    //  获取当前用户信息
    // ----------------------------------------------------------------

    @Override
    public UserInfoResp getCurrentUserInfo(Long userId) {
        User user = getUserById(userId);
        return toUserInfoResp(user);
    }

    // ----------------------------------------------------------------
    //  修改个人资料
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    //  个人主页（公开）
    // ----------------------------------------------------------------

    @Override
    public UserProfileResp getUserProfile(String username, Long currentUserId,
                                          String tab, int page, int pageSize) {
        // 1. 按 username 查目标用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 2. 判断是否本人访问
        boolean isSelf = currentUserId != null && currentUserId.equals(user.getId());
        boolean isAdmin = SecurityUtils.isAdmin();
        boolean canViewAll = isSelf || isAdmin;

        // 3. 统计各状态文章数（含 totalWordCount）
        UserProfileResp.ArticleStats stats = buildStats(user.getId(), canViewAll);

        // 4. 确定文章状态过滤条件
        ArticleStatus filterStatus = resolveTabStatus(tab, canViewAll);

        // 5. 分页查询文章列表
        LambdaQueryWrapper<Article> query = new LambdaQueryWrapper<Article>()
                .eq(Article::getAuthorId, user.getId())
                .orderByDesc(Article::getUpdatedAt);

        if (filterStatus != null) {
            query.eq(Article::getStatus, filterStatus);
        } else if (!canViewAll) {
            query.eq(Article::getStatus, ArticleStatus.APPROVED);
        }

        IPage<Article> pageResult = articleMapper.selectPage(new Page<>(page, pageSize), query);
        List<Article> articles = pageResult.getRecords();

        // 6. 若当前 tab 为 REJECTED，批量查最新拒绝原因（减少 N+1）
        Map<Long, String> rejectReasonMap = buildRejectReasonMap(filterStatus, articles);

        // 7. 组装文章卡片
        List<ArticleCardResp> articleCards = articles.stream()
                .map(a -> ArticleCardResp.builder()
                        .articleId(a.getId())
                        .title(a.getTitle())
                        .summary(a.getSummary())
                        .previewText(ArticleUtils.extractPreviewText(a.getContent(), CARD_PREVIEW_MAX_LENGTH))
                        .coverUrl(a.getCoverUrl())
                        .coverColor(a.getCoverColor())
                        .readMinutes(a.getReadMinutes())
                        .durationCategory(a.getDurationCategory())
                        .status(a.getStatus())
                        .authorId(user.getId())
                        .authorName(user.getNickname())      // 展示昵称
                        .authorAvatar(user.getAvatarUrl())
                        .publishedAt(a.getPublishedAt())
                        .updatedAt(a.getUpdatedAt())
                        .rejectReason(rejectReasonMap.get(a.getId()))  // 非 REJECTED tab 为 null
                        .build())
                .collect(Collectors.toList());

        return UserProfileResp.builder()
                .profile(UserProfileResp.ProfileInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .nickname(user.getNickname())
                        .avatarUrl(user.getAvatarUrl())
                        .coverUrl(user.getCoverUrl())
                        .signature(user.getSignature())
                        .build())
                .stats(stats)
                .list(articleCards)
                .total(pageResult.getTotal())
                .page(page)
                .pageSize(pageSize)
                .build();
    }

    // ----------------------------------------------------------------
    //  私有工具方法
    // ----------------------------------------------------------------

    /**
     * 统计各状态文章数，并计算 APPROVED 文章的总字数
     * canViewAll=false 时仅统计 APPROVED
     */
    private UserProfileResp.ArticleStats buildStats(Long userId, boolean canViewAll) {
        long approved = countByStatus(userId, ArticleStatus.APPROVED);

        // 总字数：对该用户所有 APPROVED 文章的 word_count 求和
        int totalWordCount = articleMapper.selectList(
                        new LambdaQueryWrapper<Article>()
                                .eq(Article::getAuthorId, userId)
                                .eq(Article::getStatus, ArticleStatus.APPROVED)
                                .select(Article::getWordCount)
                ).stream()
                .mapToInt(a -> a.getWordCount() != null ? a.getWordCount() : 0)
                .sum();

        if (!canViewAll) {
            return UserProfileResp.ArticleStats.builder()
                    .approved(approved)
                    .totalWordCount(totalWordCount)
                    .build();
        }
        return UserProfileResp.ArticleStats.builder()
                .approved(approved)
                .pending(countByStatus(userId, ArticleStatus.PENDING))
                .returned(countByStatus(userId, ArticleStatus.RETURNED))
                .rejected(countByStatus(userId, ArticleStatus.REJECTED))
                .draft(countByStatus(userId, ArticleStatus.DRAFT))
                .totalWordCount(totalWordCount)
                .build();
    }

    private long countByStatus(Long userId, ArticleStatus status) {
        return articleMapper.selectCount(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getAuthorId, userId)
                        .eq(Article::getStatus, status)
        );
    }

    /**
     * 批量查询拒绝原因（articleId → 最新一次 REJECT 原因）
     * 仅在 filterStatus == REJECTED 时执行，其他场景直接返回空 Map
     */
    private Map<Long, String> buildRejectReasonMap(ArticleStatus filterStatus, List<Article> articles) {
        if (filterStatus != ArticleStatus.REJECTED || articles.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> articleIds = articles.stream()
                .map(Article::getId)
                .collect(Collectors.toList());

        // 查出所有相关的 REJECT 日志，按 created_at 倒序
        List<ReviewLog> logs = reviewLogMapper.selectList(
                new LambdaQueryWrapper<ReviewLog>()
                        .in(ReviewLog::getArticleId, articleIds)
                        .eq(ReviewLog::getAction, ReviewAction.REJECT)
                        .orderByDesc(ReviewLog::getCreatedAt)
        );

        // 每篇文章只保留最新一条（利用 Map 后写覆盖，但日志已按时间倒序，
        // 用 toMap 的 mergeFunction 保留先出现的那条即最新的）
        return logs.stream()
                .collect(Collectors.toMap(
                        ReviewLog::getArticleId,
                        log -> log.getReason() != null ? log.getReason() : "",
                        (existing, replacement) -> existing  // 保留最新（倒序中先出现）
                ));
    }

    /**
     * tab 字符串 → ArticleStatus
     */
    private ArticleStatus resolveTabStatus(String tab, boolean canViewAll) {
        if (!canViewAll) {
            return ArticleStatus.APPROVED;
        }
        if (tab == null || tab.equalsIgnoreCase("all")) {
            return null;
        }
        return switch (tab.toLowerCase()) {
            case "approved" -> ArticleStatus.APPROVED;
            case "pending"  -> ArticleStatus.PENDING;
            case "returned" -> ArticleStatus.RETURNED;
            case "rejected" -> ArticleStatus.REJECTED;
            case "draft"    -> ArticleStatus.DRAFT;
            default         -> null;
        };
    }

    private User getUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
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
