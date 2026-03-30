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

/**
 * 搜索索引全量同步服务实现。
 * 启动时把当前所有已发布文章同步到 Elasticsearch，并清理已经不再公开的陈旧文档。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexSyncServiceImpl implements SearchIndexSyncService, ApplicationRunner {

    private final SearchIndexMapper searchIndexMapper;
    private final ArticleSearchRepository articleSearchRepository;

    /**
     * 应用启动后执行一次索引全量同步。
     */
    @Override
    public void run(ApplicationArguments args) {
        syncApprovedArticles();
    }

    /**
     * 全量同步已发布文章到搜索索引。
     */
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
