package com.platform.content.api.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Text leaves only: the editor retains ownership of document structure and rich-text attributes. */
public record ArticlePolishReq(
        @NotEmpty @Size(max = 200) List<@Valid Segment> segments) {
    public record Segment(
            @NotBlank @Size(max = 128) @Pattern(regexp = "[0-9]+(?:\\.[0-9]+)*") String id,
            @NotBlank @Size(max = 12000) String text) {}
}
