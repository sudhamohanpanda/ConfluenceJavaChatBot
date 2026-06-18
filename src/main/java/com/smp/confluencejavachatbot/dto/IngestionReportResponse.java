package com.smp.confluencejavachatbot.dto;

import java.util.List;

public record IngestionReportResponse(
        int totalPages,
        List<PageLineReport> pages,
        String splitTechnique
) {
}

