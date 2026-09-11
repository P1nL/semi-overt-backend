package com.platform.auth.api.resp;

import com.platform.contract.content.dto.WritingCalendarDayDto;
import com.platform.kernel.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UserProfileResp {

    private ProfileInfo profile;
    private ArticleStats stats;
    private List<WritingCalendarDayDto> writingCalendar;
    private List<ArticleCardResp> list;
    private long total;
    private int page;
    private int pageSize;
    private long pages;

    @Data
    @Builder
    public static class ProfileInfo {
        private Long id;
        private Long userId;
        private String username;
        private String nickname;
        private UserRole role;
        private String avatarUrl;
        private String coverUrl;
        private String signature;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    public static class ArticleStats {
        private long approved;
        private long pending;
        private long returned;
        private long rejected;
        private long draft;
        private Integer totalWordCount;
    }
}
