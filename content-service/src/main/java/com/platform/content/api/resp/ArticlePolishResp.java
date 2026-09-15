package com.platform.content.api.resp;

import com.platform.content.api.req.ArticlePolishReq.Segment;
import java.util.List;

public record ArticlePolishResp(List<Segment> segments) {}
