package com.platform.auth.service;

import com.platform.auth.api.req.UpdateProfileReq;
import com.platform.auth.api.resp.UserInfoResp;
import com.platform.auth.api.resp.UserProfileResp;
import com.platform.auth.entity.User;
import com.platform.auth.mapper.UserMapper;
import com.platform.auth.service.impl.UserServiceImpl;
import com.platform.contract.content.client.ContentProfileClient;
import com.platform.contract.content.dto.UserProfileArticleItemDto;
import com.platform.contract.content.dto.UserProfileArticleStatsDto;
import com.platform.contract.content.dto.UserProfileArticlesQueryReq;
import com.platform.contract.content.dto.UserProfileArticlesResp;
import com.platform.kernel.enums.ArticleStatus;
import com.platform.kernel.enums.UserRole;
import com.platform.kernel.exception.BusinessException;
import com.platform.kernel.util.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileAggregationTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private ContentProfileClient contentInternalClient;

    @Test
    void userProfileIsAssembledFromLocalUserAndRemoteArticleData() {
        UserServiceImpl service = new UserServiceImpl(userMapper, contentInternalClient);

        User user = user(7L, "alice");
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

        assertThat(resp.getProfile().getId()).isEqualTo(7L);
        assertThat(resp.getProfile().getUsername()).isEqualTo("alice");
        assertThat(resp.getProfile().getAvatarUrl()).isEqualTo("/avatar.png");
        assertThat(resp.getStats().getApproved()).isEqualTo(3);
        assertThat(resp.getStats().getPending()).isEqualTo(1);
        assertThat(resp.getStats().getTotalWordCount()).isEqualTo(900);
        assertThat(resp.getList()).hasSize(1);
        assertThat(resp.getList().get(0).getArticleId()).isEqualTo(99L);
        assertThat(resp.getList().get(0).getAuthor().getId()).isEqualTo(7L);
        assertThat(resp.getList().get(0).getStatus()).isEqualTo(ArticleStatus.PENDING);
        assertThat(resp.getPage()).isEqualTo(2);
        assertThat(resp.getPageSize()).isEqualTo(5);
    }

    @Test
    void numericIdentifierUsesIdAndHugeNumericIdentifierIsNotFound() {
        UserServiceImpl service = new UserServiceImpl(userMapper, contentInternalClient);
        User user = user(7L, "alice");
        when(userMapper.selectById(7L)).thenReturn(user);
        when(contentInternalClient.profilePage(any())).thenReturn(Result.ok(
                UserProfileArticlesResp.builder().total(0).page(1).pageSize(10).build()));

        UserProfileResp response = service.getUserProfile("7", null, "all", 1, 10);

        assertThat(response.getProfile().getUserId()).isEqualTo(7L);
        ArgumentCaptor<UserProfileArticlesQueryReq> request = ArgumentCaptor.forClass(UserProfileArticlesQueryReq.class);
        verify(contentInternalClient).profilePage(request.capture());
        assertThat(request.getValue().getAuthorId()).isEqualTo(7L);
        assertThat(request.getValue().getLimit()).isEqualTo(20);
        assertThat(request.getValue().getPage()).isEqualTo(1);
        assertThat(request.getValue().getPageSize()).isEqualTo(10);
        verify(userMapper).selectById(7L);
        verify(userMapper, never()).selectOne(any());

        assertThatThrownBy(() -> service.getUserProfile("999999999999999999999999999999", null, "all", 1, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    void updateProfileWritesOnlyFourProfileColumnsAndPreservesSecurityFieldsAndBlanks() {
        UserServiceImpl service = new UserServiceImpl(userMapper, contentInternalClient);
        User user = user(7L, "alice");
        user.setNickname("old nickname");
        user.setAvatarUrl("old-avatar");
        user.setCoverUrl("old-cover");
        user.setSignature("old signature");
        user.setEmail("alice@example.invalid");
        user.setPassword("password-hash");
        user.setSessionVersion(41L);
        user.setRole(UserRole.ADMIN);
        when(userMapper.selectById(7L)).thenReturn(user);
        String avatar = "a".repeat(255);
        when(userMapper.updateProfileFields(
                eq(7L), eq("old nickname"), eq(avatar), eq("new-cover"), eq("old signature")))
                .thenReturn(1);

        UpdateProfileReq request = new UpdateProfileReq();
        request.setNickname("   ");
        request.setAvatarUrl(" " + avatar + " ");
        request.setCoverUrl(" new-cover ");
        request.setSignature("\t");

        UserInfoResp response = service.updateProfile(7L, request);

        verify(userMapper).updateProfileFields(7L, "old nickname", avatar, "new-cover", "old signature");
        verify(userMapper, never()).updateById(any(User.class));
        assertThat(response.getNickname()).isEqualTo("old nickname");
        assertThat(response.getAvatarUrl()).isEqualTo(avatar);
        assertThat(response.getCoverUrl()).isEqualTo("new-cover");
        assertThat(response.getSignature()).isEqualTo("old signature");
        assertThat(response.getEmail()).isEqualTo("alice@example.invalid");
        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(user.getPassword()).isEqualTo("password-hash");
        assertThat(user.getSessionVersion()).isEqualTo(41L);
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

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname("Alice");
        user.setAvatarUrl("/avatar.png");
        user.setCoverUrl("/cover.png");
        user.setSignature("hello");
        return user;
    }
}
