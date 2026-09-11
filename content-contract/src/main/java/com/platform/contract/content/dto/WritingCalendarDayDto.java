package com.platform.contract.content.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Content-owned writing activity bucket derived from articles.updated_at. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingCalendarDayDto {
    private LocalDate date;
    private long wordCount;
}
