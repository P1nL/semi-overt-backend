package com.platform.repository;

import com.platform.document.ArticleSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 文章搜索仓储接口，负责检索层数据的存取操作。
 */

public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleSearchDocument, Long> {
}
