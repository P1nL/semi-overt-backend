package com.platform.repository;

import com.platform.document.ArticleSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleSearchDocument, Long> {
}
