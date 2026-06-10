package com.smp.confluencejavachatbot.dto;

public record SearchResultItem(
        String pageId,
        String rootPageId,
        String parentPageId,
        String title,
        String sourceUrl,
        int chunkIndex,
        int lineStart,
        int lineEnd,
        String content,
        double similarityScore
) {
}

