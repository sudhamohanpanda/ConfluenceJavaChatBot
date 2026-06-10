package com.smp.confluencejavachatbot.config;

import com.smp.confluencejavachatbot.dto.ConnectorMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        ConnectorMode connectorDefault,
        Chunking chunking,
        Ingestion ingestion,
        Confluence confluence,
        Mcp mcp
) {

    public record Chunking(int linesPerChunk, int overlapLines) {
    }

    public record Ingestion(int maxPages, int chunkInsertBatchSize) {
    }

    public record Confluence(String baseUrl, String username, String apiToken, String spaceKey) {
    }

    public record Mcp(String baseUrl, String authToken) {
    }
}

