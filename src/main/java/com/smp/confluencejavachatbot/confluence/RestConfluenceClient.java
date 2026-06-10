package com.smp.confluencejavachatbot.confluence;

import com.smp.confluencejavachatbot.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RestConfluenceClient implements ConfluenceClient {

    private static final Logger logger = LoggerFactory.getLogger(RestConfluenceClient.class);
    private static final int CHILD_PAGE_LIMIT = 200;
    private static final java.util.regex.Pattern LINKED_PAGE_PATTERN =
            java.util.regex.Pattern.compile("/spaces/[A-Za-z0-9]+/pages/(\\d+)");

    private final AppProperties properties;
    private final RestClient restClient;

    public RestConfluenceClient(AppProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().build();
    }

    @Override
    public ConfluencePageData fetchPage(String rootPageId, String pageId, String parentPageId) {
        String baseUrl = require(properties.confluence().baseUrl(), "CONFLUENCE_BASE_URL");

        JsonNode pageNode = null;
        Exception lastError = null;
        for (String apiRoot : apiRoots(baseUrl)) {
            String pageUri = apiRoot + "/content/" + pageId;
            try {
                logger.info("Fetching page from: {}", pageUri);
                pageNode = getWithAuth(
                        UriComponentsBuilder.fromUriString(pageUri)
                                .queryParam("expand", "body.storage,version,space,_links")
                                .build(true)
                                .toUriString()
                );
                if (pageNode != null && StringUtils.hasText(text(pageNode, "id"))) {
                    break;
                }
            } catch (Exception ex) {
                lastError = ex;
                logger.warn("Failed to fetch page {} from {}: {}", pageId, pageUri, ex.getMessage());
            }
        }

        if (pageNode == null || !StringUtils.hasText(text(pageNode, "id"))) {
            if (lastError != null) {
                throw new IllegalStateException(
                        "Confluence REST fetch failed for pageId=" + pageId + ": " + lastError.getMessage(),
                        lastError
                );
            }
            throw new IllegalStateException("Confluence REST returned empty page for pageId=" + pageId);
        }

        String title = text(pageNode, "title");
        String html = text(pageNode.path("body").path("storage"), "value");
        String webUi = text(pageNode.path("_links"), "webui");
        String sourceUrl = webUi == null ? null : baseUrl + webUi;
        String spaceKey = text(pageNode.path("space"), "key");
        OffsetDateTime updatedAt = parseTime(text(pageNode.path("version"), "when"));

        logger.info("Fetching child pages for page: {} ({})", pageId, title);
        List<String> childPageIds = fetchChildPageIds(baseUrl, pageId);
        logger.info("Page {} has {} direct child pages", pageId, childPageIds.size());

        // If no direct child pages, also extract linked pages from content
        if (childPageIds.isEmpty() && StringUtils.hasText(html)) {
            logger.debug("No direct child pages found for page {}. Extracting linked pages from content.", pageId);
            List<String> linkedPageIds = extractLinkedPageIds(html);
            logger.info("Page {} contains {} linked pages in content", pageId, linkedPageIds.size());
            childPageIds.addAll(linkedPageIds);
        }

        return new ConfluencePageData(
                pageId,
                rootPageId,
                parentPageId,
                title == null ? "Untitled" : title,
                sourceUrl,
                spaceKey,
                html == null ? "" : html,
                updatedAt,
                childPageIds
        );
    }

    private List<String> fetchChildPageIds(String baseUrl, String pageId) {
        Set<String> childPageIds = new LinkedHashSet<>();

        Exception lastError = null;
        for (String apiRoot : apiRoots(baseUrl)) {
            try {
                int start = 0;
                while (true) {
                    String childUri = UriComponentsBuilder
                            .fromUriString(apiRoot + "/content/" + pageId + "/child/page")
                            .queryParam("limit", Integer.toString(CHILD_PAGE_LIMIT))
                            .queryParam("start", Integer.toString(start))
                            .build(true)
                            .toUriString();

                    logger.debug("Fetching child pages from: {}", childUri);
                    JsonNode childNode = getWithAuth(childUri);
                    if (childNode == null || !childNode.path("results").isArray()) {
                        logger.debug("No child page results array found for pageId={} at start={}", pageId, start);
                        break;
                    }

                    int resultCount = childNode.path("results").size();
                    logger.debug("Fetched {} child pages for page {} (start={})", resultCount, pageId, start);
                    for (JsonNode result : childNode.path("results")) {
                        String childId = text(result, "id");
                        if (StringUtils.hasText(childId)) {
                            childPageIds.add(childId);
                        }
                    }

                    if (resultCount < CHILD_PAGE_LIMIT) {
                        break;
                    }
                    start += resultCount;
                }
                return new ArrayList<>(childPageIds);
            } catch (Exception ex) {
                lastError = ex;
                logger.warn("Failed to fetch child pages from apiRoot={}: {}", apiRoot, ex.getMessage());
            }
        }

        if (lastError != null) {
            logger.warn("Could not fetch child pages for pageId {} from any API root. Last error: {}", pageId, lastError.getMessage());
        }
        return new ArrayList<>(childPageIds);
    }

    /**
     * Extracts Confluence page IDs from HTML content by parsing links to confluence pages.
     * Looks for patterns like /spaces/SPACEKEY/pages/PAGEID
     */
    private List<String> extractLinkedPageIds(String html) {
        List<String> pageIds = new ArrayList<>();
        if (!StringUtils.hasText(html)) {
            return pageIds;
        }

        java.util.regex.Matcher matcher = LINKED_PAGE_PATTERN.matcher(html);

        Set<String> uniquePageIds = new LinkedHashSet<>();
        while (matcher.find()) {
            String pageId = matcher.group(1);
            if (StringUtils.hasText(pageId)) {
                uniquePageIds.add(pageId);
                logger.debug("Extracted linked page ID: {}", pageId);
            }
        }

        pageIds.addAll(uniquePageIds);
        return pageIds;
    }


    private List<String> apiRoots(String baseUrl) {
        String trimmedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        Set<String> roots = new LinkedHashSet<>();
        if (trimmedBase.endsWith("/wiki")) {
            roots.add(trimmedBase + "/rest/api");
            roots.add(trimmedBase.substring(0, trimmedBase.length() - 5) + "/rest/api");
        } else {
            roots.add(trimmedBase + "/rest/api");
            roots.add(trimmedBase + "/wiki/rest/api");
        }
        return new ArrayList<>(roots);
    }

    private JsonNode getWithAuth(String uri) {
        if (hasBasicCredentials()) {
            try {
                return restClient.get()
                        .uri(uri)
                        .headers(this::applyBasicHeaders)
                        .retrieve()
                        .body(JsonNode.class);
            } catch (HttpClientErrorException.Unauthorized ex) {
                if (!hasApiToken()) {
                    throw ex;
                }
            }
        }

        if (hasApiToken()) {
            return restClient.get()
                    .uri(uri)
                    .headers(this::applyBearerHeaders)
                    .retrieve()
                    .body(JsonNode.class);
        }

        logger.warn("No Confluence credentials configured; attempting anonymous request.");
        return restClient.get()
                .uri(uri)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(JsonNode.class);
    }

    private void applyBasicHeaders(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        String username = properties.confluence().username();
        String apiToken = properties.confluence().apiToken();
        String basic = Base64.getEncoder().encodeToString((username + ":" + apiToken)
                .getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + basic);
    }

    private void applyBearerHeaders(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiToken = properties.confluence().apiToken();
        headers.setBearerAuth(apiToken);
    }

    private boolean hasBasicCredentials() {
        return StringUtils.hasText(properties.confluence().username()) && hasApiToken();
    }

    private boolean hasApiToken() {
        return StringUtils.hasText(properties.confluence().apiToken());
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode valueNode = node.path(fieldName);
        return valueNode.isMissingNode() || valueNode.isNull() ? null : valueNode.asText();
    }

    private static OffsetDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    private static String require(String value, String key) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing required configuration: " + key);
        }
        return value;
    }
}

