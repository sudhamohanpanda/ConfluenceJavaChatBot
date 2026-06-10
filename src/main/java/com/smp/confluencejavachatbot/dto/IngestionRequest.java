package com.smp.confluencejavachatbot.dto;

public record IngestionRequest(
        String pageId,
        ConnectorMode connectorMode
) {
}

