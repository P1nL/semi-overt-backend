package com.platform.service.impl;

import com.platform.dto.resp.SearchResp;
import com.platform.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Historical single-module placeholder search implementation.
 *
 * <p>This class is not part of the active parent-module build. The current
 * runtime search implementation is {@code search-service}, which uses
 * Elasticsearch article projections.</p>
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    @Override
    public SearchResp search(String keyword, int page, int pageSize) {
        log.debug("Legacy single-module search implementation invoked, keyword={}", keyword);

        return SearchResp.builder()
                .keyword(keyword)
                .list(Collections.emptyList())
                .total(0L)
                .page(page)
                .pageSize(pageSize)
                .pages(0L)
                .build();
    }
}
