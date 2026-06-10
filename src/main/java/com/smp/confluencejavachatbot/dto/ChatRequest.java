package com.smp.confluencejavachatbot.dto;

public record ChatRequest(
        String query,
        Integer topK,
        String rootPageId
) {
}

