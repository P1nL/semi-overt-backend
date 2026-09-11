package com.platform.search.service;

import com.platform.search.mapper.SearchArticleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Checks the actual current MySQL schema before a query uses MATCH/AGAINST.
 * The check is per request so an acceptance CREATE/DROP is observed without
 * restarting the service. A missing or unusable optional index selects the
 * cleaned SQL LIKE path instead.
 */
@Slf4j
@Component
public class SearchIndexCapability {

    public static final String DEFAULT_INDEX_NAME = "ft_articles_search";

    private final SearchArticleMapper searchArticleMapper;
    private final boolean fullTextRequested;
    private final String indexName;

    public SearchIndexCapability(
            SearchArticleMapper searchArticleMapper,
            @Value("${platform.search.fulltext-enabled:false}") boolean fullTextRequested,
            @Value("${platform.search.fulltext-index-name:ft_articles_search}") String indexName) {
        this.searchArticleMapper = searchArticleMapper;
        this.fullTextRequested = fullTextRequested;
        this.indexName = indexName == null || indexName.isBlank() ? DEFAULT_INDEX_NAME : indexName;
    }

    public SearchIndexStatus inspect() {
        if (!fullTextRequested) {
            return new SearchIndexStatus(false, false, false, "LIKE_FALLBACK", "disabled_by_configuration");
        }

        try {
            boolean present = searchArticleMapper.hasFullTextIndex(indexName);
            return present
                    ? new SearchIndexStatus(true, true, true, "FULLTEXT_RELEVANCE", "configured_index_available")
                    : new SearchIndexStatus(true, false, false, "LIKE_FALLBACK", "configured_index_missing");
        } catch (DataAccessException ex) {
            log.warn("FULLTEXT capability check failed; using cleaned LIKE fallback: {}",
                    ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage());
            return new SearchIndexStatus(true, false, false, "LIKE_FALLBACK", "capability_check_failed");
        } catch (RuntimeException ex) {
            log.warn("FULLTEXT capability check failed; using cleaned LIKE fallback: {}", ex.getMessage());
            return new SearchIndexStatus(true, false, false, "LIKE_FALLBACK", "capability_check_failed");
        }
    }
}