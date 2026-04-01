package com.platform.content.api.resp;

import com.platform.kernel.enums.ArticleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Builder
public class ReviewActionResp {

        private ArticleStatus status;

        private LocalDateTime reviewedAt;
}


