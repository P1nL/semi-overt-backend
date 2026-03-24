package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.dto.req.ReviewActionReq;
import com.platform.dto.resp.ReviewActionResp;
import com.platform.dto.resp.ReviewListItemResp;
import com.platform.dto.resp.ReviewLogResp;
import com.platform.entity.Article;
import com.platform.entity.ReviewLog;
import com.platform.entity.User;
import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import com.platform.exception.BusinessException;
import com.platform.mapper.ArticleMapper;
import com.platform.mapper.ReviewLogMapper;
import com.platform.mapper.UserMapper;
import com.platform.service.ReviewService;
import com.platform.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 审核服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ArticleMapper articleMapper;
    private final ReviewLogMapper reviewLogMapper;
    private final UserMapper userMapper;

    // ==================== 获取待审核列表 ====================

    @Override
    public Page<ReviewListItemResp> getPendingList(Long currentAdminId, int page, int pageSize) {
        // 查询 PENDING 且不是当前管理员自己提交的文章
        IPage<Article> pageResult = articleMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, ArticleStatus.PENDING)
                        .ne(Article::getAuthorId, currentAdminId)   // 排除自己提交的文章
                        .orderByDesc(Article::getLastSubmittedAt)
        );

        List<Article> articles = pageResult.getRecords();

        // 批量查询作者信息
        Set<Long> authorIds = articles.stream()
                .map(Article::getAuthorId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = batchFetchUsers(authorIds);

        // 组装响应列表
        List<ReviewListItemResp> list = articles.stream()
                .map(a -> {
                    User author = userMap.get(a.getAuthorId());
                    return ReviewListItemResp.builder()
                            .id(a.getId())
                            .title(a.getTitle())
                            .submitCount(a.getSubmitCount())
                            .submittedAt(a.getLastSubmittedAt())
                            .wordCount(a.getWordCount())
                            .author(author != null
                                    ? ReviewListItemResp.AuthorInfo.builder()
                                    .id(author.getId())
                                    .username(author.getUsername())
                                    .build()
                                    : null)
                            .build();
                })
                .collect(Collectors.toList());

        // 手动构造分页结果（MyBatis Plus Page 本身是 IPage 实现，直接复用）
        Page<ReviewListItemResp> result = new Page<>(page, pageSize, pageResult.getTotal());
        result.setRecords(list);
        return result;
    }

    // ==================== 提交审核动作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewActionResp doReview(Long articleId, Long currentAdminId, ReviewActionReq req) {
        // ---- 1. 解析审核动作枚举 ----
        ReviewAction action;
        try {
            action = ReviewAction.valueOf(req.getAction().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw BusinessException.badRequest("无效的审核动作：" + req.getAction()
                    + "，支持：APPROVE / RETURN / REJECT");
        }

        // ---- 2. 仅允许 APPROVE / RETURN / REJECT（CANCEL 由文章作者操作） ----
        if (action == ReviewAction.CANCEL) {
            throw BusinessException.badRequest("CANCEL 操作请调用取消审核接口");
        }

        // ---- 3. RETURN / REJECT 时 reason 必填 ----
        if ((action == ReviewAction.RETURN || action == ReviewAction.REJECT)
                && (req.getReason() == null || req.getReason().isBlank())) {
            throw BusinessException.badRequest("退回或拒绝时必须填写原因");
        }

        // ---- 4. 查询文章（加锁兜底，防止两个管理员并发审核） ----
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("文章不存在");
        }

        // ---- 5. 校验文章仍为 PENDING（并发保护） ----
        if (article.getStatus() != ArticleStatus.PENDING) {
            throw BusinessException.conflict(
                    "文章当前状态为 " + article.getStatus() + "，已不在待审核状态，请刷新后重试");
        }

        // ---- 6. 管理员不能审核自己提交的文章 ----
        if (currentAdminId.equals(article.getAuthorId())) {
            throw BusinessException.forbidden("不能审核自己提交的文章，请由其他管理员操作");
        }

        // ---- 7. 计算目标状态 ----
        ArticleStatus fromStatus = ArticleStatus.PENDING;
        ArticleStatus toStatus = switch (action) {
            case APPROVE -> ArticleStatus.APPROVED;
            case RETURN  -> ArticleStatus.RETURNED;
            case REJECT  -> ArticleStatus.REJECTED;
            default -> throw BusinessException.badRequest("不支持的审核动作");
        };

        // ---- 8. 更新文章状态 ----
        LocalDateTime now = LocalDateTime.now();
        article.setStatus(toStatus);
        if (action == ReviewAction.APPROVE) {
            article.setPublishedAt(now);    // 审核通过时记录发布时间
        }
        articleMapper.updateById(article);

        // ---- 9. 写入审核日志 ----
        ReviewLog reviewLog = new ReviewLog();
        reviewLog.setArticleId(articleId);
        reviewLog.setOperatorId(currentAdminId);
        reviewLog.setAction(action);
        reviewLog.setFromStatus(fromStatus);
        reviewLog.setToStatus(toStatus);
        reviewLog.setReason(req.getReason());
        reviewLogMapper.insert(reviewLog);

        log.info("审核完成: articleId={}, adminId={}, action={}, toStatus={}",
                articleId, currentAdminId, action, toStatus);

        return ReviewActionResp.builder()
                .status(toStatus)
                .reviewedAt(now)
                .build();
    }

    // ==================== 获取审核日志 ====================

    @Override
    public List<ReviewLogResp> getReviewLogs(Long articleId, Long currentUserId) {
        // ---- 1. 查询文章（确认存在） ----
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw BusinessException.notFound("文章不存在");
        }

        // ---- 2. 权限校验：管理员可查所有，普通用户只能查自己的文章 ----
        boolean isAdmin = SecurityUtils.isAdmin();
        boolean isAuthor = currentUserId != null && currentUserId.equals(article.getAuthorId());
        if (!isAdmin && !isAuthor) {
            throw BusinessException.forbidden("无权查看该文章的审核记录");
        }

        // ---- 3. 查询日志列表（按时间升序，方便前端展示审核历史时间轴） ----
        List<ReviewLog> logs = reviewLogMapper.selectList(
                new LambdaQueryWrapper<ReviewLog>()
                        .eq(ReviewLog::getArticleId, articleId)
                        .orderByAsc(ReviewLog::getCreatedAt)
        );

        if (logs.isEmpty()) {
            return Collections.emptyList();
        }

        // ---- 4. 批量查询操作人信息 ----
        Set<Long> operatorIds = logs.stream()
                .map(ReviewLog::getOperatorId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = batchFetchUsers(operatorIds);

        // ---- 5. 组装响应 ----
        return logs.stream()
                .map(l -> {
                    User operator = userMap.get(l.getOperatorId());
                    return ReviewLogResp.builder()
                            .action(l.getAction())
                            .fromStatus(l.getFromStatus())
                            .toStatus(l.getToStatus())
                            .reason(l.getReason())
                            .operator(operator != null
                                    ? ReviewLogResp.OperatorInfo.builder()
                                    .id(operator.getId())
                                    .username(operator.getUsername())
                                    .build()
                                    : null)
                            .createdAt(l.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    // ==================== 私有辅助方法 ====================

    private Map<Long, User> batchFetchUsers(Set<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }
}