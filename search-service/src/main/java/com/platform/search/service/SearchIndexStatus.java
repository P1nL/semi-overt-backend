package com.platform.search.service;

/** Observable database search capability, not a claim that a secondary index was rebuilt. */
public record SearchIndexStatus(
        boolean requested,
        boolean indexPresent,
        boolean usable,
        String mode,
        String reason
) {
}