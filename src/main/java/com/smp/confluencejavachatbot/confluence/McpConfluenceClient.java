package com.smp.confluencejavachatbot.confluence;

import com.smp.confluencejavachatbot.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class McpConfluenceClient implements ConfluenceClient {

    private final AppProperties properties;
    private final RestClient restClient;

    public McpConfluenceClient(AppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public ConfluencePageData fetchPage(String rootPageId, String pageId, String parentPageId) {
        String baseUrl = properties.mcp().baseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("ATLASSIAN_MCP_BASE_URL is required when connector mode is MCP");
        }

        JsonNode node = restClient.get()
                .uri(baseUrl + "/confluence/pages/" + pageId)
                .headers(this::applyHeaders)
                .retrieve()
                .body(JsonNode.class);

        if (node == null) {
            throw new IllegalStateException("Atlassian MCP returned empty content for pageId=" + pageId);
        }

        List<String> childPageIds = new ArrayList<>();
        if (node.path("childPageIds").isArray()) {
            for (JsonNode childNode : node.path("childPageIds")) {
                String id = childNode.asText();
                if (StringUtils.hasText(id)) {
                    childPageIds.add(id);
                }
            }
        }

        String sourceUrl = nullableText(node, "sourceUrl");
        String htmlBody = nullableText(node, "htmlBody");

        return new ConfluencePageData(
                pageId,
                rootPageId,
                parentPageId,
                fallback(nullableText(node, "title"), "Untitled"),
                sourceUrl,
                nullableText(node, "spaceKey"),
                fallback(htmlBody, ""),
                parseTime(nullableText(node, "sourceUpdatedAt")),
                childPageIds
        );
    }

    private void applyHeaders(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        String authToken = properties.mcp().authToken();
        if (StringUtils.hasText(authToken)) {
            headers.setBearerAuth(authToken);
        }
    }

    private static OffsetDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode valueNode = node.path(field);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }
        return valueNode.asText();
    }

    private static String fallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}

