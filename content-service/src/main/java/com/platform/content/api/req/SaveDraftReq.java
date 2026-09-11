package com.platform.content.api.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Database-backed draft patch. Null leaves a field unchanged; an empty string clears it. */
@Data
public class SaveDraftReq {

    @Size(max = 120)
    private String title;

    private String content;

    @Size(max = 255)
    private String summary;

    @Size(max = 512)
    private String coverUrl;

    @Size(max = 32)
    private String coverColor;

    /** Compatibility hint only; server-derived metrics remain authoritative. */
    private Integer clientWordCount;

    /** Optional optimistic baseline; null uses the version read by this request. */
    private Long version;
}
