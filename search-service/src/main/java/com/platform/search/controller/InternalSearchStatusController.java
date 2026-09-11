package com.platform.search.controller;

import com.platform.kernel.util.Result;
import com.platform.search.service.SearchIndexCapability;
import com.platform.search.service.SearchIndexStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Protected by InternalTokenFilter. Observes capability; never runs DDL or a rebuild. */
@RestController
@RequestMapping("/internal/search")
@RequiredArgsConstructor
public class InternalSearchStatusController {
    private final SearchIndexCapability capability;
    @GetMapping("capability")
    public Result<SearchIndexStatus> capability() { return Result.ok(capability.inspect()); }
}
