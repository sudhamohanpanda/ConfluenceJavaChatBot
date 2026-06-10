package com.smp.confluencejavachatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record IngestionReportResponse(
        @JsonProperty("total_number_ofpage") int totalNumberOfPage,
        List<PageLineReport> pages,
        String splitTechnique
) {
}

