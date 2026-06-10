package com.smp.confluencejavachatbot.confluence;

import com.smp.confluencejavachatbot.TestKeyLoader;
import com.smp.confluencejavachatbot.config.AppProperties;
import com.smp.confluencejavachatbot.dto.ConnectorMode;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RestConfluenceClientRealDataIT {

    // Manual integration helper that reads credentials from application-local.yaml.
    public void shouldFetchProvidedConfluencePageUsingRealCredentials() {
        String baseUrl = TestKeyLoader.readProperty("app.confluence.base-url");
        String username = TestKeyLoader.readProperty("app.confluence.username");
        String apiToken = TestKeyLoader.readProperty("app.confluence.api-token");
        String spaceKey = TestKeyLoader.readProperty("app.confluence.space-key");
        String pageId = "1373642445";

        Assumptions.assumeTrue(baseUrl != null && !baseUrl.isBlank(), "base-url missing in YAML");
        Assumptions.assumeTrue(username != null && !username.isBlank(), "username missing in YAML");
        Assumptions.assumeTrue(apiToken != null && !apiToken.isBlank(), "api-token missing in YAML");

        AppProperties properties = new AppProperties(
                ConnectorMode.REST,
                new AppProperties.Chunking(60, 10),
                new AppProperties.Ingestion(1000, 50),
                new AppProperties.Confluence(
                        baseUrl,
                        username,
                        apiToken,
                        spaceKey == null || spaceKey.isBlank() ? "FLA" : spaceKey
                ),
                new AppProperties.Mcp("", "")
        );

        RestConfluenceClient client = new RestConfluenceClient(properties);
        ConfluencePageData pageData = client.fetchPage(pageId, pageId, null);

        assertNotNull(pageData);
        assertFalse(pageData.pageId() == null || pageData.pageId().isBlank());
        assertFalse(pageData.title() == null || pageData.title().isBlank());
        assertNotNull(pageData.htmlBody());
    }
}

