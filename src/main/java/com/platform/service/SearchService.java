package com.platform.service;

import com.platform.dto.resp.SearchResp;

/**
 * Historical single-module search service contract.
 *
 * <p>This interface is retained only as legacy residue in the root
 * {@code src/} tree and is not used by the active multi-module runtime.</p>
 */
@Deprecated
public interface SearchService {

    SearchResp search(String keyword, int page, int pageSize);
}
