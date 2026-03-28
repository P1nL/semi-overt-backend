package com.platform.service.impl;

import com.platform.document.ArticleSearchDocument;
import com.platform.mapper.SearchIndexMapper;
import com.platform.repository.ArticleSearchRepository;
import com.platform.service.SearchIndexSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexSyncServiceImpl implements SearchIndexSyncService, ApplicationRunner {

    private final SearchIndexMapper searchIndexMapper;
    private final ArticleSearchRepository articleSearchRepository;

    @Override
    public void run(ApplicationArguments args) {
        syncApprovedArticles();
    }

    @Override
    public void syncApprovedArticles() {
        List<ArticleSearchDocument> approvedArticles = searchIndexMapper.selectApprovedArticlesForIndex();
        Set<Long> approvedIds = approvedArticles.stream()
                .map(ArticleSearchDocument::getArticleId)
                .collect(Collectors.toSet());

        articleSearchRepository.saveAll(approvedArticles);

        List<Long> staleIds = StreamSupport.stream(articleSearchRepository.findAll().spliterator(), false)
                .map(ArticleSearchDocument::getArticleId)
                .filter(articleId -> !approvedIds.contains(articleId))
                .toList();
        if (!staleIds.isEmpty()) {
            articleSearchRepository.deleteAllById(staleIds);
        }

        log.info("Search index sync finished: approvedArticles={}, staleDocumentsRemoved={}",
                approvedArticles.size(), staleIds.size());
    }
}
