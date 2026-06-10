package com.smp.confluencejavachatbot.dto;

import java.util.List;

public record ChatResponse(
        String query,
        String response,
        List<SearchResultItem> sources
) {
}

