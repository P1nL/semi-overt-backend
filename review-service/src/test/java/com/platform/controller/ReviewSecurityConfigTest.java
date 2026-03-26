package com.platform.controller;

import com.platform.config.SecurityConfig;
import com.platform.dto.resp.ReviewLogResp;
import com.platform.enums.ArticleStatus;
import com.platform.enums.ReviewAction;
import com.platform.exception.BusinessException;
import com.platform.service.ReviewService;
import com.platform.util.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReviewController.class)
@ContextConfiguration(classes = {
        ReviewController.class,
        SecurityConfig.class,
        com.platform.exception.GlobalExceptionHandler.class
})
@Import({SecurityConfig.class, com.platform.exception.GlobalExceptionHandler.class})
class ReviewSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @Test
    void pendingListWithoutAuthenticationReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/pending"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void pendingListForNormalUserReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/reviews/pending")
                        .header("X-User-Id", "12")
                        .header("X-Username", "user12")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void logsEndpointReturns403WhenServiceRejectsViewer() throws Exception {
        when(reviewService.getReviewLogs(99L, 12L))
                .thenThrow(BusinessException.forbidden("Access denied"));

        mockMvc.perform(get("/api/v1/reviews/99/logs")
                        .header("X-User-Id", "12")
                        .header("X-Username", "user12")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void logsEndpointAllowsAuthenticatedAuthor() throws Exception {
        when(reviewService.getReviewLogs(99L, 12L)).thenReturn(List.of(
                ReviewLogResp.builder()
                        .action(ReviewAction.RETURN)
                        .fromStatus(ArticleStatus.PENDING)
                        .toStatus(ArticleStatus.RETURNED)
                        .reason("needs revision")
                        .createdAt(LocalDateTime.parse("2026-03-26T11:20:00"))
                        .build()
        ));

        mockMvc.perform(get("/api/v1/reviews/99/logs")
                        .header("X-User-Id", "12")
                        .header("X-Username", "author12")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].action").value("RETURN"));
    }
}
