package com.smp.confluencejavachatbot.dto;

import java.util.List;

public record SearchResponse(
        String query,
        int topK,
        List<SearchResultItem> results,
        String summary
) {
}

