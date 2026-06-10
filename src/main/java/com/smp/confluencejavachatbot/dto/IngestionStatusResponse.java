package com.smp.confluencejavachatbot.dto;

import java.time.OffsetDateTime;

public record IngestionStatusResponse(
        Long jobId,
        String rootPageId,
        String connectorMode,
        String status,
        int pagesProcessed,
        int chunksCreated,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

