package com.smp.confluencejavachatbot.dto;

public record SearchRequest(
        String query,
        Integer topK,
        String rootPageId
) {
}

