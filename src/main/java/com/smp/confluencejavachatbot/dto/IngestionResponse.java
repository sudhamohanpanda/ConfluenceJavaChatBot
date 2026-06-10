package com.smp.confluencejavachatbot.dto;

public record IngestionResponse(
        Long jobId,
        String status,
        int pagesProcessed,
        int chunksCreated,
        String message
) {
}

