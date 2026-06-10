package com.smp.confluencejavachatbot.confluence;

import java.time.OffsetDateTime;
import java.util.List;

public record ConfluencePageData(
        String pageId,
        String rootPageId,
        String parentPageId,
        String title,
        String sourceUrl,
        String spaceKey,
        String htmlBody,
        OffsetDateTime sourceUpdatedAt,
        List<String> childPageIds
) {
}

