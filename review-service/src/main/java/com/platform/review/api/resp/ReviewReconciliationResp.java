package com.platform.review.api.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReviewReconciliationResp {
    private int scanned;
    private int repaired;
    private int finalized;
    private int conflicts;
    private int unchanged;
    private int unresolved;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private Long articleId;
        private String decisionId;
        private String submissionId;
        private Long articleVersion;
        private String outcome;
        private String message;
    }
}
