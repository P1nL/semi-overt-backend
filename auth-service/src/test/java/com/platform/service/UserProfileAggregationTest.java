package com.platform.service;

import com.platform.client.ContentInternalClient;
import com.platform.common.dto.internal.UserProfileArticleItemDto;
import com.platform.common.dto.internal.UserProfileArticleStatsDto;
import com.platform.common.dto.internal.UserProfileArticlesResp;
import com.platform.dto.resp.UserProfileResp;
import com.platform.entity.User;
import com.platform.enums.ArticleStatus;
import com.platform.exception.BusinessException;
import com.platform.mapper.UserMapper;
import com.platform.service.impl.UserServiceImpl;
import com.platform.util.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 用户资料AggregationTest业务接口，定义对外暴露的服务能力。
 */

@ExtendWith(MockitoExtension.class)
class UserProfileAggregationTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private ContentInternalClient contentInternalClient;

    @Test
    void userProfileIsAssembledFromLocalUserAndRemoteArticleData() {
        UserServiceImpl service = new UserServiceImpl(userMapper, contentInternalClient);

        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setAvatarUrl("/avatar.png");
        user.setCoverUrl("/cover.png");
        user.setSignature("hello");

        when(userMapper.selectOne(any())).thenReturn(user);
        when(contentInternalClient.profilePage(any())).thenReturn(Result.ok(
                UserProfileArticlesResp.builder()
                        .stats(UserProfileArticleStatsDto.builder()
                                .approved(3)
                                .pending(1)
                                .totalWordCount(900)
                                .build())
                        .list(List.of(UserProfileArticleItemDto.builder()
                                .articleId(99L)
                                .title("post")
                                .status(ArticleStatus.PENDING)
                                .authorId(7L)
                                .authorName("Alice")
                                .authorAvatar("/avatar.png")
                                .build()))
                        .total(1)
                        .page(2)
                        .pageSize(5)
                        .build()
        ));

        UserProfileResp resp = service.getUserProfile("alice", 7L, "all", 2, 5);

        assertThat(resp.getProfile().getUsername()).isEqualTo("alice");
        assertThat(resp.getStats().getApproved()).isEqualTo(3);
        assertThat(resp.getStats().getPending()).isEqualTo(1);
        assertThat(resp.getStats().getTotalWordCount()).isEqualTo(900);
        assertThat(resp.getList()).hasSize(1);
        assertThat(resp.getList().get(0).getArticleId()).isEqualTo(99L);
        assertThat(resp.getList().get(0).getStatus()).isEqualTo(ArticleStatus.PENDING);
        assertThat(resp.getPage()).isEqualTo(2);
        assertThat(resp.getPageSize()).isEqualTo(5);
    }

    @Test
    void missingUserStillReturnsNotFound() {
        UserServiceImpl service = new UserServiceImpl(userMapper, contentInternalClient);
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getUserProfile("ghost", null, "all", 1, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }
}
